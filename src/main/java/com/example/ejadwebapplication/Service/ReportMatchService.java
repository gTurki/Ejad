package com.example.ejadwebapplication.Service;

import com.example.ejadwebapplication.Api.ApiException;
import com.example.ejadwebapplication.DTO.MatchResultDTO;
import com.example.ejadwebapplication.DTOIN.ReportMatchDTOIn;
import com.example.ejadwebapplication.DTOOUT.ReportMatchDTOOut;
import com.example.ejadwebapplication.Enums.MatchStatus;
import com.example.ejadwebapplication.Enums.NotificationType;
import com.example.ejadwebapplication.Enums.ReportStatus;
import com.example.ejadwebapplication.Enums.ReportType;
import com.example.ejadwebapplication.Model.Location;
import com.example.ejadwebapplication.Model.Report;
import com.example.ejadwebapplication.Model.ReportMatch;
import com.example.ejadwebapplication.Model.Staff;
import com.example.ejadwebapplication.Model.User;
import com.example.ejadwebapplication.Repository.ReportMatchRepository;
import com.example.ejadwebapplication.Repository.ReportRepository;
import com.example.ejadwebapplication.Repository.StaffRepository;
import com.example.ejadwebapplication.Repository.UserRepository;
import com.example.ejadwebapplication.Client.WhatsAppSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportMatchService {

    // أقل نسبة تشابه عشان نحفظ التطابق
    private static final double MATCH_THRESHOLD = 70.0;
    private final WhatsAppSender whatsAppSender;

    private final ReportMatchRepository reportMatchRepository;
    private final ReportRepository reportRepository;
    private final NotificationService notificationService;
    private final AiService aiService;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final GoogleMapsService googleMapsService;

    public List<ReportMatchDTOOut> getAllMatches() {
        return convertListToDTO(reportMatchRepository.findAll());
    }

    public ReportMatchDTOOut getMatchById(Integer id) {
        return convertToDTO(findMatch(id));
    }

    public List<ReportMatchDTOOut> getMatchesByStatus(String status) {
        MatchStatus matchStatus;
        try {
            matchStatus = MatchStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Status must be SUGGESTED, CONFIRMED or REJECTED");
        }
        return convertListToDTO(reportMatchRepository.findAllByStatus(matchStatus));
    }

    public List<ReportMatchDTOOut> getMatchesByReport(Integer reportId) {
        Report report = findReport(reportId);
        return convertListToDTO(reportMatchRepository.findAllByLostReportOrFoundReport(report, report));
    }

    // إضافة يدوية (مثلاً الأدمن أو للتجربة بدون AI)
    @Transactional
    public void addMatch(ReportMatchDTOIn dto) {
        Report lostReport = findReport(dto.getLostReportId());
        Report foundReport = findReport(dto.getFoundReportId());

        if (lostReport.getType() != ReportType.LOST) {
            throw new ApiException("lostReportId must belong to a LOST report");
        }
        if (foundReport.getType() != ReportType.FOUND) {
            throw new ApiException("foundReportId must belong to a FOUND report");
        }
        if (lostReport.getStatus() != ReportStatus.OPEN || foundReport.getStatus() != ReportStatus.OPEN) {
            throw new ApiException("Both reports must be open");
        }
        if (reportMatchRepository.existsByLostReportAndFoundReport(lostReport, foundReport)) {
            throw new ApiException("This match already exists");
        }

        saveMatch(lostReport, foundReport, dto.getSimilarityScore(), dto.getAiReason());
    }

    // يُستدعى من ReportService بعد حفظ أي بلاغ جديد
// فشل الـ AI ما يفشّل حفظ البلاغ، نسجّل الخطأ ونكمل
    public void findMatchesForReport(Report report) {
        ReportType oppositeType = report.getType() == ReportType.LOST ? ReportType.FOUND : ReportType.LOST;

        // اللي بينه وبين البلاغ تطابق من قبل (مقترح أو مرفوض) ما نرسله للـ AI مرة ثانية
        List<Report> candidates = new ArrayList<>();
        for (Report candidate : reportRepository.findMatchCandidates(
                oppositeType, report.getCategory(), ReportStatus.OPEN, report.getLocations())) {
            if (!matchExists(report, candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        List<MatchResultDTO> results;
        try {
            results = aiService.compareReports(report, candidates);
        } catch (Exception e) {
            log.warn("AI matching failed for report {}: {}", report.getId(), e.getMessage());
            return;
        }

        // الأعلى نسبة أول، عشان أول تطابق لكل بلاغ LOST يكون هو اللي ينرسل بالواتساب
        results.sort((a, b) -> Double.compare(b.getScore() == null ? 0 : b.getScore(),
                a.getScore() == null ? 0 : a.getScore()));

        // ids بلاغات الـ LOST اللي انرسل لأصحابها واتساب في هذي المطابقة
        Set<Integer> notifiedLost = new HashSet<>();

        for (MatchResultDTO result : results) {
            if (result.getScore() == null || result.getScore() < MATCH_THRESHOLD) {
                continue;
            }
            // نتأكد إن الـ id اللي رجّعه الـ AI فعلاً من المرشحين
            Report other = findInList(candidates, result.getReportId());
            if (other == null) {
                continue;
            }
            // لو الـ AI كرر نفس الـ id مرتين
            if (matchExists(report, other)) {
                continue;
            }

            Report lostReport = report.getType() == ReportType.LOST ? report : other;
            Report foundReport = report.getType() == ReportType.FOUND ? report : other;
            saveMatch(lostReport, foundReport, Math.min(result.getScore(), 100.0), result.getReason());

            // واتساب وحدة بس لكل بلاغ LOST، ولصاحبه لو كان user (الموظف يكفيه الإيميل والإشعار)
            // add ترجع false لو البلاغ انرسل له قبل، فيوصل أعلى تطابق بس
            if (lostReport.getUser() != null && notifiedLost.add(lostReport.getId())) {
                User owner = lostReport.getUser();
                whatsAppSender.sendMatchFound(owner.getPhone(), owner.getFullName(),
                        lostReport.getTitle(), foundReport.getTitle());
            }
        }
    }

    // تأكيد التطابق: الـ match يصير CONFIRMED والبلاغين MATCHED، كلها مع بعض أو ولا شي
    @Transactional
    public void confirmMatch(Integer id) {
        ReportMatch match = findMatch(id);
        checkIsSuggested(match);

        Report lostReport = match.getLostReport();
        Report foundReport = match.getFoundReport();
        if (lostReport.getStatus() != ReportStatus.OPEN || foundReport.getStatus() != ReportStatus.OPEN) {
            throw new ApiException("Both reports must be open to confirm the match");
        }

        match.setStatus(MatchStatus.CONFIRMED);
        lostReport.setStatus(ReportStatus.MATCHED);
        foundReport.setStatus(ReportStatus.MATCHED);
        reportMatchRepository.save(match);
        reportRepository.save(lostReport);
        reportRepository.save(foundReport);

        // باقي الاقتراحات لنفس البلاغين ما عاد لها داعي
        rejectOtherSuggestions(lostReport, match);
        rejectOtherSuggestions(foundReport, match);

        // The item is at the found report's location (FOUND has exactly one), so send the lost owner a directions link
        Location itemLocation = foundReport.getLocations().iterator().next();
        String message = "Match confirmed for your report: " + lostReport.getTitle()
                + ". Item location: " + itemLocation.getName();
        String directionsUrl = googleMapsService.buildDirectionsUrl(itemLocation);
        if (directionsUrl != null) {
            message += " - Directions: " + directionsUrl;
        }
        notificationService.notifyReportOwner(lostReport, NotificationType.MATCH_CONFIRMED, message);
        notificationService.notifyReportOwner(foundReport, NotificationType.MATCH_CONFIRMED,
                "Match confirmed for your report: " + foundReport.getTitle());
    }

    public void rejectMatch(Integer id) {
        ReportMatch match = findMatch(id);
        checkIsSuggested(match);

        match.setStatus(MatchStatus.REJECTED);
        reportMatchRepository.save(match);
    }

    public void deleteMatch(Integer id) {
        ReportMatch match = findMatch(id);
        // لو انحذف وهو مؤكد، البلاغين يبقون MATCHED بدون سبب
        if (match.getStatus() == MatchStatus.CONFIRMED) {
            throw new ApiException("Cannot delete a confirmed match");
        }
        reportMatchRepository.delete(match);
    }

    // ================= Extra =================

    // الاقتراحات اللي لسا ما انحسمت لبلاغ معيّن، الأعلى نسبة أول
    public List<ReportMatchDTOOut> getSuggestedMatchesForReport(Integer reportId) {
        Report report = findReport(reportId);
        List<ReportMatch> suggested = new ArrayList<>();
        for (ReportMatch match : reportMatchRepository.findAllByLostReportOrFoundReport(report, report)) {
            if (match.getStatus() == MatchStatus.SUGGESTED) {
                suggested.add(match);
            }
        }
        suggested.sort((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()));
        return convertListToDTO(suggested);
    }

    public List<ReportMatchDTOOut> getMatchesByUser(Integer userId) {
        User user = userRepository.findUserById(userId);
        if (user == null) {
            throw new ApiException("User not found");
        }
        return convertListToDTO(reportMatchRepository.findAllByReportOwner(user));
    }

    // نعيد المطابقة يدوياً (مثلاً انضافت بلاغات جديدة بعد بلاغي)
    @Transactional
    public List<ReportMatchDTOOut> rematchReport(Integer reportId) {
        Report report = findReport(reportId);
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ApiException("Only open reports can be matched");
        }
        findMatchesForReport(report);
        return getMatchesByReport(reportId);
    }

    // ================= Extra 2 =================

    // التطابقات اللي الغرض فيها موجود عند مكان الموظف، عشان يجهّزه للتسليم
    public List<ReportMatchDTOOut> getMatchesForStaff(Integer staffId) {
        Staff staff = staffRepository.findStaffById(staffId);
        if (staff == null) {
            throw new ApiException("Staff not found");
        }
        if (staff.getLocation() == null) {
            throw new ApiException("Staff is not assigned to a location");
        }
        return convertListToDTO(reportMatchRepository.findAllByFoundReportLocation(staff.getLocation()));
    }

    public List<ReportMatchDTOOut> getHighConfidenceMatches(Double minScore) {
        if (minScore < 0 || minScore > 100) {
            throw new ApiException("Score must be between 0 and 100");
        }
        return convertListToDTO(reportMatchRepository
                .findAllBySimilarityScoreGreaterThanEqualOrderBySimilarityScoreDesc(minScore));
    }

    // ================= Helpers =================

    // هل فيه تطابق محفوظ بين البلاغين (أي حالة)
    private boolean matchExists(Report report, Report other) {
        Report lostReport = report.getType() == ReportType.LOST ? report : other;
        Report foundReport = report.getType() == ReportType.FOUND ? report : other;
        return reportMatchRepository.existsByLostReportAndFoundReport(lostReport, foundReport);
    }

    private void saveMatch(Report lostReport, Report foundReport, Double score, String reason) {
        ReportMatch match = new ReportMatch();
        match.setLostReport(lostReport);
        match.setFoundReport(foundReport);
        match.setSimilarityScore(score);
        if (reason != null && reason.length() > 500) {
            reason = reason.substring(0, 500);
        }
        match.setAiReason(reason);
        reportMatchRepository.save(match);

        notificationService.notifyReportOwner(lostReport, NotificationType.MATCH_FOUND,
                "Possible match found for your report: " + foundReport.getTitle());
        notificationService.notifyReportOwner(foundReport, NotificationType.MATCH_FOUND,
                "Possible match found for your report: " + lostReport.getTitle());
    }

    private void rejectOtherSuggestions(Report report, ReportMatch confirmed) {
        for (ReportMatch match : reportMatchRepository.findAllByLostReportOrFoundReport(report, report)) {
            if (!match.getId().equals(confirmed.getId()) && match.getStatus() == MatchStatus.SUGGESTED) {
                match.setStatus(MatchStatus.REJECTED);
                reportMatchRepository.save(match);
            }
        }
    }

    private Report findInList(List<Report> reports, Integer id) {
        for (Report report : reports) {
            if (report.getId().equals(id)) {
                return report;
            }
        }
        return null;
    }

    private Report findReport(Integer id) {
        Report report = reportRepository.findReportById(id);
        if (report == null) {
            throw new ApiException("Report not found with ID: " + id);
        }
        return report;
    }

    private ReportMatch findMatch(Integer id) {
        ReportMatch match = reportMatchRepository.findReportMatchById(id);
        if (match == null) {
            throw new ApiException("Match not found with ID: " + id);
        }
        return match;
    }

    private void checkIsSuggested(ReportMatch match) {
        if (match.getStatus() != MatchStatus.SUGGESTED) {
            throw new ApiException("Match is already " + match.getStatus());
        }
    }

    private List<ReportMatchDTOOut> convertListToDTO(List<ReportMatch> matches) {
        List<ReportMatchDTOOut> result = new ArrayList<>();
        for (ReportMatch match : matches) {
            result.add(convertToDTO(match));
        }
        return result;
    }

    private ReportMatchDTOOut convertToDTO(ReportMatch match) {
        return new ReportMatchDTOOut(match.getId(), match.getSimilarityScore(), match.getAiReason(),
                match.getStatus().name(), match.getCreatedAt(),
                match.getLostReport().getId(), match.getLostReport().getTitle(),
                match.getFoundReport().getId(), match.getFoundReport().getTitle());
    }
}
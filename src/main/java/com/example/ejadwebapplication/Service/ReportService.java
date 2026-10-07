package com.example.ejadwebapplication.Service;

import com.example.ejadwebapplication.Api.ApiException;
import com.example.ejadwebapplication.DTO.ImageAnalysisDTO;
import com.example.ejadwebapplication.DTOIN.ReportDTOIn;
import com.example.ejadwebapplication.DTOOUT.LocationDTOOut;
import com.example.ejadwebapplication.DTOOUT.NearbyReportDTOOut;
import com.example.ejadwebapplication.DTOOUT.ReportDTOOut;
import com.example.ejadwebapplication.Enums.MatchStatus;
import com.example.ejadwebapplication.Enums.ReportStatus;
import com.example.ejadwebapplication.Enums.ReportType;
import com.example.ejadwebapplication.Model.*;
import com.example.ejadwebapplication.Repository.*;
import com.example.ejadwebapplication.Client.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;


@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final NotificationRepository notificationRepository;
    private final ReportMatchRepository reportMatchRepository;
    private final NotificationService notificationService;
    private final ReportMatchService reportMatchService;
    private final EmailSender emailSender;
    private final AiService aiService;
    private final GoogleMapsService googleMapsService;

    public List<ReportDTOOut> getAllReports() {
        return convertListToDTO(reportRepository.findAll());
    }

    public ReportDTOOut getReportById(Integer id) {
        return convertToDTO(findReport(id));
    }

    // البلاغ + إشعارات الموظفين + التطابقات، كلها في نفس الـ transaction
    @Transactional
    public ReportDTOOut addReport(ReportDTOIn dto) {
        ReportType type = ReportType.valueOf(dto.getType());

        Report report = new Report();
        report.setType(type);
        setOwner(report, dto.getUserId(), dto.getStaffId(), type);
        fillDetails(report, dto, type);

        Report saved = reportRepository.save(report);

        if (saved.getUser() != null) {
            emailSender.sendReportCreated(saved.getUser().getEmail(), saved.getUser().getFullName(),
                    type.name(), saved.getTitle());
        }

        notificationService.notifyStaffAboutNewReport(saved);
        reportMatchService.findMatchesForReport(saved);

        return convertToDTO(saved);
    }

    // لو انضاف مكان جديد، موظفينه يوصلهم إشعار
    // التعديل يغيّر التفاصيل فقط، أما النوع وصاحب البلاغ يتجاهلهم
    @Transactional
    public ReportDTOOut updateReport(Integer id, ReportDTOIn dto) {
        Report report = findReport(id);

        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ApiException("Only open reports can be updated");
        }

        // نحفظ ids الأماكن القديمة قبل ما fillDetails يستبدلها
        Set<Integer> oldLocationIds = new HashSet<>();

        for (Location location : report.getLocations()) {
            oldLocationIds.add(location.getId());
        }

        fillDetails(report, dto, report.getType());

        Report saved = reportRepository.save(report);

        // الأماكن اللي ما كانت موجودة قبل التعديل
        Set<Location> addedLocations = new HashSet<>();

        for (Location location : saved.getLocations()) {
            if (!oldLocationIds.contains(location.getId())) {
                addedLocations.add(location);
            }
        }

        notificationService.notifyStaffAtLocations(saved, addedLocations);
        reportMatchService.findMatchesForReport(saved);

        return convertToDTO(saved);
    }

    // نحذف الإشعارات والتطابقات أول، لأنها تأشر على البلاغ
    // ما نسمح بالحذف لو له تطابق مؤكد
    @Transactional
    public void deleteReport(Integer id) {
        Report report = findReport(id);

        List<ReportMatch> matches =
                reportMatchRepository.findAllByLostReportOrFoundReport(report, report);

        for (ReportMatch match : matches) {
            if (match.getStatus() == MatchStatus.CONFIRMED) {
                throw new ApiException("Cannot delete a report that has a confirmed match");
            }
        }

        notificationRepository.deleteAllByReport(report);
        reportMatchRepository.deleteAll(matches);
        reportRepository.delete(report);
    }

    // إغلاق البلاغ، والاقتراحات المعلقة ترتفض
    @Transactional
    public void closeReport(Integer id) {
        Report report = findReport(id);

        if (report.getStatus() == ReportStatus.CLOSED) {
            throw new ApiException("Report is already closed");
        }

        report.setStatus(ReportStatus.CLOSED);
        reportRepository.save(report);

        for (ReportMatch match :
                reportMatchRepository.findAllByLostReportOrFoundReport(report, report)) {

            if (match.getStatus() == MatchStatus.SUGGESTED) {
                match.setStatus(MatchStatus.REJECTED);
                reportMatchRepository.save(match);
            }
        }
    }

    // ================= Filters =================

    public List<ReportDTOOut> getReportsByUser(Integer userId) {
        User user = userRepository.findUserById(userId);

        if (user == null) {
            throw new ApiException("User not found");
        }

        return convertListToDTO(reportRepository.findAllByUser(user));
    }

    public List<ReportDTOOut> getReportsByStaff(Integer staffId) {
        Staff staff = staffRepository.findStaffById(staffId);

        if (staff == null) {
            throw new ApiException("Staff not found");
        }

        return convertListToDTO(reportRepository.findAllByStaff(staff));
    }

    public List<ReportDTOOut> getReportsByStatus(String status) {
        ReportStatus reportStatus;

        try {
            reportStatus = ReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Status must be OPEN, MATCHED or CLOSED");
        }

        return convertListToDTO(reportRepository.findAllByStatus(reportStatus));
    }

    public List<ReportDTOOut> getReportsByType(String type) {
        ReportType reportType;

        try {
            reportType = ReportType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Type must be LOST or FOUND");
        }

        return convertListToDTO(reportRepository.findAllByType(reportType));
    }

    public List<ReportDTOOut> getReportsByLocation(Integer locationId) {
        Location location = locationRepository.findLocationById(locationId);

        if (location == null) {
            throw new ApiException("Location not found");
        }

        return convertListToDTO(
                reportRepository.findAllByLocationsContaining(location)
        );
    }

    // ================= Extra =================

    public List<ReportDTOOut> searchReports(String keyword) {
        return convertListToDTO(
                reportRepository
                        .findAllByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                keyword, keyword
                        )
        );
    }
    public List<ReportDTOOut> getReportsByCategory(Integer categoryId) {
        Category category = categoryRepository.findCategoryById(categoryId);
        if (category == null) {
            throw new ApiException("Category not found");
        }
        return convertListToDTO(reportRepository.findAllByCategory(category));
    }
    // حسب تاريخ ضياع/إيجاد الغرض
    public List<ReportDTOOut> getReportsByDateRange(
            LocalDate from,
            LocalDate to) {

        if (from.isAfter(to)) {
            throw new ApiException("Start date must be before end date");
        }

        return convertListToDTO(
                reportRepository.findAllByItemDateBetween(from, to)
        );
    }

    // نرجّع البلاغ المغلق مفتوح، بشرط ما يكون له تطابق مؤكد
    @Transactional
    public void reopenReport(Integer id) {
        Report report = findReport(id);

        if (report.getStatus() != ReportStatus.CLOSED) {
            throw new ApiException("Only closed reports can be reopened");
        }

        for (ReportMatch match :
                reportMatchRepository.findAllByLostReportOrFoundReport(report, report)) {

            if (match.getStatus() == MatchStatus.CONFIRMED) {
                throw new ApiException(
                        "Cannot reopen a report that has a confirmed match"
                );
            }
        }

        report.setStatus(ReportStatus.OPEN);
        reportRepository.save(report);
    }

    // البلاغات المفتوحة في مكان الموظف
    public List<ReportDTOOut> getOpenReportsAtStaffLocation(Integer staffId) {
        Staff staff = staffRepository.findStaffById(staffId);

        if (staff == null) {
            throw new ApiException("Staff not found");
        }

        if (staff.getLocation() == null) {
            throw new ApiException("Staff is not assigned to a location");
        }

        return convertListToDTO(
                reportRepository.findAllByLocationsContainingAndStatus(
                        staff.getLocation(),
                        ReportStatus.OPEN
                )
        );
    }

    // ================= Extra 2 =================

    // تحليل الصورة + رفع البلاغ بخطوة وحدة
    @Transactional
    public ReportDTOOut addReportFromImage(
            MultipartFile image,
            String type,
            Integer userId,
            Integer staffId,
            Set<Integer> locationIds,
            LocalDate itemDate) {

        type = type.toUpperCase();

        if (!type.equals("LOST") && !type.equals("FOUND")) {
            throw new ApiException("Type must be LOST or FOUND");
        }

        if ((userId == null) == (staffId == null)) {
            throw new ApiException(
                    "Report must have exactly one owner: userId or staffId"
            );
        }

        if (locationIds == null || locationIds.isEmpty()) {
            throw new ApiException("At least one location is required");
        }

        if (itemDate.isAfter(LocalDate.now())) {
            throw new ApiException("Item date cannot be in the future");
        }

        ImageAnalysisDTO analysis = aiService.analyzeImage(image);

        if (analysis.getCategoryId() == null) {
            throw new ApiException(
                    "AI could not detect a valid category, please use /report/add instead"
            );
        }

        if (isBlank(analysis.getTitle())
                || isBlank(analysis.getDescription())
                || isBlank(analysis.getColor())) {

            throw new ApiException(
                    "AI response is missing details, please use /report/add instead"
            );
        }

        ReportDTOIn dto = new ReportDTOIn();

        dto.setType(type);
        dto.setTitle(cut(analysis.getTitle(), 50));
        dto.setDescription(cut(analysis.getDescription(), 200));
        dto.setColor(cut(analysis.getColor(), 30));
        dto.setBrand(cut(analysis.getBrand(), 50));
        dto.setItemDate(itemDate);
        dto.setUserId(userId);
        dto.setStaffId(staffId);
        dto.setCategoryId(analysis.getCategoryId());
        dto.setLocationIds(locationIds);

        return addReport(dto);
    }

    // المرشحين بدون AI:
    // النوع المعاكس + نفس التصنيف + مفتوحة + مكان مشترك
    public List<ReportDTOOut> getSimilarReports(Integer id) {
        Report report = findReport(id);

        ReportType oppositeType =
                report.getType() == ReportType.LOST
                        ? ReportType.FOUND
                        : ReportType.LOST;

        return convertListToDTO(
                reportRepository.findMatchCandidates(
                        oppositeType,
                        report.getCategory(),
                        ReportStatus.OPEN,
                        report.getLocations()
                )
        );
    }

    public List<ReportDTOOut> getReportsByCity(String city) {
        return convertListToDTO(reportRepository.findAllByCity(city));
    }

    public List<ReportDTOOut> getRecentReports(Integer days) {
        checkDays(days);

        return convertListToDTO(
                reportRepository.findAllByCreatedAtAfter(
                        LocalDateTime.now().minusDays(days)
                )
        );
    }

    // بلاغات مفتوحة من زمان بدون نتيجة
    public List<ReportDTOOut> getStaleReports(Integer days) {
        checkDays(days);

        return convertListToDTO(
                reportRepository.findAllByStatusAndCreatedAtBefore(
                        ReportStatus.OPEN,
                        LocalDateTime.now().minusDays(days)
                )
        );
    }

    public List<ReportDTOOut> getOpenReportsByUser(Integer userId) {
        User user = userRepository.findUserById(userId);

        if (user == null) {
            throw new ApiException("User not found");
        }

        return convertListToDTO(
                reportRepository.findAllByUserAndStatus(
                        user,
                        ReportStatus.OPEN
                )
        );
    }

    // Containing عشان "ذهبي" تلقى "ذهبي فاتح"
    public List<ReportDTOOut> getReportsByColor(String color) {
        return convertListToDTO(
                reportRepository.findAllByColorContainingIgnoreCase(color)
        );
    }

    public List<ReportDTOOut> getReportsByBrand(String brand) {
        return convertListToDTO(
                reportRepository.findAllByBrandContainingIgnoreCase(brand)
        );
    }

    // ================= Nearby Reports =================

    public List<NearbyReportDTOOut> getNearbyFoundReports(
            Integer reportId,
            Double radiusKm) {

        googleMapsService.validateRadius(radiusKm);

        Report lostReport = findReport(reportId);

        if (lostReport.getType() != ReportType.LOST) {
            throw new ApiException(
                    "Nearby search is only for LOST reports"
            );
        }

        if (lostReport.getStatus() != ReportStatus.OPEN) {
            throw new ApiException(
                    "Only open reports can search for nearby items"
            );
        }

        List<NearbyReportDTOOut> result = new ArrayList<>();

        for (Report foundReport :
                reportRepository.findAllByTypeAndCategoryAndStatus(
                        ReportType.FOUND,
                        lostReport.getCategory(),
                        ReportStatus.OPEN)) {

            Double distance = closestDistance(
                    lostReport.getLocations(),
                    foundReport.getLocations()
            );

            if (distance != null && distance <= radiusKm) {
                result.add(
                        new NearbyReportDTOOut(
                                distance,
                                convertToDTO(foundReport)
                        )
                );
            }
        }

        result.sort(
                Comparator.comparing(NearbyReportDTOOut::getDistanceKm)
        );

        return result;
    }

    // أقصر مسافة بين أي lost location وأي found location
    private Double closestDistance(
            Set<Location> lostLocations,
            Set<Location> foundLocations) {

        Double closest = null;

        for (Location lost : lostLocations) {
            for (Location found : foundLocations) {

                if (lost.getLatitude() == null
                        || lost.getLongitude() == null
                        || found.getLatitude() == null
                        || found.getLongitude() == null) {
                    continue;
                }

                double distance = googleMapsService.distanceKm(
                        lost.getLatitude(),
                        lost.getLongitude(),
                        found.getLatitude(),
                        found.getLongitude()
                );

                if (closest == null || distance < closest) {
                    closest = distance;
                }
            }
        }

        return closest;
    }

    // ================= Helpers =================

    private void checkDays(Integer days) {
        if (days == null || days < 1) {
            throw new ApiException("Days must be at least 1");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String cut(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }

        return value.substring(0, max);
    }

    private Report findReport(Integer id) {
        Report report = reportRepository.findReportById(id);

        if (report == null) {
            throw new ApiException("Report not found");
        }

        return report;
    }

    // واحد بالضبط: user أو staff
    private void setOwner(
            Report report,
            Integer userId,
            Integer staffId,
            ReportType type) {

        if ((userId == null) == (staffId == null)) {
            throw new ApiException(
                    "Report must have exactly one owner: userId or staffId"
            );
        }

        if (userId != null) {

            User user = userRepository.findUserById(userId);

            if (user == null) {
                throw new ApiException("User not found");
            }

            report.setUser(user);
            return;
        }

        Staff staff = staffRepository.findStaffById(staffId);

        if (staff == null) {
            throw new ApiException("Staff not found");
        }

        if (!staff.getIsVerified()) {
            throw new ApiException(
                    "Staff must be verified by admin before creating reports"
            );
        }

        // الموظف يرفع FOUND فقط
        if (type != ReportType.FOUND) {
            throw new ApiException(
                    "Staff can only create FOUND reports"
            );
        }

        report.setStaff(staff);
    }

    private void fillDetails(
            Report report,
            ReportDTOIn dto,
            ReportType type) {

        Category category =
                categoryRepository.findCategoryById(dto.getCategoryId());

        if (category == null) {
            throw new ApiException("Category not found");
        }

        report.setTitle(dto.getTitle());
        report.setDescription(dto.getDescription());
        report.setColor(dto.getColor());
        report.setBrand(dto.getBrand());
        report.setImageUrl(dto.getImageUrl());
        report.setItemDate(dto.getItemDate());
        report.setCategory(category);
        report.setLocations(
                getLocations(dto.getLocationIds(), type)
        );

        checkStaffLocation(report);
    }

    // الموظف يرفع بلاغ في مكانه هو فقط
    private void checkStaffLocation(Report report) {

        if (report.getStaff() == null) {
            return;
        }

        Location staffLocation =
                report.getStaff().getLocation();

        for (Location location : report.getLocations()) {

            if (staffLocation == null
                    || !location.getId().equals(staffLocation.getId())) {

                throw new ApiException(
                        "Staff can only create reports in their own location"
                );
            }
        }
    }

    // FOUND: مكان واحد بالضبط
    // LOST: حد أقصى 3
    private Set<Location> getLocations(
            Set<Integer> locationIds,
            ReportType type) {

        if (type == ReportType.FOUND
                && locationIds.size() != 1) {

            throw new ApiException(
                    "Found report must have exactly one location"
            );
        }

        if (type == ReportType.LOST
                && locationIds.size() > 3) {

            throw new ApiException(
                    "Lost report can have at most 3 locations"
            );
        }

        Set<Location> locations = new HashSet<>();

        for (Integer locationId : locationIds) {

            Location location =
                    locationRepository.findLocationById(locationId);

            if (location == null) {
                throw new ApiException(
                        "Location not found with id: " + locationId
                );
            }

            locations.add(location);
        }

        return locations;
    }

    private List<ReportDTOOut> convertListToDTO(
            List<Report> reports) {

        List<ReportDTOOut> result = new ArrayList<>();

        for (Report report : reports) {
            result.add(convertToDTO(report));
        }

        return result;
    }

    private ReportDTOOut convertToDTO(Report report) {

        Integer userId = null;
        Integer staffId = null;
        String reporterName = null;

        if (report.getUser() != null) {

            userId = report.getUser().getId();
            reporterName = report.getUser().getFullName();

        } else if (report.getStaff() != null) {

            staffId = report.getStaff().getId();
            reporterName = report.getStaff().getFullName();
        }

        List<LocationDTOOut> locations = new ArrayList<>();

        if (report.getLocations() != null) {

            for (Location location : report.getLocations()) {

                locations.add(
                        new LocationDTOOut(
                                location.getId(),
                                location.getName(),
                                location.getDescription(),
                                location.getCity(),
                                location.getType().name(),
                                location.getLatitude(),
                                location.getLongitude(),
                                googleMapsService.buildDirectionsUrl(location)
                        )
                );
            }
        }

        return new ReportDTOOut(
                report.getId(),
                report.getType().name(),
                report.getTitle(),
                report.getDescription(),
                report.getColor(),
                report.getBrand(),
                report.getImageUrl(),
                report.getItemDate(),
                report.getStatus().name(),
                report.getCreatedAt(),
                userId,
                staffId,
                reporterName,
                report.getCategory().getId(),
                report.getCategory().getName(),
                locations
        );
    }
}
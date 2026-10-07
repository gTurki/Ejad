package com.example.ejadwebapplication.Service;

import com.example.ejadwebapplication.Enums.MatchStatus;
import com.example.ejadwebapplication.Enums.ReportStatus;
import com.example.ejadwebapplication.Enums.ReportType;
import com.example.ejadwebapplication.Model.Category;
import com.example.ejadwebapplication.Repository.CategoryRepository;
import com.example.ejadwebapplication.Repository.ReportMatchRepository;
import com.example.ejadwebapplication.Repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {


    private final ReportRepository reportRepository;
    private final ReportMatchRepository reportMatchRepository;
    private final GeminiService geminiService;
    private final CategoryRepository categoryRepository;

    public String generateAdminReport() {

        int totalReports = reportRepository.findAll().size();

        int lostReports = reportRepository
                .findAllByType(ReportType.LOST)
                .size();

        int foundReports = reportRepository
                .findAllByType(ReportType.FOUND)
                .size();

        int matchedReports = reportRepository
                .findAllByStatus(ReportStatus.MATCHED)
                .size();

        int closedReports = reportRepository
                .findAllByStatus(ReportStatus.CLOSED)
                .size();

        int confirmedMatches = reportMatchRepository.countByStatus(MatchStatus.CONFIRMED);
        double returnRate = 0;
        if (lostReports > 0) {
            returnRate = ((double) confirmedMatches / lostReports) * 100;
        }

        List<Category> categories = categoryRepository.findAll();

        StringBuilder categoryStatistics = new StringBuilder();

        for (Category category : categories) {
            int count = category.getReports() == null
                    ? 0
                    : category.getReports().size();

            categoryStatistics.append(category.getName())
                    .append(": ")
                    .append(count)
                    .append(" reports\n");
        }

        String prompt = """
            You are an AI assistant for a Lost and Found system.

            Analyze these statistics and provide a SHORT and useful report for the administrator.

            Overall Statistics:
            Total reports: %d
            Lost reports: %d
            Found reports: %d
            Matched reports: %d
            Closed reports: %d
            Return rate: %.2f%%

            Reports by Category:
            %s

            Format your response exactly with these 3 sections:

            Summary:
            Write 1-2 short sentences summarizing the system.

            Key Insight:
            Mention the most important trend, especially the most frequently reported category.

            Recommendations:
            Give exactly 2 short and practical recommendations based on the statistics.

            Do not add introductions, conclusions, or unnecessary details.
            """.formatted(
                totalReports,
                lostReports,
                foundReports,
                matchedReports,
                closedReports,
                returnRate,
                categoryStatistics
        );

        return geminiService.generateText(prompt);
    }
}

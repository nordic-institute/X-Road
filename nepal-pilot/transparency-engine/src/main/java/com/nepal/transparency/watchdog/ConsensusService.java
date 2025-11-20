package com.nepal.transparency.watchdog;

import com.nepal.transparency.budget.BudgetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsensusService {

    private final CorruptionReportRepository reportRepository;
    private final BudgetService budgetService;
    private final com.nepal.transparency.integration.FCGOClient fcgoClient;

    public static final int WEIGHT_ANONYMOUS = 1;
    public static final int WEIGHT_CITIZEN = 5;
    public static final int WEIGHT_RESIDENT = 10;
    public static final int WEIGHT_ENGINEER = 50;

    public static final int THRESHOLD_VERIFIED = 50;
    public static final int THRESHOLD_ACTIONABLE = 100;

    @Transactional
    public void processReport(CorruptionReport report) {
        // Sybil Attack Prevention: One Person, One Vote per project
        if (reportRepository.existsByProjectTidAndWhistleblowerHash(report.getProjectTid(), report.getWhistleblowerHash())) {
            throw new RuntimeException("Duplicate Report: User has already reported this project.");
        }

        // 1. Calculate Score for this report based on user type
        int score = calculateWeight(report.getUserType());
        report.setReputationPoints(score);
        reportRepository.save(report);

        // 2. Check Aggregate Score for the Project
        checkProjectConsensus(report.getProjectTid());
    }

    private int calculateWeight(String userType) {
        return switch (userType) {
            case "ANONYMOUS" -> WEIGHT_ANONYMOUS;
            case "CITIZEN" -> WEIGHT_CITIZEN;
            case "RESIDENT" -> WEIGHT_RESIDENT;
            case "ENGINEER" -> WEIGHT_ENGINEER;
            default -> 1;
        };
    }

    private void checkProjectConsensus(String projectTid) {
        List<CorruptionReport> reports = reportRepository.findByProjectTid(projectTid);

        int totalScore = reports.stream()
                .mapToInt(CorruptionReport::getReputationPoints)
                .sum();

        if (totalScore > THRESHOLD_ACTIONABLE) {
            // Trigger Payment Freeze
            budgetService.freezeBudget(projectTid);
            fcgoClient.notifyFreeze(projectTid, "High Corruption Score: " + totalScore);
            System.out.println("ALERT: Project " + projectTid + " frozen due to high corruption score: " + totalScore);
        } else if (totalScore > THRESHOLD_VERIFIED) {
            // Mark as Public Warning
             System.out.println("WARNING: Project " + projectTid + " has verified complaints. Score: " + totalScore);
        }
    }
}

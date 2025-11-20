package com.nepal.transparency;

import com.nepal.transparency.budget.Budget;
import com.nepal.transparency.budget.BudgetService;
import com.nepal.transparency.watchdog.ConsensusService;
import com.nepal.transparency.watchdog.CorruptionReport;
import com.nepal.transparency.watchdog.Evidence;
import com.nepal.transparency.watchdog.GeoLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TransparencyEngineTests {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private ConsensusService consensusService;

    @Test
    void testBudgetTrackingAndFreeze() {
        // 1. Allocate Budget
        Budget budget = budgetService.allocateBudget(
                "2025", "BAG", "KAL", "RD", "001",
                "Kalanki Road Paving",
                new BigDecimal("10000000.00")
        );

        assertNotNull(budget);
        assertEquals("2025-BAG-KAL-RD-001", budget.getTrackingId().toString());
        assertFalse(budget.isFrozen());

        // 2. Simulate Reports to Trigger Freeze
        // Threshold for Actionable is > 100.
        // Engineer weight is 50. So 3 Engineers should trigger it (150 > 100).

        String tid = budget.getTrackingId().toString();

        // Report 1 (Engineer 1)
        submitReport(tid, "ENGINEER", "user1");
        // Report 2 (Engineer 2)
        submitReport(tid, "ENGINEER", "user2");
        // Report 3 (Engineer 3)
        submitReport(tid, "ENGINEER", "user3");

        // 3. Verify Budget is Frozen
        Budget updatedBudget = budgetService.getBudget(tid).orElseThrow();
        assertTrue(updatedBudget.isFrozen(), "Budget should be frozen after 3 engineer reports");

        // 4. Try to spend (Should fail)
        assertThrows(RuntimeException.class, () -> {
            budgetService.spendBudget(tid, new BigDecimal("1000.00"), "ABC Construction");
        });
    }

    @Test
    void testSybilAttackPrevention() {
        // 1. Allocate Budget
        Budget budget = budgetService.allocateBudget(
                "2025", "LUM", "BUT", "SCH", "999",
                "Butwal School Wall",
                new BigDecimal("500000.00")
        );
        String tid = budget.getTrackingId().toString();

        // 2. User A reports once (Success)
        submitReport(tid, "CITIZEN", "userA");

        // 3. User A tries to report again (Should Fail)
        assertThrows(RuntimeException.class, () -> {
            submitReport(tid, "CITIZEN", "userA");
        });
    }

    private void submitReport(String tid, String userType, String userHash) {
        CorruptionReport report = new CorruptionReport();
        report.setProjectTid(tid);
        report.setTimestamp(LocalDateTime.now());
        report.setUserType(userType);
        report.setLocation(new GeoLocation(27.7, 85.3, "Chandragiri-12"));
        report.setEvidence(new Evidence("hash123", "Bad cement"));
        report.setWhistleblowerHash(userHash);

        consensusService.processReport(report);
    }
}

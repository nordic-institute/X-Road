package com.nepal.transparency.api;

import com.nepal.transparency.budget.Budget;
import com.nepal.transparency.budget.BudgetService;
import com.nepal.transparency.watchdog.ConsensusService;
import com.nepal.transparency.watchdog.CorruptionReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransparencyController {

    private final BudgetService budgetService;
    private final ConsensusService consensusService;

    // --- Smart Budget Endpoints ---

    @PostMapping("/budget/allocate")
    public ResponseEntity<Budget> allocateBudget(
            @RequestHeader(value = "X-Road-Client", required = false) String xRoadClient,
            @RequestParam String year,
            @RequestParam String province,
            @RequestParam String district,
            @RequestParam String type,
            @RequestParam String sequence,
            @RequestParam String projectName,
            @RequestParam BigDecimal amount) {

        System.out.println("X-Road Request from: " + xRoadClient);
        Budget budget = budgetService.allocateBudget(year, province, district, type, sequence, projectName, amount);
        return ResponseEntity.ok(budget);
    }

    @GetMapping("/budget/{tid}")
    public ResponseEntity<Budget> getBudget(
            @RequestHeader(value = "X-Road-Client", required = false) String xRoadClient,
            @PathVariable String tid) {
        return budgetService.getBudget(tid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/budgets")
    public ResponseEntity<java.util.List<Budget>> getAllBudgets(
            @RequestHeader(value = "X-Road-Client", required = false) String xRoadClient
    ) {
        return ResponseEntity.ok(budgetService.getAllBudgets());
    }

    // --- Watchdog Endpoints ---

    @PostMapping("/report")
    public ResponseEntity<String> submitReport(
            @RequestHeader(value = "X-Road-Client", required = false) String xRoadClient,
            @RequestHeader(value = "X-Road-UserId", required = false) String xRoadUserId,
            @RequestBody CorruptionReport report) {

        System.out.println("X-Road Report via Client: " + xRoadClient + ", User: " + xRoadUserId);

        // In a production X-Road environment, the Security Server ensures the headers are authentic.

        consensusService.processReport(report);
        return ResponseEntity.ok("Report received. Consensus check initiated.");
    }
}

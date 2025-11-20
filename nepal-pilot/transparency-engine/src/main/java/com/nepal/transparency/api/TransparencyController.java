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
            @RequestParam String year,
            @RequestParam String province,
            @RequestParam String district,
            @RequestParam String type,
            @RequestParam String sequence,
            @RequestParam String projectName,
            @RequestParam BigDecimal amount) {

        Budget budget = budgetService.allocateBudget(year, province, district, type, sequence, projectName, amount);
        return ResponseEntity.ok(budget);
    }

    @GetMapping("/budget/{tid}")
    public ResponseEntity<Budget> getBudget(@PathVariable String tid) {
        return budgetService.getBudget(tid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/budgets")
    public ResponseEntity<java.util.List<Budget>> getAllBudgets() {
        return ResponseEntity.ok(budgetService.getAllBudgets());
    }

    // --- Watchdog Endpoints ---

    @PostMapping("/report")
    public ResponseEntity<String> submitReport(@RequestBody CorruptionReport report) {
        // In a real X-Road implementation, we would verify the X-Road signature here.
        // checkSignature(headers);

        consensusService.processReport(report);
        return ResponseEntity.ok("Report received. Consensus check initiated.");
    }
}

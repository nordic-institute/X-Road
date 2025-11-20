package com.nepal.transparency.budget;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;

    @Transactional
    public Budget allocateBudget(String year, String province, String district, String type, String sequence,
                                 String projectName, BigDecimal amount) {
        TrackingID tid = new TrackingID(year, province, district, type, sequence);
        Budget budget = new Budget();
        budget.setTrackingId(tid);
        budget.setProjectName(projectName);
        budget.setTotalAmount(amount);
        budget.setSpentAmount(BigDecimal.ZERO);
        budget.setFrozen(false);

        return budgetRepository.save(budget);
    }

    public Optional<Budget> getBudget(String tidString) {
        return budgetRepository.findById(TrackingID.fromString(tidString));
    }

    public java.util.List<Budget> getAllBudgets() {
        return budgetRepository.findAll();
    }

    @Transactional
    public Budget spendBudget(String tidString, BigDecimal amount, String recipient) {
        Budget budget = getBudget(tidString).orElseThrow(() -> new RuntimeException("Budget not found"));

        if (budget.isFrozen()) {
            throw new RuntimeException("Payment Frozen: Budget is under investigation");
        }

        if (budget.getRemainingAmount().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        budget.setSpentAmount(budget.getSpentAmount().add(amount));
        budget.setRecipient(recipient);

        return budgetRepository.save(budget);
    }

    @Transactional
    public void freezeBudget(String tidString) {
        Budget budget = getBudget(tidString).orElseThrow(() -> new RuntimeException("Budget not found"));
        budget.setFrozen(true);
        budgetRepository.save(budget);
    }

    @Transactional
    public void unfreezeBudget(String tidString) {
        Budget budget = getBudget(tidString).orElseThrow(() -> new RuntimeException("Budget not found"));
        budget.setFrozen(false);
        budgetRepository.save(budget);
    }
}

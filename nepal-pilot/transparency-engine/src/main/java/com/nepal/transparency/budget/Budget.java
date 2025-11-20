package com.nepal.transparency.budget;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "budgets")
@Data
public class Budget {

    @EmbeddedId
    private TrackingID trackingId;

    private String projectName;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal spentAmount;

    private String recipient;

    private boolean frozen;

    public BigDecimal getRemainingAmount() {
        return totalAmount.subtract(spentAmount);
    }
}

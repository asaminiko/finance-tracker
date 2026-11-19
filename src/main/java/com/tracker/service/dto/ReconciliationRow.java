package com.tracker.service.dto;

import java.math.BigDecimal;

public record ReconciliationRow(
        String month,
        BigDecimal openingBalance,
        BigDecimal expected,
        BigDecimal actual,
        BigDecimal difference,
        String status) {
}

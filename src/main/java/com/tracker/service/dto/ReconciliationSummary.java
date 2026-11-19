package com.tracker.service.dto;

import java.math.BigDecimal;

public record ReconciliationSummary(
        BigDecimal expected,
        BigDecimal actual,
        BigDecimal difference,
        String verdict) {
}

package com.tracker.service.dto;

import java.math.BigDecimal;

public record CategoryReportRow(
        String category,
        BigDecimal amount,
        BigDecimal percent) {
}

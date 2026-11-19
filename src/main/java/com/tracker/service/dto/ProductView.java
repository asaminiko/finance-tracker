package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductView(
        long id,
        String kind,
        String name,
        long accountId,
        String account,
        BigDecimal amount,
        BigDecimal ratePct,
        BigDecimal bonusPct,
        boolean taxable,
        LocalDate openDate,
        int termMonths,
        LocalDate endDate,
        String status,
        String state) {
}

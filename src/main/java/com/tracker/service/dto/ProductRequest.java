package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductRequest(
        String kind,
        String name,
        Long accountId,
        BigDecimal amount,
        BigDecimal ratePct,
        BigDecimal bonusPct,
        Boolean taxable,
        LocalDate openDate,
        Integer termMonths) {
}

package com.tracker.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        Long id,
        LocalDate date,
        BigDecimal amount,
        Account account,
        Category category,
        String description,
        TransactionType type,
        SourceType sourceType,
        Long sourceId) {
}

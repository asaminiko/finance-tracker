package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionView(
        long id, LocalDate date, String account, String category, String type, BigDecimal amount, String description) {
}

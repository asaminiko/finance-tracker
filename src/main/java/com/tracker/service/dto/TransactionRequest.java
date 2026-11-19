package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        LocalDate date, Long accountId, Long categoryId, String type, BigDecimal amount, String description) {
}

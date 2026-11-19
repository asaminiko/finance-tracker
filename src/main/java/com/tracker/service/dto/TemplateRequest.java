package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TemplateRequest(
        String name,
        BigDecimal amount,
        Long categoryId,
        Long accountId,
        String description,
        LocalDate nextRunDate) {
}

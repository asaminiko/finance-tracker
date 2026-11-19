package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TemplateView(
        long id,
        String name,
        BigDecimal amount,
        String type,
        String category,
        String account,
        String description,
        int dayOfMonth,
        LocalDate nextRunDate) {
}

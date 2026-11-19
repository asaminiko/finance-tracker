package com.tracker.service.dto;

import java.math.BigDecimal;
import java.util.List;

public record CategoryReport(
        String kind,
        BigDecimal total,
        List<CategoryReportRow> rows) {
}

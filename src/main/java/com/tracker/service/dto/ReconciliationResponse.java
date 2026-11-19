package com.tracker.service.dto;

import java.util.List;

public record ReconciliationResponse(
        ProductView product,
        List<ReconciliationRow> rows,
        ReconciliationSummary summary) {
}

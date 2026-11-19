package com.tracker.service.dto;

import java.time.LocalDate;
import java.util.List;

public record ReceiptRequest(
        LocalDate date,
        String shop,
        Long accountId,
        List<ReceiptItemRequest> items,
        String scanBase64,
        String scanExt) {
}

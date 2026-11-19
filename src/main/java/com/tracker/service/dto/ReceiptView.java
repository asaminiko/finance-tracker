package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptView(
        long id,
        LocalDate date,
        String shop,
        String account,
        BigDecimal total,
        boolean hasScan,
        List<ReceiptItemView> items) {
}

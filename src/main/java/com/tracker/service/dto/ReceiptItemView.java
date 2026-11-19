package com.tracker.service.dto;

import java.math.BigDecimal;

public record ReceiptItemView(String name, String category, BigDecimal amount) {
}

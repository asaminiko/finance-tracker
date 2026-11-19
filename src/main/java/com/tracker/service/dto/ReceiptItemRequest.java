package com.tracker.service.dto;

import java.math.BigDecimal;

public record ReceiptItemRequest(String name, BigDecimal amount, Long categoryId) {
}

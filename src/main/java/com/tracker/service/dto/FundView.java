package com.tracker.service.dto;

import java.math.BigDecimal;

public record FundView(
        long id,
        String name,
        BigDecimal target,
        BigDecimal accumulated,
        BigDecimal percent) {
}

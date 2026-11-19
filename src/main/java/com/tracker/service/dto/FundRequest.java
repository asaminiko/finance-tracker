package com.tracker.service.dto;

import java.math.BigDecimal;

public record FundRequest(String name, BigDecimal target) {
}

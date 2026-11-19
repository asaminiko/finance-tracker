package com.tracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InterestRequest(LocalDate date, BigDecimal amount) {
}

package com.tracker.service.dto;

import java.math.BigDecimal;

public record ContributionRequest(Long accountId, BigDecimal amount) {
}

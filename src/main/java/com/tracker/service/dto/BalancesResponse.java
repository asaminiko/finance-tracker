package com.tracker.service.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalancesResponse(List<BalanceItem> items, BigDecimal total) {
}

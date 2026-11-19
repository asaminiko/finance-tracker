package com.tracker.service.dto;

import java.math.BigDecimal;

public record BalanceItem(long id, String name, String owner, BigDecimal balance) {
}

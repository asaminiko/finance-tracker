package com.tracker.service.dto;

import java.math.BigDecimal;

public record AccountRequest(String name, String owner, BigDecimal initialBalance) {
}

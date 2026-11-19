package com.tracker.domain;

import java.math.BigDecimal;

public record Account(long id, String name, String owner, BigDecimal initialBalance) {
}

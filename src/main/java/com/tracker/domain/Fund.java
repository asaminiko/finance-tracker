package com.tracker.domain;

import java.math.BigDecimal;

public record Fund(long id, String name, BigDecimal target) {
}

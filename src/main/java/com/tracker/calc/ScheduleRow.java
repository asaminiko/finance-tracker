package com.tracker.calc;

import java.math.BigDecimal;

public record ScheduleRow(String month, BigDecimal openingBalance, BigDecimal expected) {
}

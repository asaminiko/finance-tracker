package com.tracker.calc;

import java.math.BigDecimal;

public interface InterestCalculator {

    BigDecimal monthlyInterest(BigDecimal balance, BigDecimal annualRatePct);
}

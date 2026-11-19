package com.tracker.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BaseInterestCalculator implements InterestCalculator {

    private static final BigDecimal DIVISOR = new BigDecimal("1200");

    @Override
    public BigDecimal monthlyInterest(BigDecimal balance, BigDecimal annualRatePct) {
        return balance.multiply(annualRatePct).divide(DIVISOR, 2, RoundingMode.HALF_UP);
    }
}

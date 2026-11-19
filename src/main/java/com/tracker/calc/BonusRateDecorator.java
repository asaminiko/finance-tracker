package com.tracker.calc;

import java.math.BigDecimal;

public class BonusRateDecorator implements InterestCalculator {

    private final InterestCalculator inner;
    private final BigDecimal bonusRatePct;

    public BonusRateDecorator(InterestCalculator inner, BigDecimal bonusRatePct) {
        this.inner = inner;
        this.bonusRatePct = bonusRatePct;
    }

    @Override
    public BigDecimal monthlyInterest(BigDecimal balance, BigDecimal annualRatePct) {
        return inner.monthlyInterest(balance, annualRatePct.add(bonusRatePct));
    }
}

package com.tracker.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TaxDecorator implements InterestCalculator {

    private final InterestCalculator inner;
    private final BigDecimal taxRate;

    public TaxDecorator(InterestCalculator inner, BigDecimal taxRate) {
        this.inner = inner;
        this.taxRate = taxRate;
    }

    @Override
    public BigDecimal monthlyInterest(BigDecimal balance, BigDecimal annualRatePct) {
        BigDecimal gross = inner.monthlyInterest(balance, annualRatePct);
        return gross.multiply(BigDecimal.ONE.subtract(taxRate)).setScale(2, RoundingMode.HALF_UP);
    }
}

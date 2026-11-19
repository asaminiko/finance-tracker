package com.tracker.domain;

import java.time.LocalDate;

public class ClosedState implements ProductState {

    @Override
    public String code() {
        return "CLOSED";
    }

    @Override
    public void checkRecordInterest(BankProduct product, LocalDate date, LocalDate today) {
        throw new BusinessRuleException("Продукт закрито, запис відсотків заборонено");
    }

    @Override
    public void checkClose() {
        throw new BusinessRuleException("Продукт уже закрито");
    }
}

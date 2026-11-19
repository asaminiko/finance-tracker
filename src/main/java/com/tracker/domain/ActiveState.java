package com.tracker.domain;

import java.time.LocalDate;

public class ActiveState implements ProductState {

    @Override
    public String code() {
        return "ACTIVE";
    }

    @Override
    public void checkRecordInterest(BankProduct product, LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new BusinessRuleException("Дата фактичних відсотків не може бути в майбутньому");
        }
    }

    @Override
    public void checkClose() {
    }
}

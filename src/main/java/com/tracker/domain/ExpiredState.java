package com.tracker.domain;

import java.time.LocalDate;

public class ExpiredState implements ProductState {

    @Override
    public String code() {
        return "EXPIRED";
    }

    @Override
    public void checkRecordInterest(BankProduct product, LocalDate date, LocalDate today) {
    }

    @Override
    public void checkClose() {
    }
}

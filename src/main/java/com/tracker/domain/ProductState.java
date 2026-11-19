package com.tracker.domain;

import java.time.LocalDate;

public interface ProductState {

    String code();

    void checkRecordInterest(BankProduct product, LocalDate date, LocalDate today);

    void checkClose();
}

package com.tracker.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTemplate(
        long id,
        String name,
        BigDecimal amount,
        Category category,
        Account account,
        String description,
        int dayOfMonth,
        LocalDate nextRunDate) {

    public TransactionType type() {
        return amount.signum() > 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
    }

    public Transaction toPrototype() {
        return new Transaction(
                null,
                nextRunDate,
                amount,
                account,
                category,
                description,
                type(),
                SourceType.MANUAL,
                null);
    }
}

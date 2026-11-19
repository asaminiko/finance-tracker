package com.tracker.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BankProduct {

    private final long id;
    private final ProductKind kind;
    private final String name;
    private final Account account;
    private final BigDecimal amount;
    private final BigDecimal ratePct;
    private final BigDecimal bonusPct;
    private final boolean taxable;
    private final LocalDate openDate;
    private final int termMonths;
    private ProductStatus status;
    private ProductState state;

    public BankProduct(
            long id,
            ProductKind kind,
            String name,
            Account account,
            BigDecimal amount,
            BigDecimal ratePct,
            BigDecimal bonusPct,
            boolean taxable,
            LocalDate openDate,
            int termMonths,
            ProductStatus status,
            ProductState state) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.account = account;
        this.amount = amount;
        this.ratePct = ratePct;
        this.bonusPct = bonusPct;
        this.taxable = taxable;
        this.openDate = openDate;
        this.termMonths = termMonths;
        this.status = status;
        this.state = state;
    }

    public long id() {
        return id;
    }

    public ProductKind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public Account account() {
        return account;
    }

    public BigDecimal amount() {
        return amount;
    }

    public BigDecimal ratePct() {
        return ratePct;
    }

    public BigDecimal bonusPct() {
        return bonusPct;
    }

    public boolean taxable() {
        return taxable;
    }

    public LocalDate openDate() {
        return openDate;
    }

    public int termMonths() {
        return termMonths;
    }

    public ProductStatus status() {
        return status;
    }

    public ProductState state() {
        return state;
    }

    public LocalDate endDate() {
        return openDate.plusMonths(termMonths);
    }

    public void checkRecordInterest(LocalDate date, LocalDate today) {
        state.checkRecordInterest(this, date, today);
    }

    public void checkClose() {
        state.checkClose();
    }

    public void close() {
        state.checkClose();
        this.status = ProductStatus.CLOSED;
        this.state = new ClosedState();
    }
}

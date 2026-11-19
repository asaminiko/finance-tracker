package com.tracker.domain;

import java.util.List;

public final class SystemCategories {

    public static final String DEPOSIT_INTEREST = "Відсотки за вкладом";
    public static final String CREDIT_INTEREST = "Відсотки за кредитом";
    public static final String FUND_CONTRIBUTION = "Внесок у фонд";
    public static final List<String> ALL = List.of(DEPOSIT_INTEREST, CREDIT_INTEREST, FUND_CONTRIBUTION);

    private SystemCategories() {
    }
}

package com.tracker.calc;

import com.tracker.domain.ProductKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class ScheduleBuilder {

    private ScheduleBuilder() {
    }

    public static List<ScheduleRow> build(
            ProductKind kind,
            BigDecimal principal,
            BigDecimal ratePct,
            int termMonths,
            LocalDate openDate,
            InterestCalculator calculator) {
        YearMonth start = YearMonth.from(openDate);
        BigDecimal scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP);
        List<ScheduleRow> rows = new ArrayList<>(termMonths);
        if (kind == ProductKind.DEPOSIT) {
            for (int i = 1; i <= termMonths; i++) {
                String month = start.plusMonths(i).toString();
                BigDecimal expected = calculator.monthlyInterest(scaledPrincipal, ratePct);
                rows.add(new ScheduleRow(month, scaledPrincipal, expected));
            }
            return rows;
        }
        BigDecimal regularBody = scaledPrincipal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        BigDecimal balance = scaledPrincipal;
        for (int i = 1; i <= termMonths; i++) {
            String month = start.plusMonths(i).toString();
            BigDecimal openingBalance = balance;
            BigDecimal expected = calculator.monthlyInterest(openingBalance, ratePct);
            BigDecimal body = i == termMonths ? balance : regularBody;
            balance = balance.subtract(body);
            rows.add(new ScheduleRow(month, openingBalance, expected));
        }
        return rows;
    }
}

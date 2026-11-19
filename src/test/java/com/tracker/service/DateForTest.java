package com.tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DateForTest {

    @Test
    void day31AcrossJanuaryFebruaryMarchDoesNotDrift() {
        LocalDate jan = LedgerService.dateFor(YearMonth.of(2026, 1), 31);
        LocalDate feb = LedgerService.dateFor(YearMonth.from(jan).plusMonths(1), 31);
        LocalDate mar = LedgerService.dateFor(YearMonth.from(feb).plusMonths(1), 31);

        assertEquals(LocalDate.of(2026, 1, 31), jan);
        assertEquals(LocalDate.of(2026, 2, 28), feb);
        assertEquals(LocalDate.of(2026, 3, 31), mar);
    }

    @Test
    void clampsToLeapFebruaryAndThirtyDayMonth() {
        assertEquals(LocalDate.of(2024, 2, 29), LedgerService.dateFor(YearMonth.of(2024, 2), 31));
        assertEquals(LocalDate.of(2026, 4, 30), LedgerService.dateFor(YearMonth.of(2026, 4), 31));
        assertEquals(LocalDate.of(2026, 5, 15), LedgerService.dateFor(YearMonth.of(2026, 5), 15));
    }
}

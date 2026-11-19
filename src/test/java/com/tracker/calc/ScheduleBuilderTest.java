package com.tracker.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tracker.domain.ProductKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleBuilderTest {

    @Test
    void creditControlExampleOpeningBalances() {
        List<ScheduleRow> rows = ScheduleBuilder.build(
                ProductKind.CREDIT,
                new BigDecimal("1000.00"),
                new BigDecimal("12"),
                3,
                LocalDate.of(2026, 1, 15),
                new BaseInterestCalculator());

        assertEquals(3, rows.size());
        assertEquals(new BigDecimal("1000.00"), rows.get(0).openingBalance());
        assertEquals(new BigDecimal("666.67"), rows.get(1).openingBalance());
        assertEquals(new BigDecimal("333.34"), rows.get(2).openingBalance());
    }

    @Test
    void creditControlExampleExpectedInterestAndMonths() {
        List<ScheduleRow> rows = ScheduleBuilder.build(
                ProductKind.CREDIT,
                new BigDecimal("1000.00"),
                new BigDecimal("12"),
                3,
                LocalDate.of(2026, 1, 15),
                new BaseInterestCalculator());

        assertEquals("2026-02", rows.get(0).month());
        assertEquals(new BigDecimal("10.00"), rows.get(0).expected());
        assertEquals(new BigDecimal("6.67"), rows.get(1).expected());
        assertEquals(new BigDecimal("3.33"), rows.get(2).expected());
    }

    @Test
    void depositBaseChainControlExample() {
        InterestCalculator calculator = new BaseInterestCalculator();
        List<ScheduleRow> rows = ScheduleBuilder.build(
                ProductKind.DEPOSIT,
                new BigDecimal("10000.00"),
                new BigDecimal("12"),
                3,
                LocalDate.of(2026, 1, 10),
                calculator);

        assertEquals(3, rows.size());
        assertEquals(new BigDecimal("10000.00"), rows.get(0).openingBalance());
        assertEquals(new BigDecimal("100.00"), rows.get(0).expected());
        assertEquals(new BigDecimal("100.00"), rows.get(2).expected());
    }

    @Test
    void depositBasePlusBonusChainControlExample() {
        InterestCalculator calculator = new BonusRateDecorator(
                new BaseInterestCalculator(),
                new BigDecimal("2"));
        List<ScheduleRow> rows = ScheduleBuilder.build(
                ProductKind.DEPOSIT,
                new BigDecimal("10000.00"),
                new BigDecimal("12"),
                2,
                LocalDate.of(2026, 1, 10),
                calculator);

        assertEquals(new BigDecimal("116.67"), rows.get(0).expected());
        assertEquals(new BigDecimal("116.67"), rows.get(1).expected());
    }

    @Test
    void depositBasePlusTaxChainControlExample() {
        InterestCalculator calculator = new TaxDecorator(
                new BaseInterestCalculator(),
                new BigDecimal("0.10"));
        List<ScheduleRow> rows = ScheduleBuilder.build(
                ProductKind.DEPOSIT,
                new BigDecimal("10000.00"),
                new BigDecimal("12"),
                2,
                LocalDate.of(2026, 1, 10),
                calculator);

        assertEquals(new BigDecimal("90.00"), rows.get(0).expected());
        assertEquals(new BigDecimal("90.00"), rows.get(1).expected());
    }

    @Test
    void depositBasePlusBonusPlusTaxChain() {
        InterestCalculator calculator = new TaxDecorator(
                new BonusRateDecorator(
                        new BaseInterestCalculator(),
                        new BigDecimal("2")),
                new BigDecimal("0.10"));
        List<ScheduleRow> rows = ScheduleBuilder.build(
                ProductKind.DEPOSIT,
                new BigDecimal("10000.00"),
                new BigDecimal("12"),
                2,
                LocalDate.of(2026, 1, 10),
                calculator);

        assertEquals(new BigDecimal("105.00"), rows.get(0).expected());
        assertEquals(new BigDecimal("105.00"), rows.get(1).expected());
    }
}

package com.tracker.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StateResolverTest {

    @Test
    void resolvesClosedRegardlessOfDates() {
        LocalDate endDate = LocalDate.of(2026, 6, 1);

        assertEquals("CLOSED", StateResolver.resolve(ProductStatus.CLOSED, endDate, LocalDate.of(2026, 5, 1)).code());
        assertEquals("CLOSED", StateResolver.resolve(ProductStatus.CLOSED, endDate, LocalDate.of(2026, 6, 1)).code());
        assertEquals("CLOSED", StateResolver.resolve(ProductStatus.CLOSED, endDate, LocalDate.of(2026, 7, 1)).code());
    }

    @Test
    void resolvesActiveBeforeAndOnEndDateAndExpiredAfter() {
        LocalDate endDate = LocalDate.of(2026, 6, 1);

        assertEquals("ACTIVE", StateResolver.resolve(ProductStatus.ACTIVE, endDate, LocalDate.of(2026, 5, 31)).code());
        assertEquals("ACTIVE", StateResolver.resolve(ProductStatus.ACTIVE, endDate, LocalDate.of(2026, 6, 1)).code());
        assertEquals("EXPIRED", StateResolver.resolve(ProductStatus.ACTIVE, endDate, LocalDate.of(2026, 6, 2)).code());
    }

    @Test
    void activeStateAllowsInterestUpToTodayAndForbidsFutureDate() {
        LocalDate today = LocalDate.of(2026, 4, 15);
        BankProduct product = sampleProduct(new ActiveState());

        assertDoesNotThrow(() -> product.checkRecordInterest(LocalDate.of(2026, 4, 15), today));
        assertThrows(
                BusinessRuleException.class,
                () -> product.checkRecordInterest(LocalDate.of(2026, 4, 16), today));
    }

    @Test
    void activeAndExpiredStatesAllowCloseAndTransitionToClosed() {
        BankProduct active = sampleProduct(new ActiveState());
        BankProduct expired = sampleProduct(new ExpiredState());

        active.close();
        expired.close();

        assertEquals("CLOSED", active.state().code());
        assertEquals(ProductStatus.CLOSED, active.status());
        assertEquals("CLOSED", expired.state().code());
        assertEquals(ProductStatus.CLOSED, expired.status());
    }

    @Test
    void expiredStateAllowsInterestRecording() {
        BankProduct expired = sampleProduct(new ExpiredState());

        assertDoesNotThrow(() -> expired.checkRecordInterest(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 8, 1)));
    }

    @Test
    void closedStateForbidsInterestAndClose() {
        BankProduct closed = sampleProduct(new ClosedState());

        assertThrows(
                BusinessRuleException.class,
                () -> closed.checkRecordInterest(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 4, 10)));
        assertThrows(BusinessRuleException.class, closed::close);
    }

    private static BankProduct sampleProduct(ProductState state) {
        Account account = new Account(1L, "Зарплатна картка", "Анна", new BigDecimal("5000.00"));
        return new BankProduct(
                1L,
                ProductKind.DEPOSIT,
                "Депозит Стандарт",
                account,
                new BigDecimal("10000.00"),
                new BigDecimal("12.00"),
                BigDecimal.ZERO.setScale(2),
                false,
                LocalDate.of(2026, 1, 10),
                6,
                state instanceof ClosedState ? ProductStatus.CLOSED : ProductStatus.ACTIVE,
                state);
    }
}

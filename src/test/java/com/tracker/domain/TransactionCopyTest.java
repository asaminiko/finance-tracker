package com.tracker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionCopyTest {

    @Test
    void copyResetsIdUpdatesDateAndSharesCategoryInstance() {
        Account account = new Account(1L, "Зарплатна картка", "Анна", new BigDecimal("5000.00"));
        Category category = new Category(2L, "Зарплата", false);
        Transaction original = new Transaction(
                10L,
                LocalDate.of(2026, 1, 31),
                new BigDecimal("25000.00"),
                account,
                category,
                "Зарплата за місяць",
                TransactionType.INCOME,
                SourceType.MANUAL,
                null);

        Transaction copy = original.copy(LocalDate.of(2026, 2, 28));

        assertNull(copy.id());
        assertEquals(LocalDate.of(2026, 2, 28), copy.date());
        assertSame(original.category(), copy.category());
        assertEquals(10L, original.id());
    }

    @Test
    void copyPreservesOtherFieldsAndDoesNotMutateOriginal() {
        Account account = new Account(1L, "Зарплатна картка", "Анна", new BigDecimal("5000.00"));
        Category category = new Category(2L, "Зарплата", false);
        Transaction original = new Transaction(
                10L,
                LocalDate.of(2026, 1, 31),
                new BigDecimal("25000.00"),
                account,
                category,
                "Зарплата за місяць",
                TransactionType.INCOME,
                SourceType.MANUAL,
                null);

        Transaction copy = original.copy(LocalDate.of(2026, 2, 28));

        assertEquals(original.amount(), copy.amount());
        assertEquals(original.account(), copy.account());
        assertEquals(original.description(), copy.description());
        assertEquals(LocalDate.of(2026, 1, 31), original.date());
    }

    @Test
    void prototypeRegistryCreatesCopyWithTargetDate() {
        Account account = new Account(1L, "Готівка", "Анна", new BigDecimal("1200.00"));
        Category category = new Category(3L, "Оренда", false);
        Transaction prototype = new Transaction(
                5L,
                LocalDate.of(2026, 1, 15),
                new BigDecimal("-8000.00"),
                account,
                category,
                "Оренда квартири",
                TransactionType.EXPENSE,
                SourceType.MANUAL,
                null);
        PrototypeRegistry registry = new PrototypeRegistry();
        registry.register(1L, prototype);

        Transaction copy = registry.createFrom(1L, LocalDate.of(2026, 2, 15));

        assertNull(copy.id());
        assertEquals(LocalDate.of(2026, 2, 15), copy.date());
        assertSame(category, copy.category());
        assertEquals(TransactionType.EXPENSE, copy.type());
    }

    @Test
    void copyWithoutArgsResetsIdAndKeepsDateAndCategory() {
        Account account = new Account(1L, "Готівка", "Анна", new BigDecimal("1200.00"));
        Category category = new Category(3L, "Оренда", false);
        Transaction original = new Transaction(
                5L,
                LocalDate.of(2026, 1, 15),
                new BigDecimal("-8000.00"),
                account,
                category,
                "Оренда квартири",
                TransactionType.EXPENSE,
                SourceType.MANUAL,
                null);

        Transaction copy = original.copy();

        assertNull(copy.id());
        assertEquals(original.date(), copy.date());
        assertSame(category, copy.category());
    }
}

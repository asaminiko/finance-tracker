package com.tracker.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tracker.domain.Category;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.TransactionRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportValidatorTest {

    private final ImportValidator validator = new ImportValidator();

    @Test
    void validRowsPassAndMapToTransactionRequests() {
        Map<String, Category> categoriesByName = Map.of(
                "зарплата", new Category(1L, "Зарплата", false),
                "продукти", new Category(2L, "Продукти", false));
        Map<String, Long> accountIdsByName = Map.of("готівка", 10L);
        List<TransferRow> rows = List.of(
                new TransferRow(1, "2026-03-01", "5000.00", "Зарплата", "Готівка", "Аванс", null),
                new TransferRow(2, "02.03.2026", "-250,50", "Продукти", "Готівка", "Вечеря", null));

        List<TransactionRequest> requests = validator.validate(rows, categoriesByName, accountIdsByName);

        assertEquals(2, requests.size());
        assertEquals("INCOME", requests.get(0).type());
        assertEquals(new BigDecimal("5000.00"), requests.get(0).amount());
        assertEquals("EXPENSE", requests.get(1).type());
    }

    @Test
    void unknownOrSystemCategoryThrowsValidationExceptionWithRowPrefix() {
        Map<String, Category> categoriesByName = Map.of(
                "продукти", new Category(1L, "Продукти", false),
                "внесок у фонд", new Category(2L, "Внесок у фонд", true));
        Map<String, Long> accountIdsByName = Map.of("готівка", 10L);
        List<TransferRow> unknownCategoryRows = List.of(
                new TransferRow(2, "2026-03-01", "-100.00", "Невідома", "Готівка", "", null));
        List<TransferRow> systemCategoryRows = List.of(
                new TransferRow(3, "2026-03-01", "-100.00", "Внесок у фонд", "Готівка", "", null));

        ValidationException unknownEx = assertThrows(
                ValidationException.class,
                () -> validator.validate(unknownCategoryRows, categoriesByName, accountIdsByName));
        ValidationException systemEx = assertThrows(
                ValidationException.class,
                () -> validator.validate(systemCategoryRows, categoriesByName, accountIdsByName));

        assertTrue(unknownEx.getMessage().startsWith("Рядок 2: "));
        assertTrue(systemEx.getMessage().startsWith("Рядок 3: "));
    }

    @Test
    void amountWithThreeDecimalsThrowsValidationExceptionWithRowPrefix() {
        Map<String, Category> categoriesByName = Map.of(
                "продукти", new Category(1L, "Продукти", false));
        Map<String, Long> accountIdsByName = Map.of("готівка", 10L);
        List<TransferRow> rows = List.of(
                new TransferRow(4, "2026-03-01", "-12.345", "Продукти", "Готівка", "", null));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> validator.validate(rows, categoriesByName, accountIdsByName));

        assertTrue(ex.getMessage().startsWith("Рядок 4: "));
    }
}

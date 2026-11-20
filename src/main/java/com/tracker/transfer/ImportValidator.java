package com.tracker.transfer;

import com.tracker.domain.Category;
import com.tracker.domain.TransactionType;
import com.tracker.domain.ValidationException;
import com.tracker.service.Validation;
import com.tracker.service.dto.TransactionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ImportValidator {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    public List<TransactionRequest> validate(
            List<TransferRow> rows,
            Map<String, Category> categoriesByName,
            Map<String, Long> accountIdsByName) {
        List<TransferRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(TransferRow::rowNumber));
        List<TransactionRequest> result = new ArrayList<>(sorted.size());
        for (TransferRow row : sorted) {
            int n = row.rowNumber();
            if (row.parseError() != null && !row.parseError().isBlank()) {
                throw new ValidationException("Рядок " + n + ": " + row.parseError());
            }
            LocalDate date = parseDate(n, row.date());
            BigDecimal signed = parseAmount(n, row.amount());
            String type = signed.signum() > 0 ? TransactionType.INCOME.name() : TransactionType.EXPENSE.name();
            Category category = resolveCategory(n, row.category(), categoriesByName);
            Long accountId = resolveAccount(n, row.account(), accountIdsByName);
            String description = row.description() == null ? "" : row.description().trim();
            if (description.length() > 120) {
                throw new ValidationException("Рядок " + n + ": опис не може перевищувати 120 символів");
            }
            result.add(new TransactionRequest(date, accountId, category.id(), type, signed.abs(), description));
        }
        return result;
    }

    private static LocalDate parseDate(int rowNumber, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Рядок " + rowNumber + ": порожня дата");
        }
        String text = raw.trim();
        LocalDate parsed = tryParseDate(text, ISO_DATE);
        if (parsed == null) {
            parsed = tryParseDate(text, DOT_DATE);
        }
        if (parsed == null) {
            throw new ValidationException("Рядок " + rowNumber + ": некоректна дата");
        }
        if (parsed.getYear() < 1990 || parsed.getYear() > 2100) {
            throw new ValidationException("Рядок " + rowNumber + ": рік дати має бути від 1990 до 2100");
        }
        return parsed;
    }

    private static LocalDate tryParseDate(String text, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(text, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseAmount(int rowNumber, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Рядок " + rowNumber + ": порожня сума");
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ValidationException("Рядок " + rowNumber + ": некоректна сума");
        }
        if (parsed.signum() == 0) {
            throw new ValidationException("Рядок " + rowNumber + ": сума не може дорівнювати нулю");
        }
        BigDecimal stripped = parsed.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new ValidationException("Рядок " + rowNumber + ": сума має містити не більше 2 знаків після коми");
        }
        if (stripped.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException(
                    "Рядок " + rowNumber + ": сума перевищує максимально допустиму (999999999.99)");
        }
        return stripped.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static Category resolveCategory(
            int rowNumber, String raw, Map<String, Category> categoriesByName) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Рядок " + rowNumber + ": порожня категорія");
        }
        Category category = categoriesByName.get(Validation.normalize(raw));
        if (category == null) {
            throw new ValidationException("Рядок " + rowNumber + ": категорію не знайдено: " + raw.trim());
        }
        if (category.isSystem()) {
            throw new ValidationException(
                    "Рядок " + rowNumber + ": системну категорію не можна використовувати при імпорті");
        }
        return category;
    }

    private static Long resolveAccount(int rowNumber, String raw, Map<String, Long> accountIdsByName) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Рядок " + rowNumber + ": порожній рахунок");
        }
        Long accountId = accountIdsByName.get(Validation.normalize(raw));
        if (accountId == null) {
            throw new ValidationException("Рядок " + rowNumber + ": рахунок не знайдено: " + raw.trim());
        }
        return accountId;
    }
}

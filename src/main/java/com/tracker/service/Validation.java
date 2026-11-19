package com.tracker.service;

import com.tracker.domain.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public final class Validation {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");
    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;

    private Validation() {
    }

    public static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    public static BigDecimal money(BigDecimal value, String missingMessage) {
        if (value == null) {
            throw new ValidationException(missingMessage);
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new ValidationException("Сума має містити не більше 2 знаків після коми");
        }
        if (value.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException("Сума перевищує максимально допустиму (999999999.99)");
        }
        return value.setScale(2);
    }

    public static BigDecimal positiveMoney(BigDecimal value, String missingMessage) {
        BigDecimal result = money(value, missingMessage);
        if (result.signum() <= 0) {
            throw new ValidationException("Сума має бути більшою за нуль");
        }
        return result;
    }

    public static LocalDate date(LocalDate value) {
        if (value == null) {
            throw new ValidationException("Оберіть дату");
        }
        if (value.getYear() < MIN_YEAR || value.getYear() > MAX_YEAR) {
            throw new ValidationException("Рік дати має бути від 1990 до 2100");
        }
        return value;
    }

    public static void period(LocalDate from, LocalDate to) {
        if (from != null) {
            date(from);
        }
        if (to != null) {
            date(to);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("Початок періоду не може бути пізніше за кінець");
        }
    }
}

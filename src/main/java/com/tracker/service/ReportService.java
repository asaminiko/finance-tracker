package com.tracker.service;

import com.tracker.domain.TransactionType;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.CategoryReport;
import com.tracker.service.dto.CategoryReportRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReportService {

    private final LedgerQueries queries;

    public ReportService(LedgerQueries queries) {
        this.queries = queries;
    }

    public CategoryReport categoryReport(String kind, LocalDate from, LocalDate to) {
        TransactionType type = parseKind(kind);
        Validation.period(from, to);
        Map<String, BigDecimal> sums = queries.sumByCategory(type, from, to);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (BigDecimal amount : sums.values()) {
            total = total.add(amount);
        }
        List<CategoryReportRow> rows = new ArrayList<>();
        if (total.signum() > 0) {
            BigDecimal hundred = BigDecimal.valueOf(100);
            for (Map.Entry<String, BigDecimal> entry : sums.entrySet()) {
                BigDecimal amount = entry.getValue();
                BigDecimal percent = amount.multiply(hundred).divide(total, 1, RoundingMode.HALF_UP);
                rows.add(new CategoryReportRow(entry.getKey(), amount, percent));
            }
        }
        return new CategoryReport(type.name(), total, rows);
    }

    private static TransactionType parseKind(String raw) {
        if ("EXPENSE".equals(raw)) {
            return TransactionType.EXPENSE;
        }
        if ("INCOME".equals(raw)) {
            return TransactionType.INCOME;
        }
        throw new ValidationException("Тип звіту має бути EXPENSE або INCOME");
    }
}

package com.tracker.service;

import com.tracker.domain.Category;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.BalanceItem;
import com.tracker.service.dto.ExportContent;
import com.tracker.service.dto.ImportResponse;
import com.tracker.service.dto.TransactionRequest;
import com.tracker.service.dto.TransactionView;
import com.tracker.transfer.ExportTransfer;
import com.tracker.transfer.ImportTransfer;
import com.tracker.transfer.ImportValidator;
import com.tracker.transfer.TransactionStorage;
import com.tracker.transfer.TransferRow;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExchangeService {

    private final LedgerQueries queries;
    private final LedgerService ledger;
    private final TransactionStorage excelStorage;
    private final TransactionStorage sqliteStorage;
    private final ImportValidator validator;

    public ExchangeService(
            LedgerQueries queries,
            LedgerService ledger,
            TransactionStorage excelStorage,
            TransactionStorage sqliteStorage,
            ImportValidator validator) {
        this.queries = queries;
        this.ledger = ledger;
        this.excelStorage = excelStorage;
        this.sqliteStorage = sqliteStorage;
        this.validator = validator;
    }

    public ExportContent exportTransactions(String format, LocalDate from, LocalDate to) {
        TransactionStorage storage = resolveStorage(format);
        List<TransactionView> items = queries.listTransactions(from, to).items();
        List<TransferRow> rows = new ArrayList<>(items.size());
        for (int i = items.size() - 1; i >= 0; i--) {
            TransactionView tx = items.get(i);
            rows.add(new TransferRow(
                    rows.size() + 1,
                    tx.date().toString(),
                    tx.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    tx.category(),
                    tx.account(),
                    tx.description(),
                    null));
        }
        byte[] bytes = new ExportTransfer(storage).run(rows);
        return new ExportContent(bytes, storage.mediaType());
    }

    public ImportResponse importTransactions(String format, byte[] bytes) {
        TransactionStorage storage = resolveStorage(format);
        List<TransferRow> rows = new ImportTransfer(storage).run(bytes);
        Map<String, Category> categoriesByName = new HashMap<>();
        for (Category category : ledger.listCategories()) {
            categoriesByName.put(Validation.normalize(category.name()), category);
        }
        Map<String, Long> accountIdsByName = new HashMap<>();
        for (BalanceItem account : ledger.listBalances().items()) {
            accountIdsByName.put(Validation.normalize(account.name()), account.id());
        }
        List<TransactionRequest> requests = validator.validate(rows, categoriesByName, accountIdsByName);
        int imported = ledger.importTransactions(requests);
        return new ImportResponse(imported);
    }

    private TransactionStorage resolveStorage(String format) {
        if (format == null || format.isBlank()) {
            throw new ValidationException("Вкажіть формат (excel або sqlite)");
        }
        return switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "excel" -> excelStorage;
            case "sqlite" -> sqliteStorage;
            default -> throw new ValidationException("Непідтримуваний формат: " + format);
        };
    }
}

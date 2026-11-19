package com.tracker.service;

import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.domain.FlyweightFactory;
import com.tracker.domain.NotFoundException;
import com.tracker.domain.SourceType;
import com.tracker.domain.Transaction;
import com.tracker.domain.TransactionType;
import com.tracker.domain.ValidationException;
import com.tracker.repo.AccountRepository;
import com.tracker.repo.CategoryRepository;
import com.tracker.repo.TransactionRepository;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.BalanceItem;
import com.tracker.service.dto.BalancesResponse;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.FlyweightStats;
import com.tracker.service.dto.TransactionRequest;
import com.tracker.service.dto.TransactionView;
import com.tracker.service.dto.TransactionsResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class LedgerService {

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final FlyweightFactory flyweights;

    public LedgerService(
            AccountRepository accounts,
            CategoryRepository categories,
            TransactionRepository transactions,
            FlyweightFactory flyweights) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.flyweights = flyweights;
    }

    public Account createAccount(AccountRequest request) {
        String name = Validation.requireText(request.name(), "Назва рахунку не може бути порожньою");
        String owner = Validation.requireText(request.owner(), "Вкажіть власника рахунку");
        requireMaxLength(name, 40, "Назва рахунку не може перевищувати 40 символів");
        requireMaxLength(owner, 40, "Ім'я власника не може перевищувати 40 символів");
        BigDecimal initial = Validation.money(request.initialBalance(), "Вкажіть початковий баланс");
        String key = Validation.normalize(name);
        for (Account existing : accounts.findAll()) {
            if (Validation.normalize(existing.name()).equals(key)) {
                throw new ValidationException("Рахунок із такою назвою вже існує");
            }
        }
        return accounts.insert(name, owner, initial);
    }

    public BalancesResponse listBalances() {
        List<BalanceItem> items = accounts.balances();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (BalanceItem item : items) {
            total = total.add(item.balance());
        }
        return new BalancesResponse(items, total);
    }

    public Category createCategory(CategoryRequest request) {
        String name = Validation.requireText(request.name(), "Назва категорії не може бути порожньою");
        requireMaxLength(name, 40, "Назва категорії не може перевищувати 40 символів");
        String key = Validation.normalize(name);
        for (Category existing : categories.findAll()) {
            if (Validation.normalize(existing.name()).equals(key)) {
                throw new ValidationException("Категорія з такою назвою вже існує");
            }
        }
        return categories.insert(name);
    }

    public List<Category> listCategories() {
        return categories.findAll();
    }

    public TransactionView addTransaction(TransactionRequest request) {
        LocalDate date = Validation.date(request.date());
        if (request.accountId() == null) {
            throw new ValidationException("Оберіть рахунок");
        }
        if (request.categoryId() == null) {
            throw new ValidationException("Оберіть категорію");
        }
        TransactionType type = parseUserType(request.type());
        BigDecimal amount = Validation.positiveMoney(request.amount(), "Вкажіть суму");
        Account account = accounts.find(request.accountId())
                .orElseThrow(() -> new NotFoundException("Рахунок не знайдено"));
        Category category = categories.find(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Категорію не знайдено"));
        if (category.isSystem()) {
            throw new ValidationException("Системну категорію не можна використовувати у транзакції");
        }
        BigDecimal signed = type == TransactionType.INCOME ? amount : amount.negate();
        String description = request.description() == null ? "" : request.description().trim();
        requireMaxLength(description, 120, "Опис не може перевищувати 120 символів");
        Transaction transaction = new Transaction(
                null, date, signed, account, category, description, type, SourceType.MANUAL, null);
        long id = transactions.insert(transaction);
        return view(id, transaction);
    }

    public TransactionsResponse listTransactions(LocalDate from, LocalDate to) {
        Validation.period(from, to);
        List<TransactionView> items = transactions.list(from, to).stream()
                .map(t -> view(t.id(), t))
                .toList();
        return new TransactionsResponse(
                items, new FlyweightStats(flyweights.transactionsLoaded(), flyweights.poolSize()));
    }

    private static void requireMaxLength(String value, int max, String message) {
        if (value.length() > max) {
            throw new ValidationException(message);
        }
    }

    private static TransactionType parseUserType(String raw) {
        if ("INCOME".equals(raw)) {
            return TransactionType.INCOME;
        }
        if ("EXPENSE".equals(raw)) {
            return TransactionType.EXPENSE;
        }
        throw new ValidationException("Тип операції має бути INCOME або EXPENSE");
    }

    private static TransactionView view(long id, Transaction t) {
        return new TransactionView(
                id,
                t.date(),
                t.account().name(),
                t.category().name(),
                t.type().name(),
                t.amount(),
                t.description());
    }
}

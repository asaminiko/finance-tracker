package com.tracker.service;

import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.domain.FlyweightFactory;
import com.tracker.domain.NotFoundException;
import com.tracker.domain.PrototypeRegistry;
import com.tracker.domain.RecurringTemplate;
import com.tracker.domain.SourceType;
import com.tracker.domain.Transaction;
import com.tracker.domain.TransactionType;
import com.tracker.domain.ValidationException;
import com.tracker.repo.AccountRepository;
import com.tracker.repo.CategoryRepository;
import com.tracker.repo.ReceiptRepository;
import com.tracker.repo.ScanStore;
import com.tracker.repo.TemplateRepository;
import com.tracker.repo.TransactionRepository;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.BalanceItem;
import com.tracker.service.dto.BalancesResponse;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.FlyweightStats;
import com.tracker.service.dto.ReceiptItemRequest;
import com.tracker.service.dto.ReceiptItemView;
import com.tracker.service.dto.ReceiptRequest;
import com.tracker.service.dto.ReceiptView;
import com.tracker.service.dto.ScanContent;
import com.tracker.service.dto.TemplateRequest;
import com.tracker.service.dto.TemplateRunResponse;
import com.tracker.service.dto.TemplateView;
import com.tracker.service.dto.TransactionRequest;
import com.tracker.service.dto.TransactionView;
import com.tracker.service.dto.TransactionsResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LedgerService {

    private static final int MAX_SCAN_BYTES = 5 * 1024 * 1024;

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final FlyweightFactory flyweights;
    private final ReceiptRepository receipts;
    private final ScanStore scans;
    private final TemplateRepository templates;
    private final Clock clock;

    public LedgerService(
            AccountRepository accounts,
            CategoryRepository categories,
            TransactionRepository transactions,
            FlyweightFactory flyweights,
            ReceiptRepository receipts,
            ScanStore scans,
            TemplateRepository templates,
            Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.flyweights = flyweights;
        this.receipts = receipts;
        this.scans = scans;
        this.templates = templates;
        this.clock = clock;
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

    public ReceiptView addReceipt(ReceiptRequest request) {
        LocalDate date = Validation.date(request.date());
        String shop = Validation.requireText(request.shop(), "Вкажіть магазин");
        requireMaxLength(shop, 50, "Назва магазину не може перевищувати 50 символів");
        if (request.accountId() == null) {
            throw new ValidationException("Оберіть рахунок");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ValidationException("Додайте хоча б одну позицію чека");
        }
        Account account = accounts.find(request.accountId())
                .orElseThrow(() -> new NotFoundException("Рахунок не знайдено"));
        List<ReceiptLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        int number = 0;
        for (ReceiptItemRequest item : request.items()) {
            number++;
            if (item == null) {
                throw new ValidationException("Позиція " + number + ": відсутні дані");
            }
            String name = item.name() == null || item.name().isBlank()
                    ? null
                    : item.name().trim();
            if (name == null) {
                throw new ValidationException("Позиція " + number + ": вкажіть назву");
            }
            requireMaxLength(name, 60, "Позиція " + number + ": назва не може перевищувати 60 символів");
            BigDecimal amount = Validation.positiveMoney(item.amount(), "Позиція " + number + ": вкажіть суму");
            if (item.categoryId() == null) {
                throw new ValidationException("Позиція " + number + ": оберіть категорію");
            }
            Category category = categories.find(item.categoryId())
                    .orElseThrow(() -> new NotFoundException("Категорію не знайдено"));
            if (category.isSystem()) {
                throw new ValidationException("Позиція " + number + ": системну категорію не можна використовувати");
            }
            total = total.add(amount);
            lines.add(new ReceiptLine(name, amount, category));
        }
        Validation.money(total, "Вкажіть суму");
        String scanFile = null;
        byte[] scanBytes = null;
        boolean hasScanBase64 = request.scanBase64() != null;
        boolean hasScanExt = request.scanExt() != null;
        if (hasScanBase64 != hasScanExt) {
            throw new ValidationException("Скан і його розширення мають бути вказані разом");
        }
        if (hasScanBase64) {
            scanBytes = decodeScan(request.scanBase64());
            String ext = request.scanExt().trim().toLowerCase(Locale.ROOT);
            if (!isImage(ext, scanBytes)) {
                throw new ValidationException("Некоректний файл зображення");
            }
            scanFile = UUID.randomUUID() + "." + ext;
        }
        if (scanFile != null) {
            scans.write(scanFile, scanBytes);
        }
        try {
            long receiptId = receipts.insert(account.id(), date, shop, scanFile);
            for (ReceiptLine line : lines) {
                receipts.insertItem(receiptId, line.name(), line.amount(), line.category().id());
                transactions.insert(new Transaction(
                        null,
                        date,
                        line.amount().negate(),
                        account,
                        line.category(),
                        shop + ": " + line.name(),
                        TransactionType.EXPENSE,
                        SourceType.RECEIPT,
                        receiptId));
            }
            List<ReceiptItemView> items = lines.stream()
                    .map(l -> new ReceiptItemView(l.name(), l.category().name(), l.amount()))
                    .toList();
            return new ReceiptView(receiptId, date, shop, account.name(), total, scanFile != null, items);
        } catch (RuntimeException e) {
            if (scanFile != null) {
                scans.deleteQuietly(scanFile);
            }
            throw e;
        }
    }

    public List<ReceiptView> listReceipts() {
        return receipts.list();
    }

    public ScanContent receiptScan(long id) {
        if (!receipts.exists(id)) {
            throw new NotFoundException("Чек не знайдено");
        }
        String fileName = receipts.findScanFile(id)
                .orElseThrow(() -> new NotFoundException("Скан чека відсутній"));
        byte[] bytes = scans.read(fileName)
                .orElseThrow(() -> new NotFoundException("Файл скану не знайдено"));
        String contentType = fileName.endsWith(".png") ? "image/png" : "image/jpeg";
        return new ScanContent(bytes, contentType);
    }

    public static LocalDate dateFor(YearMonth ym, int dayOfMonth) {
        return ym.atDay(Math.min(dayOfMonth, ym.lengthOfMonth()));
    }

    public TemplateView createTemplate(TemplateRequest request) {
        String name = Validation.requireText(request.name(), "Назва шаблону не може бути порожньою");
        requireMaxLength(name, 60, "Назва шаблону не може перевищувати 60 символів");
        BigDecimal amount = Validation.nonZeroMoney(request.amount(), "Вкажіть суму");
        if (request.accountId() == null) {
            throw new ValidationException("Оберіть рахунок");
        }
        if (request.categoryId() == null) {
            throw new ValidationException("Оберіть категорію");
        }
        LocalDate nextRunDate = Validation.date(request.nextRunDate());
        LocalDate today = LocalDate.now(clock);
        if (nextRunDate.isBefore(today.minusYears(5))) {
            throw new ValidationException("Дата наступного виконання не може бути раніше ніж 5 років тому");
        }
        Account account = accounts.find(request.accountId())
                .orElseThrow(() -> new NotFoundException("Рахунок не знайдено"));
        Category category = categories.find(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Категорію не знайдено"));
        if (category.isSystem()) {
            throw new ValidationException("Системну категорію не можна використовувати у шаблоні");
        }
        String description = request.description() == null ? "" : request.description().trim();
        requireMaxLength(description, 120, "Опис не може перевищувати 120 символів");
        int dayOfMonth = nextRunDate.getDayOfMonth();
        RecurringTemplate saved = templates.insert(
                name, amount, category, account, description, dayOfMonth, nextRunDate);
        return templateView(saved);
    }

    public List<TemplateView> listTemplates() {
        return templates.findAll().stream().map(LedgerService::templateView).toList();
    }

    public TemplateRunResponse runTemplates() {
        List<RecurringTemplate> all = templates.findAll();
        PrototypeRegistry registry = new PrototypeRegistry();
        for (RecurringTemplate template : all) {
            registry.register(template.id(), template.toPrototype());
        }
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        for (RecurringTemplate template : all) {
            LocalDate next = template.nextRunDate();
            while (!next.isAfter(today)) {
                Transaction copy = registry.createFrom(template.id(), next);
                transactions.insert(copy);
                created++;
                next = dateFor(YearMonth.from(next).plusMonths(1), template.dayOfMonth());
            }
            if (!next.equals(template.nextRunDate())) {
                templates.updateNextRunDate(template.id(), next);
            }
        }
        return new TemplateRunResponse(created);
    }

    private static TemplateView templateView(RecurringTemplate t) {
        return new TemplateView(
                t.id(),
                t.name(),
                t.amount(),
                t.type().name(),
                t.category().name(),
                t.account().name(),
                t.description(),
                t.dayOfMonth(),
                t.nextRunDate());
    }

    private static byte[] decodeScan(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Некоректний файл зображення");
        }
        if (bytes.length == 0) {
            throw new ValidationException("Скан порожній");
        }
        if (bytes.length > MAX_SCAN_BYTES) {
            throw new ValidationException("Скан перевищує 5 МБ");
        }
        return bytes;
    }

    private static boolean isImage(String ext, byte[] bytes) {
        if ("jpg".equals(ext)) {
            return bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return bytes.length >= 4
                    && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
        }
        return false;
    }

    private record ReceiptLine(String name, BigDecimal amount, Category category) {
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

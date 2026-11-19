package com.tracker.seed;

import com.tracker.Config;
import com.tracker.ServerContext;
import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.service.LedgerService;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.TransactionRequest;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

public class SeedMain {

    public static void main(String[] args) {
        try {
            Config config = Config.load(Path.of("config.properties"));
            config.prepareDirectories();
            ServerContext context = new ServerContext(config, Clock.systemDefaultZone());
            context.database.init();
            boolean seeded = context.database.inTx(() -> seed(context.ledger, LocalDate.now(context.clock)));
            if (seeded) {
                System.out.println("Тестові дані створено.");
            } else {
                System.out.println("БД не порожня, видаліть папку data/");
            }
        } catch (Exception e) {
            System.err.println("Помилка сіду: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean seed(LedgerService ledger, LocalDate today) {
        if (!ledger.listBalances().items().isEmpty()) {
            return false;
        }
        Account salaryCard = ledger.createAccount(new AccountRequest("Зарплатна картка", "Анна", amount("5000.00")));
        Account cash = ledger.createAccount(new AccountRequest("Готівка", "Анна", amount("1200.00")));
        Account petroCard = ledger.createAccount(new AccountRequest("Картка Петра", "Петро", amount("8000.00")));

        Category groceries = category(ledger, "Продукти");
        Category transport = category(ledger, "Транспорт");
        Category utilities = category(ledger, "Комунальні послуги");
        Category fun = category(ledger, "Розваги");
        Category medicine = category(ledger, "Медицина");
        Category household = category(ledger, "Побутова хімія");
        Category salary = category(ledger, "Зарплата");
        Category rent = category(ledger, "Оренда");

        add(ledger, today, 55, salaryCard, salary, "INCOME", "25000.00", "Зарплата за місяць");
        add(ledger, today, 52, salaryCard, groceries, "EXPENSE", "1250.40", "Супермаркет");
        add(ledger, today, 48, cash, transport, "EXPENSE", "300.00", "Проїзд");
        add(ledger, today, 45, salaryCard, utilities, "EXPENSE", "2100.75", "Комунальні платежі");
        add(ledger, today, 30, petroCard, salary, "INCOME", "30000.00", "Зарплата Петра");
        add(ledger, today, 28, petroCard, groceries, "EXPENSE", "980.20", "Ринок");
        add(ledger, today, 21, cash, fun, "EXPENSE", "450.00", "Кіно");
        add(ledger, today, 14, petroCard, medicine, "EXPENSE", "780.00", "Аптека");
        add(ledger, today, 9, salaryCard, groceries, "EXPENSE", "1520.30", "Продукти на тиждень");
        add(ledger, today, 5, cash, household, "EXPENSE", "360.50", "Побутова хімія");
        add(ledger, today, 3, petroCard, rent, "EXPENSE", "8000.00", "Оренда квартири");
        add(ledger, today, 1, salaryCard, transport, "EXPENSE", "150.00", "Таксі");
        return true;
    }

    private static Category category(LedgerService ledger, String name) {
        return ledger.createCategory(new CategoryRequest(name));
    }

    private static void add(
            LedgerService ledger,
            LocalDate today,
            int daysAgo,
            Account account,
            Category category,
            String type,
            String amount,
            String description) {
        ledger.addTransaction(new TransactionRequest(
                today.minusDays(daysAgo), account.id(), category.id(), type, amount(amount), description));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}

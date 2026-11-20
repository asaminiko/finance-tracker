package com.tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.domain.FlyweightFactory;
import com.tracker.repo.AccountRepository;
import com.tracker.repo.CategoryRepository;
import com.tracker.repo.Database;
import com.tracker.repo.FundRepository;
import com.tracker.repo.ReceiptRepository;
import com.tracker.repo.ScanStore;
import com.tracker.repo.TemplateRepository;
import com.tracker.repo.TransactionManager;
import com.tracker.repo.TransactionRepository;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.BalancesResponse;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.ContributionRequest;
import com.tracker.service.dto.FundRequest;
import com.tracker.service.dto.FundView;
import com.tracker.service.dto.TransactionRequest;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerServiceTest {

    @Test
    void accountBalanceAndFundAccumulatedReflectIncomeExpenseAndContribution(@TempDir Path tempDir) {
        TransactionManager txManager = new TransactionManager();
        Database database = new Database(tempDir.resolve("test.db").toString(), txManager);
        database.init();
        FlyweightFactory flyweights = new FlyweightFactory();
        CategoryRepository categories = new CategoryRepository(txManager, flyweights);
        AccountRepository accounts = new AccountRepository(txManager);
        TransactionRepository transactions = new TransactionRepository(txManager, categories, flyweights);
        ReceiptRepository receipts = new ReceiptRepository(txManager);
        ScanStore scans = new ScanStore(tempDir.resolve("scans"));
        TemplateRepository templates = new TemplateRepository(txManager, categories);
        FundRepository funds = new FundRepository(txManager);
        Clock clock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC);
        LedgerService ledger = new LedgerService(
                accounts, categories, transactions, flyweights, receipts, scans, templates, funds, clock);

        database.inTx(() -> {
            Account account = ledger.createAccount(
                    new AccountRequest("Основний", "Анна", new BigDecimal("1000.00")));
            Category salary = ledger.createCategory(new CategoryRequest("Зарплата"));
            Category food = ledger.createCategory(new CategoryRequest("Продукти"));
            FundView fund = ledger.createFund(new FundRequest("Відпустка", new BigDecimal("5000.00")));
            ledger.addTransaction(new TransactionRequest(
                    LocalDate.of(2026, 3, 10),
                    account.id(),
                    salary.id(),
                    "INCOME",
                    new BigDecimal("2000.00"),
                    "Аванс"));
            ledger.addTransaction(new TransactionRequest(
                    LocalDate.of(2026, 3, 11),
                    account.id(),
                    food.id(),
                    "EXPENSE",
                    new BigDecimal("350.00"),
                    "Магазин"));
            ledger.contributeToFund(fund.id(), new ContributionRequest(account.id(), new BigDecimal("400.00")));
            return null;
        });

        BalancesResponse balances = database.inTx(ledger::listBalances);
        List<FundView> fundViews = database.inTx(ledger::listFunds);

        assertEquals(new BigDecimal("2250.00"), balances.items().get(0).balance());
        assertEquals(new BigDecimal("2250.00"), balances.total());
        assertEquals(new BigDecimal("400.00"), fundViews.get(0).accumulated());
        assertEquals(new BigDecimal("8.0"), fundViews.get(0).percent());
    }
}

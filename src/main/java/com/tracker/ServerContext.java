package com.tracker;

import com.google.gson.Gson;
import com.tracker.api.Json;
import com.tracker.domain.FlyweightFactory;
import com.tracker.repo.AccountRepository;
import com.tracker.repo.CategoryRepository;
import com.tracker.repo.Database;
import com.tracker.repo.FundRepository;
import com.tracker.repo.ProductRepository;
import com.tracker.repo.ReceiptRepository;
import com.tracker.repo.ScanStore;
import com.tracker.repo.TemplateRepository;
import com.tracker.repo.TransactionManager;
import com.tracker.repo.TransactionRepository;
import com.tracker.service.DepositService;
import com.tracker.service.LedgerService;
import java.time.Clock;

public class ServerContext {

    public final Config config;
    public final Clock clock;
    public final Gson gson;
    public final TransactionManager txManager;
    public final Database database;
    public final FlyweightFactory flyweights;
    public final LedgerService ledger;
    public final DepositService deposit;

    public ServerContext(Config config, Clock clock) {
        this.config = config;
        this.clock = clock;
        this.gson = Json.create();
        this.txManager = new TransactionManager();
        this.database = new Database(config.dbPath().toString(), txManager);
        this.flyweights = new FlyweightFactory();
        CategoryRepository categories = new CategoryRepository(txManager, flyweights);
        AccountRepository accounts = new AccountRepository(txManager);
        TransactionRepository transactions = new TransactionRepository(txManager, categories, flyweights);
        ReceiptRepository receipts = new ReceiptRepository(txManager);
        ScanStore scans = new ScanStore(config.scansDir());
        TemplateRepository templates = new TemplateRepository(txManager, categories);
        FundRepository funds = new FundRepository(txManager);
        ProductRepository products = new ProductRepository(txManager);
        this.ledger = new LedgerService(
                accounts, categories, transactions, flyweights, receipts, scans, templates, funds, clock);
        this.deposit = new DepositService(products, ledger, ledger, config.interestTaxRate(), clock);
    }
}

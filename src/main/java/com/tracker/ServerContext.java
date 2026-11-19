package com.tracker;

import com.google.gson.Gson;
import com.tracker.api.Json;
import com.tracker.domain.FlyweightFactory;
import com.tracker.repo.AccountRepository;
import com.tracker.repo.CategoryRepository;
import com.tracker.repo.Database;
import com.tracker.repo.TransactionManager;
import com.tracker.repo.TransactionRepository;
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
        this.ledger = new LedgerService(accounts, categories, transactions, flyweights);
    }
}

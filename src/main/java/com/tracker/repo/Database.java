package com.tracker.repo;

import com.tracker.domain.StorageException;
import com.tracker.domain.SystemCategories;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Supplier;
import org.sqlite.SQLiteConfig;

public class Database {

    private static final List<String> SCHEMA = List.of(
            "CREATE TABLE IF NOT EXISTS category ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL UNIQUE, "
                    + "is_system INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS account ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "owner TEXT NOT NULL, "
                    + "initial_kopecks INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS transactions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "account_id INTEGER NOT NULL REFERENCES account(id), "
                    + "category_id INTEGER NOT NULL REFERENCES category(id), "
                    + "type_code TEXT NOT NULL CHECK (type_code IN ('INCOME', 'EXPENSE', 'FUND_CONTRIBUTION')), "
                    + "amount_kopecks INTEGER NOT NULL, "
                    + "date TEXT NOT NULL, "
                    + "description TEXT NOT NULL DEFAULT '', "
                    + "source_type TEXT NOT NULL, "
                    + "source_id INTEGER, "
                    + "CHECK ((type_code = 'INCOME' AND amount_kopecks > 0) "
                    + "OR (type_code <> 'INCOME' AND amount_kopecks < 0)))",
            "CREATE TABLE IF NOT EXISTS receipt ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "account_id INTEGER NOT NULL REFERENCES account(id), "
                    + "date TEXT NOT NULL, "
                    + "shop TEXT NOT NULL, "
                    + "scan_file TEXT)",
            "CREATE TABLE IF NOT EXISTS receipt_item ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "receipt_id INTEGER NOT NULL REFERENCES receipt(id), "
                    + "name TEXT NOT NULL, "
                    + "amount_kopecks INTEGER NOT NULL, "
                    + "category_id INTEGER NOT NULL REFERENCES category(id))",
            "CREATE TABLE IF NOT EXISTS recurring_template ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "amount_kopecks INTEGER NOT NULL, "
                    + "category_id INTEGER NOT NULL REFERENCES category(id), "
                    + "account_id INTEGER NOT NULL REFERENCES account(id), "
                    + "description TEXT NOT NULL DEFAULT '', "
                    + "day_of_month INTEGER NOT NULL, "
                    + "next_run_date TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS bank_product ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "kind TEXT NOT NULL CHECK (kind IN ('DEPOSIT', 'CREDIT')), "
                    + "name TEXT NOT NULL, "
                    + "account_id INTEGER NOT NULL REFERENCES account(id), "
                    + "amount_kopecks INTEGER NOT NULL, "
                    + "rate_pct TEXT NOT NULL, "
                    + "bonus_pct TEXT NOT NULL, "
                    + "taxable INTEGER NOT NULL, "
                    + "open_date TEXT NOT NULL, "
                    + "term_months INTEGER NOT NULL, "
                    + "status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')))",
            "CREATE TABLE IF NOT EXISTS fund ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "target_kopecks INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(date)",
            "CREATE INDEX IF NOT EXISTS idx_transactions_source ON transactions(source_type, source_id)");

    private final String url;
    private final TransactionManager txManager;

    public Database(String path, TransactionManager txManager) {
        this.url = "jdbc:sqlite:" + path;
        this.txManager = txManager;
    }

    public Connection open() {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        try {
            return DriverManager.getConnection(url, config.toProperties());
        } catch (SQLException e) {
            throw new StorageException("Не вдалося відкрити базу даних: " + e.getMessage(), e);
        }
    }

    public void init() {
        try (Connection c = open()) {
            try (Statement st = c.createStatement()) {
                for (String ddl : SCHEMA) {
                    st.execute(ddl);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO category (name, is_system) VALUES (?, 1)")) {
                for (String name : SystemCategories.ALL) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося ініціалізувати базу даних: " + e.getMessage(), e);
        }
    }

    public <T> T inTx(Supplier<T> work) {
        Connection c = open();
        txManager.bind(c);
        try {
            c.setAutoCommit(false);
            T result = work.get();
            c.commit();
            return result;
        } catch (SQLException e) {
            rollbackQuietly(c);
            throw new StorageException("Помилка бази даних: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            rollbackQuietly(c);
            throw e;
        } finally {
            txManager.unbind();
            closeQuietly(c);
        }
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
        }
    }
}

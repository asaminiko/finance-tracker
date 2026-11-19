package com.tracker.api;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.tracker.domain.BusinessRuleException;
import com.tracker.domain.NotFoundException;
import com.tracker.domain.StorageException;
import com.tracker.domain.ValidationException;
import com.tracker.repo.Database;
import com.tracker.repo.TransactionManager;
import com.tracker.service.LedgerService;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.ErrorResponse;
import com.tracker.service.dto.TransactionRequest;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class Api {

    private static final long MAX_REQUEST_SIZE = 10L * 1024 * 1024;

    private final LedgerService ledger;
    private final Database db;
    private final TransactionManager txManager;
    private final Gson gson;

    public Api(LedgerService ledger, Database db, TransactionManager txManager, Gson gson) {
        this.ledger = ledger;
        this.db = db;
        this.txManager = txManager;
        this.gson = gson;
    }

    public Javalin create() {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new GsonMapper(gson));
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
        });

        app.post("/accounts", tx(ctx -> {
            AccountRequest request = parseBody(ctx, AccountRequest.class);
            ctx.status(HttpStatus.CREATED).json(ledger.createAccount(request));
        }));
        app.get("/accounts/balances", tx(ctx -> ctx.json(ledger.listBalances())));

        app.post("/categories", tx(ctx -> {
            CategoryRequest request = parseBody(ctx, CategoryRequest.class);
            ctx.status(HttpStatus.CREATED).json(ledger.createCategory(request));
        }));
        app.get("/categories", tx(ctx -> ctx.json(ledger.listCategories())));

        app.post("/transactions", tx(ctx -> {
            TransactionRequest request = parseBody(ctx, TransactionRequest.class);
            ctx.status(HttpStatus.CREATED).json(ledger.addTransaction(request));
        }));
        app.get("/transactions", tx(ctx -> {
            LocalDate from = parseDate(ctx.queryParam("from"));
            LocalDate to = parseDate(ctx.queryParam("to"));
            ctx.json(ledger.listTransactions(from, to));
        }));

        app.exception(ValidationException.class, (e, ctx) -> error(ctx, HttpStatus.BAD_REQUEST, "VALIDATION", e));
        app.exception(NotFoundException.class, (e, ctx) -> error(ctx, HttpStatus.NOT_FOUND, "NOT_FOUND", e));
        app.exception(BusinessRuleException.class,
                (e, ctx) -> error(ctx, HttpStatus.CONFLICT, "BUSINESS_RULE", e));
        app.exception(StorageException.class,
                (e, ctx) -> error(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE", e));
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(new ErrorResponse("STORAGE", "Внутрішня помилка сервера"));
        });
        return app;
    }

    Handler tx(Handler handler) {
        return ctx -> {
            Connection c = db.open();
            txManager.bind(c);
            try {
                c.setAutoCommit(false);
                handler.handle(ctx);
                c.commit();
            } catch (Exception e) {
                rollbackQuietly(c);
                throw e;
            } finally {
                txManager.unbind();
                c.close();
            }
        };
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void error(Context ctx, HttpStatus status, String code, Exception e) {
        ctx.status(status).json(new ErrorResponse(code, e.getMessage()));
    }

    private <T> T parseBody(Context ctx, Class<T> type) {
        String body = new String(ctx.bodyAsBytes(), StandardCharsets.UTF_8);
        try {
            T value = gson.fromJson(body, type);
            if (value == null) {
                throw new ValidationException("Тіло запиту порожнє");
            }
            return value;
        } catch (JsonParseException e) {
            throw new ValidationException("Некоректні дані запиту");
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Некоректна дата: " + raw);
        }
    }
}

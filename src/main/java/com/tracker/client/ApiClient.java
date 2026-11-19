package com.tracker.client;

import com.google.gson.reflect.TypeToken;
import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.BalancesResponse;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.TransactionRequest;
import com.tracker.service.dto.TransactionView;
import com.tracker.service.dto.TransactionsResponse;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {

    private static final Type CATEGORY_LIST = new TypeToken<List<Category>>() {
    }.getType();

    private final HttpGateway gateway;

    public ApiClient(HttpGateway gateway) {
        this.gateway = gateway;
    }

    public Account createAccount(AccountRequest request) {
        return gateway.post("/accounts", request, Account.class);
    }

    public BalancesResponse balances() {
        return gateway.get("/accounts/balances", BalancesResponse.class);
    }

    public Category createCategory(CategoryRequest request) {
        return gateway.post("/categories", request, Category.class);
    }

    public List<Category> categories() {
        return gateway.get("/categories", CATEGORY_LIST);
    }

    public TransactionView createTransaction(TransactionRequest request) {
        return gateway.post("/transactions", request, TransactionView.class);
    }

    public TransactionsResponse transactions(LocalDate from, LocalDate to) {
        List<String> params = new ArrayList<>();
        if (from != null) {
            params.add("from=" + from);
        }
        if (to != null) {
            params.add("to=" + to);
        }
        String query = params.isEmpty() ? "" : "?" + String.join("&", params);
        return gateway.get("/transactions" + query, TransactionsResponse.class);
    }
}

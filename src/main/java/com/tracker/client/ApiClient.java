package com.tracker.client;

import com.google.gson.reflect.TypeToken;
import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.BalancesResponse;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.ContributionRequest;
import com.tracker.service.dto.FundRequest;
import com.tracker.service.dto.FundView;
import com.tracker.service.dto.InterestRequest;
import com.tracker.service.dto.ProductRequest;
import com.tracker.service.dto.ProductView;
import com.tracker.service.dto.ReceiptRequest;
import com.tracker.service.dto.ReceiptView;
import com.tracker.service.dto.ReconciliationResponse;
import com.tracker.service.dto.TemplateRequest;
import com.tracker.service.dto.TemplateRunResponse;
import com.tracker.service.dto.TemplateView;
import com.tracker.service.dto.TransactionRequest;
import com.tracker.service.dto.TransactionView;
import com.tracker.service.dto.TransactionsResponse;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApiClient {

    private static final Type CATEGORY_LIST = new TypeToken<List<Category>>() {
    }.getType();
    private static final Type RECEIPT_LIST = new TypeToken<List<ReceiptView>>() {
    }.getType();
    private static final Type TEMPLATE_LIST = new TypeToken<List<TemplateView>>() {
    }.getType();
    private static final Type FUND_LIST = new TypeToken<List<FundView>>() {
    }.getType();
    private static final Type PRODUCT_LIST = new TypeToken<List<ProductView>>() {
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

    public ReceiptView createReceipt(ReceiptRequest request) {
        return gateway.post("/receipts", request, ReceiptView.class);
    }

    public List<ReceiptView> receipts() {
        return gateway.get("/receipts", RECEIPT_LIST);
    }

    public byte[] receiptScan(long id) {
        return gateway.getBytes("/receipts/" + id + "/scan");
    }

    public TemplateView createTemplate(TemplateRequest request) {
        return gateway.post("/templates", request, TemplateView.class);
    }

    public List<TemplateView> templates() {
        return gateway.get("/templates", TEMPLATE_LIST);
    }

    public TemplateRunResponse runTemplates() {
        return gateway.post("/templates/run", Map.of(), TemplateRunResponse.class);
    }

    public FundView createFund(FundRequest request) {
        return gateway.post("/funds", request, FundView.class);
    }

    public List<FundView> funds() {
        return gateway.get("/funds", FUND_LIST);
    }

    public TransactionView contributeToFund(long fundId, ContributionRequest request) {
        return gateway.post("/funds/" + fundId + "/contribute", request, TransactionView.class);
    }

    public ProductView createProduct(ProductRequest request) {
        return gateway.post("/products", request, ProductView.class);
    }

    public List<ProductView> products() {
        return gateway.get("/products", PRODUCT_LIST);
    }

    public TransactionView recordInterest(long productId, InterestRequest request) {
        return gateway.post("/products/" + productId + "/interest", request, TransactionView.class);
    }

    public ProductView closeProduct(long productId) {
        return gateway.post("/products/" + productId + "/close", Map.of(), ProductView.class);
    }

    public ReconciliationResponse reconciliation(long productId) {
        return gateway.get("/products/" + productId + "/reconciliation", ReconciliationResponse.class);
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

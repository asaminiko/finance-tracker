package com.tracker.service.dto;

import java.util.List;

public record TransactionsResponse(List<TransactionView> items, FlyweightStats flyweight) {
}

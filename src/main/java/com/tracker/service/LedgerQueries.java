package com.tracker.service;

import com.tracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface LedgerQueries {

    Map<String, BigDecimal> interestByMonth(long productId);

    Map<String, BigDecimal> sumByCategory(TransactionType kind, LocalDate from, LocalDate to);
}

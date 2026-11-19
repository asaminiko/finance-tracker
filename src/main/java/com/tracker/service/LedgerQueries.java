package com.tracker.service;

import java.math.BigDecimal;
import java.util.Map;

public interface LedgerQueries {

    Map<String, BigDecimal> interestByMonth(long productId);
}

package com.tracker.domain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class FlyweightFactory {

    private final ConcurrentHashMap<Long, Category> pool = new ConcurrentHashMap<>();
    private final AtomicLong transactionsLoaded = new AtomicLong();

    public Category category(long id, Supplier<Category> loader) {
        Category c = pool.get(id);
        if (c == null) {
            c = loader.get();
            pool.putIfAbsent(id, c);
            c = pool.get(id);
        }
        return c;
    }

    public void transactionLoaded() {
        transactionsLoaded.incrementAndGet();
    }

    public long transactionsLoaded() {
        return transactionsLoaded.get();
    }

    public int poolSize() {
        return pool.size();
    }
}

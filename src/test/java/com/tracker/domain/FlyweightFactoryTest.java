package com.tracker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FlyweightFactoryTest {

    @Test
    void sameCategoryRequestedTwiceGivesSameInstance() {
        FlyweightFactory factory = new FlyweightFactory();
        AtomicInteger loads = new AtomicInteger();

        Category first = factory.category(1, () -> {
            loads.incrementAndGet();
            return new Category(1, "Продукти", false);
        });
        Category second = factory.category(1, () -> {
            loads.incrementAndGet();
            return new Category(1, "Продукти", false);
        });

        assertSame(first, second);
        assertEquals(1, factory.poolSize());
        assertEquals(1, loads.get());
    }

    @Test
    void differentCategoriesGrowPoolAndCounterCountsTransactions() {
        FlyweightFactory factory = new FlyweightFactory();

        factory.category(1, () -> new Category(1, "Продукти", false));
        factory.category(2, () -> new Category(2, "Транспорт", false));
        factory.transactionLoaded();
        factory.transactionLoaded();
        factory.transactionLoaded();

        assertEquals(2, factory.poolSize());
        assertEquals(3, factory.transactionsLoaded());
    }
}

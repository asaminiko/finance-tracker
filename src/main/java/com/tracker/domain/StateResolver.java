package com.tracker.domain;

import java.time.LocalDate;

public final class StateResolver {

    private StateResolver() {
    }

    public static ProductState resolve(ProductStatus status, LocalDate endDate, LocalDate today) {
        if (status == ProductStatus.CLOSED) {
            return new ClosedState();
        }
        if (today.isAfter(endDate)) {
            return new ExpiredState();
        }
        return new ActiveState();
    }
}

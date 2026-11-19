package com.tracker.domain;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class PrototypeRegistry {

    private final Map<Long, Transaction> prototypes = new HashMap<>();

    public void register(long templateId, Transaction prototype) {
        prototypes.put(templateId, prototype);
    }

    public Transaction createFrom(long templateId, LocalDate date) {
        Transaction prototype = prototypes.get(templateId);
        if (prototype == null) {
            throw new NotFoundException("Шаблон не знайдено");
        }
        return prototype.copy(date);
    }
}

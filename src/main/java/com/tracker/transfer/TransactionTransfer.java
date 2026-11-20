package com.tracker.transfer;

public abstract class TransactionTransfer {

    protected final TransactionStorage storage;

    protected TransactionTransfer(TransactionStorage storage) {
        this.storage = storage;
    }

    public TransactionStorage storage() {
        return storage;
    }
}

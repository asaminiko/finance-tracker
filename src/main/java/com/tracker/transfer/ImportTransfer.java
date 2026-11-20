package com.tracker.transfer;

import java.util.List;

public class ImportTransfer extends TransactionTransfer {

    public ImportTransfer(TransactionStorage storage) {
        super(storage);
    }

    public List<TransferRow> run(byte[] bytes) {
        return storage.read(bytes);
    }
}

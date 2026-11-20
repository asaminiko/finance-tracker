package com.tracker.transfer;

import java.util.List;

public class ExportTransfer extends TransactionTransfer {

    public ExportTransfer(TransactionStorage storage) {
        super(storage);
    }

    public byte[] run(List<TransferRow> rows) {
        return storage.write(rows);
    }
}

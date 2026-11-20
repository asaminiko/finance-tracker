package com.tracker.transfer;

import java.util.List;

public interface TransactionStorage {

    byte[] write(List<TransferRow> rows);

    List<TransferRow> read(byte[] bytes);

    String mediaType();

    String fileExtension();
}

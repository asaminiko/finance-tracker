package com.tracker.transfer;

public record TransferRow(
        int rowNumber,
        String date,
        String amount,
        String category,
        String account,
        String description,
        String parseError) {
}

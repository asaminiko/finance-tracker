package com.tracker.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BridgeTransferTest {

    @Test
    void exportTransferDelegatesToInMemoryStorage() {
        InMemoryStorage storage = new InMemoryStorage();
        ExportTransfer exportTransfer = new ExportTransfer(storage);
        List<TransferRow> rows = List.of(
                new TransferRow(1, "2026-03-01", "-250.50", "Продукти", "Готівка", "Вечеря", null));

        byte[] bytes = exportTransfer.run(rows);

        assertEquals(rows, storage.writtenRows);
        assertArrayEquals("1|2026-03-01|-250.50|Продукти|Готівка|Вечеря".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void importTransferReadsRowsFromInMemoryStorage() {
        InMemoryStorage storage = new InMemoryStorage();
        ImportTransfer importTransfer = new ImportTransfer(storage);
        byte[] payload = "1|2026-03-02|5000.00|Зарплата|Зарплатна картка|Аванс"
                .getBytes(StandardCharsets.UTF_8);

        List<TransferRow> rows = importTransfer.run(payload);

        assertEquals(1, rows.size());
        assertEquals("5000.00", rows.get(0).amount());
        assertEquals("Зарплата", rows.get(0).category());
        assertEquals("Аванс", rows.get(0).description());
    }

    @Test
    void exportAndImportRoundTripThroughFakeStoragePreservesRows() {
        TransactionStorage storage = new InMemoryStorage();
        List<TransferRow> source = List.of(
                new TransferRow(1, "2026-03-01", "-120.00", "Транспорт", "Готівка", "Таксі", null),
                new TransferRow(2, "2026-03-02", "1500.00", "Зарплата", "Зарплатна картка", "", null));

        byte[] exported = new ExportTransfer(storage).run(source);
        List<TransferRow> imported = new ImportTransfer(storage).run(exported);

        assertEquals(source, imported);
    }

    private static final class InMemoryStorage implements TransactionStorage {

        private List<TransferRow> writtenRows = List.of();

        @Override
        public byte[] write(List<TransferRow> rows) {
            writtenRows = List.copyOf(rows);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                TransferRow row = rows.get(i);
                sb.append(row.rowNumber()).append('|')
                        .append(row.date()).append('|')
                        .append(row.amount()).append('|')
                        .append(row.category()).append('|')
                        .append(row.account()).append('|')
                        .append(row.description());
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public List<TransferRow> read(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.isEmpty()) {
                return List.of();
            }
            String[] lines = text.split("\n", -1);
            List<TransferRow> result = new ArrayList<>(lines.length);
            for (String line : lines) {
                String[] parts = line.split("\\|", -1);
                result.add(new TransferRow(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        parts[2],
                        parts[3],
                        parts[4],
                        parts[5],
                        null));
            }
            return result;
        }

        @Override
        public String mediaType() {
            return "application/x-memory";
        }

        @Override
        public String fileExtension() {
            return "mem";
        }
    }
}

package com.tracker.transfer;

import com.tracker.domain.StorageException;
import com.tracker.domain.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.sqlite.SQLiteConfig;

public class SQLiteStorage implements TransactionStorage {

    private static final byte[] SQLITE_HEADER = "SQLite format 3".getBytes(StandardCharsets.UTF_8);

    @Override
    public byte[] write(List<TransferRow> rows) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("tracker-export-", ".sqlite");
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tempFile.toAbsolutePath())) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE transactions ("
                            + "date TEXT, amount TEXT, category TEXT, account TEXT, description TEXT)");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO transactions (date, amount, category, account, description) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    for (TransferRow row : rows) {
                        ps.setString(1, row.date());
                        ps.setString(2, row.amount());
                        ps.setString(3, row.category());
                        ps.setString(4, row.account());
                        ps.setString(5, row.description() == null ? "" : row.description());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            }
            return Files.readAllBytes(tempFile);
        } catch (IOException | SQLException e) {
            throw new StorageException("Не вдалося сформувати файл SQLite: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @Override
    public List<TransferRow> read(byte[] bytes) {
        if (bytes == null
                || bytes.length < SQLITE_HEADER.length
                || !Arrays.equals(bytes, 0, SQLITE_HEADER.length, SQLITE_HEADER, 0, SQLITE_HEADER.length)) {
            throw new ValidationException("Файл не є базою SQLite");
        }
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("tracker-import-", ".sqlite");
            Files.write(tempFile, bytes);
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            try (Connection c = DriverManager.getConnection(
                    "jdbc:sqlite:" + tempFile.toAbsolutePath(), config.toProperties());
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT date, amount, category, account, description FROM transactions ORDER BY rowid")) {
                List<TransferRow> rows = new ArrayList<>();
                int rowNumber = 0;
                while (rs.next()) {
                    rowNumber++;
                    rows.add(new TransferRow(
                            rowNumber,
                            rs.getString("date"),
                            rs.getString("amount"),
                            rs.getString("category"),
                            rs.getString("account"),
                            rs.getString("description"),
                            null));
                }
                return rows;
            }
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("no such table")) {
                throw new ValidationException("У файлі SQLite відсутня таблиця transactions");
            }
            if (message.contains("no such column")) {
                throw new ValidationException(
                        "У таблиці transactions відсутні потрібні колонки (date, amount, category, account, description)");
            }
            throw new ValidationException("Некоректний файл SQLite: " + e.getMessage());
        } catch (IOException e) {
            throw new StorageException("Не вдалося прочитати тимчасовий файл SQLite: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @Override
    public String mediaType() {
        return "application/vnd.sqlite3";
    }

    @Override
    public String fileExtension() {
        return "sqlite";
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
        }
    }
}

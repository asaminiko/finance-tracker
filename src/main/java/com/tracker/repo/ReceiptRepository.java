package com.tracker.repo;

import com.tracker.domain.StorageException;
import com.tracker.service.dto.ReceiptItemView;
import com.tracker.service.dto.ReceiptView;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReceiptRepository {

    private final TransactionManager txManager;

    public ReceiptRepository(TransactionManager txManager) {
        this.txManager = txManager;
    }

    public long insert(long accountId, LocalDate date, String shop, String scanFile) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO receipt (account_id, date, shop, scan_file) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, accountId);
            ps.setString(2, date.toString());
            ps.setString(3, shop);
            ps.setString(4, scanFile);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося зберегти чек: " + e.getMessage(), e);
        }
    }

    public void insertItem(long receiptId, String name, BigDecimal amount, long categoryId) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO receipt_item (receipt_id, name, amount_kopecks, category_id) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, receiptId);
            ps.setString(2, name);
            ps.setLong(3, amount.movePointRight(2).longValueExact());
            ps.setLong(4, categoryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Не вдалося зберегти позицію чека: " + e.getMessage(), e);
        }
    }

    public List<ReceiptView> list() {
        Map<Long, ReceiptRow> rows = new LinkedHashMap<>();
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT r.id, r.date, r.shop, r.scan_file, a.name AS account_name "
                        + "FROM receipt r JOIN account a ON a.id = r.account_id "
                        + "ORDER BY r.date DESC, r.id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.put(rs.getLong("id"), new ReceiptRow(
                        LocalDate.parse(rs.getString("date")),
                        rs.getString("shop"),
                        rs.getString("account_name"),
                        rs.getString("scan_file") != null));
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати чеки: " + e.getMessage(), e);
        }
        Map<Long, List<ReceiptItemView>> items = new LinkedHashMap<>();
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT i.receipt_id, i.name, i.amount_kopecks, c.name AS category_name "
                        + "FROM receipt_item i JOIN category c ON c.id = i.category_id "
                        + "ORDER BY i.id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.computeIfAbsent(rs.getLong("receipt_id"), k -> new ArrayList<>()).add(
                        new ReceiptItemView(
                                rs.getString("name"),
                                rs.getString("category_name"),
                                BigDecimal.valueOf(rs.getLong("amount_kopecks"), 2)));
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати позиції чеків: " + e.getMessage(), e);
        }
        List<ReceiptView> result = new ArrayList<>();
        for (Map.Entry<Long, ReceiptRow> entry : rows.entrySet()) {
            List<ReceiptItemView> receiptItems = items.getOrDefault(entry.getKey(), List.of());
            BigDecimal total = BigDecimal.ZERO.setScale(2);
            for (ReceiptItemView item : receiptItems) {
                total = total.add(item.amount());
            }
            ReceiptRow row = entry.getValue();
            result.add(new ReceiptView(
                    entry.getKey(), row.date(), row.shop(), row.account(), total, row.hasScan(), receiptItems));
        }
        return result;
    }

    public boolean exists(long id) {
        try (PreparedStatement ps = txManager.current().prepareStatement("SELECT 1 FROM receipt WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати чек: " + e.getMessage(), e);
        }
    }

    public Optional<String> findScanFile(long id) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT scan_file FROM receipt WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("scan_file"));
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати чек: " + e.getMessage(), e);
        }
    }

    private record ReceiptRow(LocalDate date, String shop, String account, boolean hasScan) {
    }
}

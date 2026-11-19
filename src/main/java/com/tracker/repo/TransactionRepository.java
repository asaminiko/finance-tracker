package com.tracker.repo;

import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.domain.FlyweightFactory;
import com.tracker.domain.SourceType;
import com.tracker.domain.StorageException;
import com.tracker.domain.Transaction;
import com.tracker.domain.TransactionType;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionRepository {

    private static final String MIN_DATE = "0000-01-01";
    private static final String MAX_DATE = "9999-12-31";

    private final TransactionManager txManager;
    private final CategoryRepository categories;
    private final FlyweightFactory flyweights;

    public TransactionRepository(
            TransactionManager txManager, CategoryRepository categories, FlyweightFactory flyweights) {
        this.txManager = txManager;
        this.categories = categories;
        this.flyweights = flyweights;
    }

    public long insert(Transaction t) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO transactions (account_id, category_id, type_code, amount_kopecks, date, "
                        + "description, source_type, source_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, t.account().id());
            ps.setLong(2, t.category().id());
            ps.setString(3, t.type().name());
            ps.setLong(4, t.amount().movePointRight(2).longValueExact());
            ps.setString(5, t.date().toString());
            ps.setString(6, t.description());
            ps.setString(7, t.sourceType().name());
            if (t.sourceId() == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setLong(8, t.sourceId());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося зберегти транзакцію: " + e.getMessage(), e);
        }
    }

    public List<Transaction> list(LocalDate from, LocalDate to) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT t.id, t.date, t.amount_kopecks, t.type_code, t.description, t.source_type, "
                        + "t.source_id, t.category_id, a.id AS account_id, a.name AS account_name, "
                        + "a.owner AS account_owner, a.initial_kopecks AS account_initial "
                        + "FROM transactions t JOIN account a ON a.id = t.account_id "
                        + "WHERE t.date >= ? AND t.date <= ? "
                        + "ORDER BY t.date DESC, t.id DESC")) {
            ps.setString(1, from == null ? MIN_DATE : from.toString());
            ps.setString(2, to == null ? MAX_DATE : to.toString());
            List<Transaction> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати транзакції: " + e.getMessage(), e);
        }
    }

    private Transaction map(ResultSet rs) throws SQLException {
        Category category = categories.resolve(rs.getLong("category_id"));
        Account account = new Account(
                rs.getLong("account_id"),
                rs.getString("account_name"),
                rs.getString("account_owner"),
                BigDecimal.valueOf(rs.getLong("account_initial"), 2));
        long sourceId = rs.getLong("source_id");
        Long sourceIdValue = rs.wasNull() ? null : sourceId;
        flyweights.transactionLoaded();
        return new Transaction(
                rs.getLong("id"),
                LocalDate.parse(rs.getString("date")),
                BigDecimal.valueOf(rs.getLong("amount_kopecks"), 2),
                account,
                category,
                rs.getString("description"),
                TransactionType.valueOf(rs.getString("type_code")),
                SourceType.valueOf(rs.getString("source_type")),
                sourceIdValue);
    }
}

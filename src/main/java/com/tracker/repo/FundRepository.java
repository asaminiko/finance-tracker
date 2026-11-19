package com.tracker.repo;

import com.tracker.domain.Fund;
import com.tracker.domain.StorageException;
import com.tracker.service.dto.FundView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FundRepository {

    private final TransactionManager txManager;

    public FundRepository(TransactionManager txManager) {
        this.txManager = txManager;
    }

    public Fund insert(String name, BigDecimal target) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO fund (name, target_kopecks) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setLong(2, target.movePointRight(2).longValueExact());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Fund(keys.getLong(1), name, target);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося створити фонд: " + e.getMessage(), e);
        }
    }

    public Optional<Fund> find(long id) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT id, name, target_kopecks FROM fund WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Fund(
                        rs.getLong("id"),
                        rs.getString("name"),
                        BigDecimal.valueOf(rs.getLong("target_kopecks"), 2)));
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати фонд: " + e.getMessage(), e);
        }
    }

    public List<FundView> listWithProgress() {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT f.id, f.name, f.target_kopecks, "
                        + "COALESCE(-SUM(t.amount_kopecks), 0) AS accumulated_kopecks "
                        + "FROM fund f "
                        + "LEFT JOIN transactions t ON t.source_type = 'FUND' AND t.source_id = f.id "
                        + "GROUP BY f.id, f.name, f.target_kopecks "
                        + "ORDER BY f.id");
             ResultSet rs = ps.executeQuery()) {
            List<FundView> result = new ArrayList<>();
            while (rs.next()) {
                BigDecimal target = BigDecimal.valueOf(rs.getLong("target_kopecks"), 2);
                BigDecimal accumulated = BigDecimal.valueOf(rs.getLong("accumulated_kopecks"), 2);
                BigDecimal percent = accumulated.multiply(BigDecimal.valueOf(100))
                        .divide(target, 1, RoundingMode.HALF_UP);
                result.add(new FundView(
                        rs.getLong("id"),
                        rs.getString("name"),
                        target,
                        accumulated,
                        percent));
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати фонди: " + e.getMessage(), e);
        }
    }
}

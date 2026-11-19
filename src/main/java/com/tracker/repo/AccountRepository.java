package com.tracker.repo;

import com.tracker.domain.Account;
import com.tracker.domain.StorageException;
import com.tracker.service.dto.BalanceItem;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountRepository {

    private final TransactionManager txManager;

    public AccountRepository(TransactionManager txManager) {
        this.txManager = txManager;
    }

    public Account insert(String name, String owner, BigDecimal initialBalance) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO account (name, owner, initial_kopecks) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, owner);
            ps.setLong(3, initialBalance.movePointRight(2).longValueExact());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Account(keys.getLong(1), name, owner, initialBalance);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося створити рахунок: " + e.getMessage(), e);
        }
    }

    public List<Account> findAll() {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT id, name, owner, initial_kopecks FROM account ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<Account> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new Account(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("owner"),
                        BigDecimal.valueOf(rs.getLong("initial_kopecks"), 2)));
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати рахунки: " + e.getMessage(), e);
        }
    }

    public Optional<Account> find(long id) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT id, name, owner, initial_kopecks FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Account(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("owner"),
                        BigDecimal.valueOf(rs.getLong("initial_kopecks"), 2)));
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати рахунок: " + e.getMessage(), e);
        }
    }

    public List<BalanceItem> balances() {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT a.id, a.name, a.owner, "
                        + "a.initial_kopecks + COALESCE(SUM(t.amount_kopecks), 0) AS balance_kopecks "
                        + "FROM account a LEFT JOIN transactions t ON t.account_id = a.id "
                        + "GROUP BY a.id, a.name, a.owner, a.initial_kopecks "
                        + "ORDER BY a.id");
             ResultSet rs = ps.executeQuery()) {
            List<BalanceItem> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new BalanceItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("owner"),
                        BigDecimal.valueOf(rs.getLong("balance_kopecks"), 2)));
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося обчислити залишки: " + e.getMessage(), e);
        }
    }
}

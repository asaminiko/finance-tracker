package com.tracker.repo;

import com.tracker.domain.Account;
import com.tracker.domain.BankProduct;
import com.tracker.domain.ProductKind;
import com.tracker.domain.ProductState;
import com.tracker.domain.ProductStatus;
import com.tracker.domain.StateResolver;
import com.tracker.domain.StorageException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductRepository {

    private static final String SELECT_COLUMNS =
            "SELECT p.id, p.kind, p.name, p.amount_kopecks, p.rate_pct, p.bonus_pct, p.taxable, "
                    + "p.open_date, p.term_months, p.status, a.id AS account_id, a.name AS account_name, "
                    + "a.owner AS account_owner, a.initial_kopecks AS account_initial "
                    + "FROM bank_product p JOIN account a ON a.id = p.account_id ";

    private final TransactionManager txManager;

    public ProductRepository(TransactionManager txManager) {
        this.txManager = txManager;
    }

    public BankProduct insert(
            ProductKind kind,
            String name,
            Account account,
            BigDecimal amount,
            BigDecimal ratePct,
            BigDecimal bonusPct,
            boolean taxable,
            LocalDate openDate,
            int termMonths,
            LocalDate today) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO bank_product (kind, name, account_id, amount_kopecks, rate_pct, bonus_pct, "
                        + "taxable, open_date, term_months, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            BigDecimal scaledRate = ratePct.setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal scaledBonus = bonusPct.setScale(2, RoundingMode.UNNECESSARY);
            ps.setString(1, kind.name());
            ps.setString(2, name);
            ps.setLong(3, account.id());
            ps.setLong(4, amount.movePointRight(2).longValueExact());
            ps.setString(5, scaledRate.toPlainString());
            ps.setString(6, scaledBonus.toPlainString());
            ps.setInt(7, taxable ? 1 : 0);
            ps.setString(8, openDate.toString());
            ps.setInt(9, termMonths);
            ps.setString(10, ProductStatus.ACTIVE.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                LocalDate endDate = openDate.plusMonths(termMonths);
                ProductState state = StateResolver.resolve(ProductStatus.ACTIVE, endDate, today);
                return new BankProduct(
                        id,
                        kind,
                        name,
                        account,
                        amount,
                        scaledRate,
                        scaledBonus,
                        taxable,
                        openDate,
                        termMonths,
                        ProductStatus.ACTIVE,
                        state);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося зберегти банківський продукт: " + e.getMessage(), e);
        }
    }

    public List<BankProduct> findAll(LocalDate today) {
        try (PreparedStatement ps = txManager.current().prepareStatement(SELECT_COLUMNS + "ORDER BY p.id");
             ResultSet rs = ps.executeQuery()) {
            List<BankProduct> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs, today));
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати банківські продукти: " + e.getMessage(), e);
        }
    }

    public Optional<BankProduct> find(long id, LocalDate today) {
        try (PreparedStatement ps = txManager.current().prepareStatement(SELECT_COLUMNS + "WHERE p.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs, today)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати банківський продукт: " + e.getMessage(), e);
        }
    }

    public void updateStatus(long id, ProductStatus status) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "UPDATE bank_product SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Не вдалося оновити статус продукту: " + e.getMessage(), e);
        }
    }

    private static BankProduct map(ResultSet rs, LocalDate today) throws SQLException {
        Account account = new Account(
                rs.getLong("account_id"),
                rs.getString("account_name"),
                rs.getString("account_owner"),
                BigDecimal.valueOf(rs.getLong("account_initial"), 2));
        LocalDate openDate = LocalDate.parse(rs.getString("open_date"));
        int termMonths = rs.getInt("term_months");
        ProductStatus status = ProductStatus.valueOf(rs.getString("status"));
        ProductState state = StateResolver.resolve(status, openDate.plusMonths(termMonths), today);
        return new BankProduct(
                rs.getLong("id"),
                ProductKind.valueOf(rs.getString("kind")),
                rs.getString("name"),
                account,
                BigDecimal.valueOf(rs.getLong("amount_kopecks"), 2),
                new BigDecimal(rs.getString("rate_pct")),
                new BigDecimal(rs.getString("bonus_pct")),
                rs.getInt("taxable") != 0,
                openDate,
                termMonths,
                status,
                state);
    }
}

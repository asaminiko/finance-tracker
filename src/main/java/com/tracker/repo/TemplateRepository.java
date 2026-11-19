package com.tracker.repo;

import com.tracker.domain.Account;
import com.tracker.domain.Category;
import com.tracker.domain.RecurringTemplate;
import com.tracker.domain.StorageException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TemplateRepository {

    private final TransactionManager txManager;
    private final CategoryRepository categories;

    public TemplateRepository(TransactionManager txManager, CategoryRepository categories) {
        this.txManager = txManager;
        this.categories = categories;
    }

    public RecurringTemplate insert(
            String name,
            BigDecimal amount,
            Category category,
            Account account,
            String description,
            int dayOfMonth,
            LocalDate nextRunDate) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO recurring_template (name, amount_kopecks, category_id, account_id, "
                        + "description, day_of_month, next_run_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setLong(2, amount.movePointRight(2).longValueExact());
            ps.setLong(3, category.id());
            ps.setLong(4, account.id());
            ps.setString(5, description);
            ps.setInt(6, dayOfMonth);
            ps.setString(7, nextRunDate.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new RecurringTemplate(
                        keys.getLong(1), name, amount, category, account, description, dayOfMonth, nextRunDate);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося зберегти шаблон: " + e.getMessage(), e);
        }
    }

    public List<RecurringTemplate> findAll() {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT rt.id, rt.name, rt.amount_kopecks, rt.category_id, rt.description, "
                        + "rt.day_of_month, rt.next_run_date, "
                        + "a.id AS account_id, a.name AS account_name, "
                        + "a.owner AS account_owner, a.initial_kopecks AS account_initial "
                        + "FROM recurring_template rt "
                        + "JOIN account a ON a.id = rt.account_id "
                        + "ORDER BY rt.id");
             ResultSet rs = ps.executeQuery()) {
            List<RecurringTemplate> result = new ArrayList<>();
            while (rs.next()) {
                Category category = categories.resolve(rs.getLong("category_id"));
                Account account = new Account(
                        rs.getLong("account_id"),
                        rs.getString("account_name"),
                        rs.getString("account_owner"),
                        BigDecimal.valueOf(rs.getLong("account_initial"), 2));
                result.add(new RecurringTemplate(
                        rs.getLong("id"),
                        rs.getString("name"),
                        BigDecimal.valueOf(rs.getLong("amount_kopecks"), 2),
                        category,
                        account,
                        rs.getString("description"),
                        rs.getInt("day_of_month"),
                        LocalDate.parse(rs.getString("next_run_date"))));
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати шаблони: " + e.getMessage(), e);
        }
    }

    public void updateNextRunDate(long id, LocalDate nextRunDate) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "UPDATE recurring_template SET next_run_date = ? WHERE id = ?")) {
            ps.setString(1, nextRunDate.toString());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Не вдалося оновити дату шаблону: " + e.getMessage(), e);
        }
    }
}

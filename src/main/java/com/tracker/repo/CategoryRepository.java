package com.tracker.repo;

import com.tracker.domain.Category;
import com.tracker.domain.FlyweightFactory;
import com.tracker.domain.NotFoundException;
import com.tracker.domain.StorageException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryRepository {

    private final TransactionManager txManager;
    private final FlyweightFactory flyweights;

    public CategoryRepository(TransactionManager txManager, FlyweightFactory flyweights) {
        this.txManager = txManager;
        this.flyweights = flyweights;
    }

    public Category insert(String name) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "INSERT INTO category (name, is_system) VALUES (?, 0)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Category(keys.getLong(1), name, false);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося створити категорію: " + e.getMessage(), e);
        }
    }

    public List<Category> findAll() {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT id, name, is_system FROM category ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<Category> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати категорії: " + e.getMessage(), e);
        }
    }

    public Optional<Category> find(long id) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT id, name, is_system FROM category WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати категорію: " + e.getMessage(), e);
        }
    }

    public Category resolve(long id) {
        return flyweights.category(id, () -> find(id)
                .orElseThrow(() -> new NotFoundException("Категорію не знайдено")));
    }

    public Category findByName(String name) {
        try (PreparedStatement ps = txManager.current().prepareStatement(
                "SELECT id, name, is_system FROM category WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("Категорію не знайдено: " + name);
                }
                Category category = map(rs);
                return flyweights.category(category.id(), () -> category);
            }
        } catch (SQLException e) {
            throw new StorageException("Не вдалося прочитати категорію: " + e.getMessage(), e);
        }
    }

    private static Category map(ResultSet rs) throws SQLException {
        return new Category(rs.getLong("id"), rs.getString("name"), rs.getInt("is_system") != 0);
    }
}

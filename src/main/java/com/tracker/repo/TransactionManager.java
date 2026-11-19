package com.tracker.repo;

import com.tracker.domain.StorageException;
import java.sql.Connection;

public class TransactionManager {

    private final ThreadLocal<Connection> holder = new ThreadLocal<>();

    public void bind(Connection connection) {
        holder.set(connection);
    }

    public void unbind() {
        holder.remove();
    }

    public Connection current() {
        Connection c = holder.get();
        if (c == null) {
            throw new StorageException("Немає активної транзакції");
        }
        return c;
    }
}

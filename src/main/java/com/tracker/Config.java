package com.tracker;

import com.tracker.domain.StorageException;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {

    private final int serverPort;
    private final Path dbPath;
    private final Path scansDir;
    private final BigDecimal interestTaxRate;

    public Config(int serverPort, Path dbPath, Path scansDir, BigDecimal interestTaxRate) {
        this.serverPort = serverPort;
        this.dbPath = dbPath;
        this.scansDir = scansDir;
        this.interestTaxRate = interestTaxRate;
    }

    public static Config load(Path file) {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new StorageException("Не вдалося прочитати " + file + ": " + e.getMessage(), e);
        }
        try {
            return new Config(
                    Integer.parseInt(required(p, "server.port")),
                    Path.of(required(p, "db.path")),
                    Path.of(required(p, "scans.dir")),
                    new BigDecimal(required(p, "interest.tax.rate")));
        } catch (NumberFormatException e) {
            throw new StorageException("Некоректне числове значення у " + file, e);
        }
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new StorageException("У конфігурації не задано параметр " + key);
        }
        return v.trim();
    }

    public void prepareDirectories() {
        try {
            Path parent = dbPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createDirectories(scansDir);
        } catch (IOException e) {
            throw new StorageException("Не вдалося створити папки даних: " + e.getMessage(), e);
        }
    }

    public int serverPort() {
        return serverPort;
    }

    public Path dbPath() {
        return dbPath;
    }

    public Path scansDir() {
        return scansDir;
    }

    public BigDecimal interestTaxRate() {
        return interestTaxRate;
    }
}

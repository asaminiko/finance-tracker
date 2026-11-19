package com.tracker.repo;

import com.tracker.domain.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ScanStore {

    private final Path dir;

    public ScanStore(Path dir) {
        this.dir = dir;
    }

    public void write(String fileName, byte[] bytes) {
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), bytes);
        } catch (IOException e) {
            throw new StorageException("Не вдалося зберегти скан: " + e.getMessage(), e);
        }
    }

    public Optional<byte[]> read(String fileName) {
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new StorageException("Не вдалося прочитати скан: " + e.getMessage(), e);
        }
    }

    public void deleteQuietly(String fileName) {
        try {
            Files.deleteIfExists(dir.resolve(fileName));
        } catch (IOException ignored) {
        }
    }
}

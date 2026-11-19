package com.tracker.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

public class Async {

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "client-async");
        thread.setDaemon(true);
        return thread;
    });

    public <T> void run(Supplier<T> supplier, Consumer<T> onOk, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(supplier, executor).whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause()
                        : error;
                Platform.runLater(() -> onError.accept(cause));
            } else {
                Platform.runLater(() -> onOk.accept(result));
            }
        });
    }
}

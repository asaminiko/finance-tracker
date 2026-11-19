package com.tracker.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.tracker.domain.BusinessRuleException;
import com.tracker.domain.NotFoundException;
import com.tracker.domain.StorageException;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.ErrorResponse;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class HttpGateway {

    private static final String UNAVAILABLE = "Сервер недоступний. Запустіть сервер.";

    private final HttpClient client;
    private final Gson gson;
    private final String baseUrl;

    public HttpGateway(Gson gson, String baseUrl) {
        this.gson = gson;
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public <T> T get(String path, Type type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return gson.fromJson(send(request), type);
    }

    public <T> T post(String path, Object body, Type type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        return gson.fromJson(send(request), type);
    }

    public byte[] getBytes(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return sendBytes(request);
    }

    private String send(HttpRequest request) {
        return new String(sendBytes(request), StandardCharsets.UTF_8);
    }

    private byte[] sendBytes(HttpRequest request) {
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            throw new StorageException(UNAVAILABLE, e);
        } catch (IOException e) {
            throw new StorageException("З'єднання з сервером перервано: " + describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("Запит перервано", e);
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        throw toException(status, new String(response.body(), StandardCharsets.UTF_8));
    }

    private RuntimeException toException(int status, String body) {
        ErrorResponse error = null;
        try {
            error = gson.fromJson(body, ErrorResponse.class);
        } catch (JsonParseException ignored) {
        }
        if (error == null || error.code() == null || error.message() == null) {
            if (status == 413) {
                return new StorageException("Запит завеликий для сервера");
            }
            return new StorageException("Сервер повернув помилку (HTTP " + status + ")");
        }
        return switch (error.code()) {
            case "VALIDATION" -> new ValidationException(error.message());
            case "NOT_FOUND" -> new NotFoundException(error.message());
            case "BUSINESS_RULE" -> new BusinessRuleException(error.message());
            default -> new StorageException(error.message());
        };
    }

    private static String describe(IOException e) {
        if (e instanceof ClosedChannelException || e.getMessage() == null) {
            return "сервер закрив з'єднання";
        }
        return e.getMessage();
    }
}

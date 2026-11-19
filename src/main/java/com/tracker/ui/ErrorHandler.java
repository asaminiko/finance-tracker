package com.tracker.ui;

import com.tracker.domain.BusinessRuleException;
import com.tracker.domain.NotFoundException;
import com.tracker.domain.StorageException;
import com.tracker.domain.ValidationException;
import javafx.scene.control.Alert;

public final class ErrorHandler {

    private ErrorHandler() {
    }

    public static void show(Throwable error) {
        String title;
        if (error instanceof ValidationException) {
            title = "Помилка введення";
        } else if (error instanceof NotFoundException) {
            title = "Не знайдено";
        } else if (error instanceof BusinessRuleException) {
            title = "Операція неможлива";
        } else if (error instanceof StorageException) {
            title = "Помилка сервера";
        } else {
            title = "Несподівана помилка";
        }
        String message = error.getMessage() == null || error.getMessage().isBlank()
                ? "Невідома помилка"
                : error.getMessage();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

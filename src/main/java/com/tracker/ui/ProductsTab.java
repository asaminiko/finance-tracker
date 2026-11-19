package com.tracker.ui;

import com.tracker.client.ApiClient;
import com.tracker.client.Async;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.BalanceItem;
import com.tracker.service.dto.InterestRequest;
import com.tracker.service.dto.ProductRequest;
import com.tracker.service.dto.ProductView;
import com.tracker.service.dto.ReconciliationResponse;
import com.tracker.service.dto.ReconciliationRow;
import com.tracker.service.dto.ReconciliationSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class ProductsTab {

    private final ApiClient api;
    private final Async async;
    private final Runnable onChanged;

    private final TableView<ProductView> productTable = new TableView<>();
    private final TableView<ReconciliationRow> reconciliationTable = new TableView<>();
    private final Label summaryLabel = new Label("Оберіть вклад або кредит для перегляду звірки");
    private final Button interestButton = new Button("Ввести фактичні відсотки");
    private final Button closeButton = new Button("Закрити продукт");
    private final VBox root = new VBox(8);

    private List<BalanceItem> accounts = List.of();

    public ProductsTab(ApiClient api, Async async, Runnable onChanged) {
        this.api = api;
        this.async = async;
        this.onChanged = onChanged;
        buildView();
    }

    public Node view() {
        return root;
    }

    public void refreshAll() {
        async.run(api::balances, response -> accounts = response.items(), ErrorHandler::show);
        loadProducts(null);
    }

    private void buildView() {
        root.setPadding(new Insets(10));

        Button add = new Button("Додати вклад/кредит");
        add.setOnAction(e -> showProductDialog());
        interestButton.setDisable(true);
        interestButton.setOnAction(e -> showInterestDialog());
        closeButton.setDisable(true);
        closeButton.setOnAction(e -> closeSelectedProduct());
        Button refresh = new Button("Оновити");
        refresh.setOnAction(e -> refreshAll());
        HBox actions = new HBox(8, add, interestButton, closeButton, refresh);

        productTable.getColumns().add(textColumn("Вид", 80, p -> kindLabel(p.kind())));
        productTable.getColumns().add(textColumn("Назва", 150, ProductView::name));
        productTable.getColumns().add(textColumn("Рахунок", 130, ProductView::account));
        productTable.getColumns().add(textColumn("Сума, UAH", 105, p -> money(p.amount())));
        productTable.getColumns().add(textColumn("Ставка, %", 85, p -> money(p.ratePct())));
        productTable.getColumns().add(textColumn("Бонус, %", 80, p ->
                "DEPOSIT".equals(p.kind()) ? money(p.bonusPct()) : "—"));
        productTable.getColumns().add(textColumn("Податок", 75, p ->
                "DEPOSIT".equals(p.kind()) ? (p.taxable() ? "Так" : "Ні") : "—"));
        productTable.getColumns().add(textColumn("Відкрито", 95, p -> p.openDate().toString()));
        productTable.getColumns().add(textColumn("Строк, міс.", 80, p -> String.valueOf(p.termMonths())));
        productTable.getColumns().add(textColumn("Закінчення", 95, p -> p.endDate().toString()));
        productTable.getColumns().add(textColumn("Стан", 105, p -> stateLabel(p.state())));
        productTable.setPrefHeight(230);
        productTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            boolean hasSelection = selected != null;
            interestButton.setDisable(!hasSelection);
            closeButton.setDisable(!hasSelection);
            if (!hasSelection) {
                reconciliationTable.setItems(FXCollections.observableArrayList());
                summaryLabel.setText("Оберіть вклад або кредит для перегляду звірки");
            } else {
                loadReconciliation(selected.id());
            }
        });

        reconciliationTable.getColumns().add(textColumn("Місяць", 100, ReconciliationRow::month));
        reconciliationTable.getColumns().add(textColumn("Залишок тіла, UAH", 150, r -> money(r.openingBalance())));
        reconciliationTable.getColumns().add(textColumn("Очікувані, UAH", 130, r -> money(r.expected())));
        reconciliationTable.getColumns().add(textColumn("Фактичні, UAH", 130, r ->
                r.actual() == null ? "—" : money(r.actual())));
        reconciliationTable.getColumns().add(textColumn("Різниця, UAH", 130, r ->
                r.difference() == null ? "—" : money(r.difference())));
        TableColumn<ReconciliationRow, String> statusCol = new TableColumn<>("Статус");
        statusCol.setPrefWidth(130);
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                switch (item) {
                    case "OK" -> {
                        setText("OK");
                        setStyle("-fx-text-fill: #1b7f2a; -fx-font-weight: bold;");
                    }
                    case "MISMATCH" -> {
                        setText("Розбіжність");
                        setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    }
                    default -> {
                        setText("—");
                        setStyle("");
                    }
                }
            }
        });
        reconciliationTable.getColumns().add(statusCol);
        VBox.setVgrow(reconciliationTable, Priority.ALWAYS);

        root.getChildren().addAll(
                actions,
                new Label("Вклади та кредити"),
                productTable,
                new Label("Таблиця звірки відсотків"),
                reconciliationTable,
                summaryLabel);
    }

    private void loadProducts(Long preferredId) {
        ProductView current = productTable.getSelectionModel().getSelectedItem();
        Long targetId = preferredId != null ? preferredId : (current != null ? current.id() : null);
        async.run(api::products, list -> {
            productTable.setItems(FXCollections.observableArrayList(list));
            if (targetId != null) {
                for (ProductView item : list) {
                    if (item.id() == targetId) {
                        productTable.getSelectionModel().select(item);
                        return;
                    }
                }
            }
            productTable.getSelectionModel().clearSelection();
            reconciliationTable.setItems(FXCollections.observableArrayList());
            summaryLabel.setText("Оберіть вклад або кредит для перегляду звірки");
        }, ErrorHandler::show);
    }

    private void loadReconciliation(long productId) {
        async.run(() -> api.reconciliation(productId), this::showReconciliation, ErrorHandler::show);
    }

    private void showReconciliation(ReconciliationResponse response) {
        reconciliationTable.setItems(FXCollections.observableArrayList(response.rows()));
        ReconciliationSummary summary = response.summary();
        if ("NONE".equals(summary.verdict())) {
            summaryLabel.setText("Підсумок звірки: немає порівняних місяців");
            return;
        }
        summaryLabel.setText("Підсумок звірки: очікувано "
                + money(summary.expected())
                + " UAH, фактично "
                + money(summary.actual())
                + " UAH, різниця "
                + money(summary.difference())
                + " UAH ("
                + verdictLabel(summary.verdict())
                + ")");
    }

    private void showProductDialog() {
        if (accounts.isEmpty()) {
            ErrorHandler.show(new ValidationException("Спершу створіть рахунок"));
            return;
        }
        ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList("Вклад", "Кредит"));
        kind.getSelectionModel().select("Вклад");
        TextField name = limited(60);
        ComboBox<BalanceItem> account = new ComboBox<>(FXCollections.observableArrayList(accounts));
        account.setConverter(converter(BalanceItem::name));
        TextField amount = new TextField();
        TextField rate = new TextField();
        TextField bonus = new TextField("0.00");
        CheckBox taxable = new CheckBox("Утримувати податок з відсотків");
        DatePicker openDate = new DatePicker();
        openDate.setEditable(false);
        TextField termMonths = new TextField();

        kind.valueProperty().addListener((obs, old, selectedKind) -> {
            boolean isCredit = "Кредит".equals(selectedKind);
            bonus.setDisable(isCredit);
            taxable.setDisable(isCredit);
            if (isCredit) {
                bonus.setText("0.00");
                taxable.setSelected(false);
            }
        });

        GridPane grid = grid();
        grid.addRow(0, new Label("Вид:"), kind);
        grid.addRow(1, new Label("Назва:"), name);
        grid.addRow(2, new Label("Рахунок:"), account);
        grid.addRow(3, new Label("Сума, UAH:"), amount);
        grid.addRow(4, new Label("Річна ставка, %:"), rate);
        grid.addRow(5, new Label("Бонус, %:"), bonus);
        grid.addRow(6, new Label("Податок:"), taxable);
        grid.addRow(7, new Label("Дата відкриття:"), openDate);
        grid.addRow(8, new Label("Строк, міс.:"), termMonths);

        confirm("Новий вклад / кредит", grid, () -> {
            if (account.getValue() == null) {
                throw new ValidationException("Оберіть рахунок");
            }
            if (openDate.getValue() == null) {
                throw new ValidationException("Оберіть дату");
            }
            boolean isDeposit = "Вклад".equals(kind.getValue());
            BigDecimal bonusValue = isDeposit ? parseOptionalPercent(bonus.getText()) : BigDecimal.ZERO;
            ProductRequest request = new ProductRequest(
                    isDeposit ? "DEPOSIT" : "CREDIT",
                    name.getText(),
                    account.getValue().id(),
                    parseDecimal(amount.getText(), "Вкажіть суму", "Некоректна сума"),
                    parseDecimal(rate.getText(), "Вкажіть річну ставку", "Некоректна ставка"),
                    bonusValue,
                    isDeposit && taxable.isSelected(),
                    openDate.getValue(),
                    parseTerm(termMonths.getText()));
            async.run(() -> api.createProduct(request), created -> loadProducts(created.id()), ErrorHandler::show);
        });
    }

    private void showInterestDialog() {
        ProductView selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        DatePicker date = new DatePicker();
        date.setEditable(false);
        TextField amount = new TextField();
        GridPane grid = grid();
        grid.addRow(0, new Label("Дата:"), date);
        grid.addRow(1, new Label("Фактичні відсотки, UAH:"), amount);

        confirm("Введення фактичних відсотків: " + selected.name(), grid, () -> {
            if (date.getValue() == null) {
                throw new ValidationException("Оберіть дату");
            }
            InterestRequest request = new InterestRequest(
                    date.getValue(),
                    parseDecimal(amount.getText(), "Вкажіть суму", "Некоректна сума"));
            async.run(() -> api.recordInterest(selected.id(), request), tx -> {
                loadProducts(selected.id());
                onChanged.run();
            }, ErrorHandler::show);
        });
    }

    private void closeSelectedProduct() {
        ProductView selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        async.run(() -> api.closeProduct(selected.id()), closed -> loadProducts(closed.id()), ErrorHandler::show);
    }

    private void confirm(String title, Node content, Runnable onSave) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        ButtonType save = new ButtonType("Зберегти", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Скасувати", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        dialog.getDialogPane().setContent(content);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == save) {
            try {
                onSave.run();
            } catch (ValidationException e) {
                ErrorHandler.show(e);
            }
        }
    }

    private static <T> TableColumn<T, String> textColumn(String title, double width, Function<T, String> getter) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        return column;
    }

    private static TextField limited(int max) {
        TextField field = new TextField();
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() > max ? null : change));
        return field;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        return grid;
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static BigDecimal parseDecimal(String text, String missingMessage, String invalidMessage) {
        if (text == null || text.isBlank()) {
            throw new ValidationException(missingMessage);
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ValidationException(invalidMessage);
        }
    }

    private static BigDecimal parseOptionalPercent(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ValidationException("Некоректний бонус");
        }
    }

    private static int parseTerm(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Вкажіть строк у місяцях");
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Некоректний строк у місяцях");
        }
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String kindLabel(String kind) {
        return "DEPOSIT".equals(kind) ? "Вклад" : "Кредит";
    }

    private static String stateLabel(String state) {
        return switch (state) {
            case "ACTIVE" -> "Активний";
            case "EXPIRED" -> "Строк минув";
            case "CLOSED" -> "Закритий";
            default -> state;
        };
    }

    private static String verdictLabel(String verdict) {
        return switch (verdict) {
            case "EQUAL" -> "збігається";
            case "ACTUAL_LESS" -> "факт менший за очікуваний";
            case "ACTUAL_MORE" -> "факт більший за очікуваний";
            default -> "немає порівняних місяців";
        };
    }
}

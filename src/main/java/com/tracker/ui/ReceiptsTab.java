package com.tracker.ui;

import com.tracker.client.ApiClient;
import com.tracker.client.Async;
import com.tracker.domain.Category;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.BalanceItem;
import com.tracker.service.dto.ReceiptItemRequest;
import com.tracker.service.dto.ReceiptItemView;
import com.tracker.service.dto.ReceiptRequest;
import com.tracker.service.dto.ReceiptView;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

public class ReceiptsTab {

    private static final long MAX_SCAN_BYTES = 5L * 1024 * 1024;

    private final ApiClient api;
    private final Async async;
    private final Runnable onChanged;

    private final TableView<ReceiptView> receiptTable = new TableView<>();
    private final TableView<ReceiptItemView> itemTable = new TableView<>();
    private final Button scanButton = new Button("Переглянути скан");
    private final VBox root = new VBox(8);

    private List<BalanceItem> accounts = List.of();
    private List<Category> categories = List.of();

    public ReceiptsTab(ApiClient api, Async async, Runnable onChanged) {
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
        async.run(api::categories, list -> categories = list, ErrorHandler::show);
        loadReceipts();
    }

    private void buildView() {
        root.setPadding(new Insets(10));

        Button add = new Button("Додати чек");
        add.setOnAction(e -> showReceiptDialog());
        Button refresh = new Button("Оновити");
        refresh.setOnAction(e -> refreshAll());
        scanButton.setDisable(true);
        scanButton.setOnAction(e -> showScan());
        HBox actions = new HBox(8, add, scanButton, refresh);

        receiptTable.getColumns().add(column(receiptTable, "Дата", 100, r -> r.date().toString()));
        receiptTable.getColumns().add(column(receiptTable, "Магазин", 180, ReceiptView::shop));
        receiptTable.getColumns().add(column(receiptTable, "Рахунок", 170, ReceiptView::account));
        receiptTable.getColumns().add(column(receiptTable, "Сума, UAH", 110, r -> money(r.total())));
        receiptTable.getColumns().add(column(receiptTable, "Скан", 70, r -> r.hasScan() ? "Так" : "Ні"));
        receiptTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            scanButton.setDisable(selected == null || !selected.hasScan());
            itemTable.setItems(FXCollections.observableArrayList(
                    selected == null ? List.<ReceiptItemView>of() : selected.items()));
        });

        itemTable.getColumns().add(column(itemTable, "Позиція", 220, ReceiptItemView::name));
        itemTable.getColumns().add(column(itemTable, "Категорія", 160, ReceiptItemView::category));
        itemTable.getColumns().add(column(itemTable, "Сума, UAH", 110, i -> money(i.amount())));

        VBox left = new VBox(6, new Label("Чеки"), receiptTable);
        VBox right = new VBox(6, new Label("Позиції вибраного чека"), itemTable);
        VBox.setVgrow(receiptTable, Priority.ALWAYS);
        VBox.setVgrow(itemTable, Priority.ALWAYS);
        HBox lists = new HBox(10, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(lists, Priority.ALWAYS);

        root.getChildren().addAll(actions, lists);
    }

    private static <T> TableColumn<T, String> column(
            TableView<T> table, String title, double width, Function<T, String> getter) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        return column;
    }

    private void loadReceipts() {
        async.run(api::receipts, list -> {
            receiptTable.setItems(FXCollections.observableArrayList(list));
            receiptTable.getSelectionModel().clearSelection();
            itemTable.setItems(FXCollections.observableArrayList());
        }, ErrorHandler::show);
    }

    private void showScan() {
        ReceiptView selected = receiptTable.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.hasScan()) {
            return;
        }
        async.run(() -> api.receiptScan(selected.id()), bytes -> {
            Image image = new Image(new ByteArrayInputStream(bytes), 700, 600, true, true);
            if (image.isError()) {
                ErrorHandler.show(new ValidationException("Некоректний файл зображення"));
                return;
            }
            ScrollPane scroll = new ScrollPane(new ImageView(image));
            scroll.setPrefSize(720, 620);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Скан чека: " + selected.shop());
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setContent(scroll);
            dialog.setResizable(true);
            dialog.show();
        }, ErrorHandler::show);
    }

    private void showReceiptDialog() {
        if (accounts.isEmpty()) {
            ErrorHandler.show(new ValidationException("Спершу створіть рахунок"));
            return;
        }
        List<Category> userCategories = categories.stream().filter(c -> !c.isSystem()).toList();
        if (userCategories.isEmpty()) {
            ErrorHandler.show(new ValidationException("Спершу створіть категорію"));
            return;
        }
        DatePicker date = new DatePicker();
        date.setEditable(false);
        TextField shop = limited(50);
        ComboBox<BalanceItem> account = new ComboBox<>(FXCollections.observableArrayList(accounts));
        account.setConverter(converter(BalanceItem::name));

        String[] scanExt = new String[1];
        String[] scanBase64 = new String[1];
        Label scanLabel = new Label("Скан не обрано");
        Button chooseScan = new Button("Обрати скан");
        chooseScan.setOnAction(e -> chooseScan(scanLabel, scanExt, scanBase64));

        List<ItemRow> itemRows = new ArrayList<>();
        VBox itemsBox = new VBox(6);
        Runnable addRow = () -> {
            ItemRow row = new ItemRow(userCategories);
            itemRows.add(row);
            itemsBox.getChildren().add(row.node());
        };
        addRow.run();
        Button addItem = new Button("Додати позицію");
        addItem.setOnAction(e -> addRow.run());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Дата:"), date);
        grid.addRow(1, new Label("Магазин:"), shop);
        grid.addRow(2, new Label("Рахунок:"), account);
        grid.addRow(3, chooseScan, scanLabel);
        grid.addRow(4, new Label("Позиції:"), itemsBox);
        grid.add(addItem, 1, 5);

        ScrollPane content = new ScrollPane(grid);
        content.setFitToWidth(true);
        content.setPrefSize(640, 460);

        confirm("Новий чек", content, () -> {
            if (date.getValue() == null) {
                throw new ValidationException("Оберіть дату");
            }
            if (account.getValue() == null) {
                throw new ValidationException("Оберіть рахунок");
            }
            List<ReceiptItemRequest> items = new ArrayList<>();
            for (ItemRow row : itemRows) {
                items.add(row.toRequest());
            }
            ReceiptRequest request = new ReceiptRequest(
                    date.getValue(), shop.getText(), account.getValue().id(), items, scanBase64[0], scanExt[0]);
            async.run(() -> api.createReceipt(request), created -> {
                loadReceipts();
                onChanged.run();
            }, ErrorHandler::show);
        });
    }

    private void chooseScan(Label label, String[] ext, String[] base64) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Обрати скан чека");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Зображення (JPG, PNG)", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        String chosenExt;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            chosenExt = "jpg";
        } else if (name.endsWith(".png")) {
            chosenExt = "png";
        } else {
            ErrorHandler.show(new ValidationException("Скан має бути у форматі JPG або PNG"));
            return;
        }
        try {
            if (Files.size(file.toPath()) > MAX_SCAN_BYTES) {
                ErrorHandler.show(new ValidationException("Скан перевищує 5 МБ"));
                return;
            }
            base64[0] = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            ErrorHandler.show(new ValidationException("Не вдалося прочитати файл: " + e.getMessage()));
            return;
        }
        ext[0] = chosenExt;
        label.setText(file.getName());
    }

    private void confirm(String title, Node content, Runnable onSave) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        ButtonType save = new ButtonType("Зберегти", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Скасувати", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        dialog.getDialogPane().setContent(content);
        dialog.setResizable(true);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == save) {
            try {
                onSave.run();
            } catch (ValidationException e) {
                ErrorHandler.show(e);
            }
        }
    }

    private static TextField limited(int max) {
        TextField field = new TextField();
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() > max ? null : change));
        return field;
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

    private static String money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private static final class ItemRow {

        private final TextField name = limited(60);
        private final TextField amount = new TextField();
        private final ComboBox<Category> category;
        private final HBox node;

        ItemRow(List<Category> userCategories) {
            name.setPromptText("Назва");
            amount.setPromptText("Сума");
            amount.setPrefWidth(100);
            category = new ComboBox<>(FXCollections.observableArrayList(userCategories));
            category.setConverter(converter(Category::name));
            category.setPromptText("Категорія");
            node = new HBox(6, name, amount, category);
        }

        Node node() {
            return node;
        }

        ReceiptItemRequest toRequest() {
            String text = amount.getText();
            if (text == null || text.isBlank()) {
                throw new ValidationException("Вкажіть суму позиції");
            }
            BigDecimal value;
            try {
                value = new BigDecimal(text.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new ValidationException("Некоректна сума позиції");
            }
            Category selected = category.getValue();
            return new ReceiptItemRequest(name.getText(), value, selected == null ? null : selected.id());
        }
    }
}

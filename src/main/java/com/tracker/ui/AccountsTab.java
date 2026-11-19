package com.tracker.ui;

import com.tracker.client.ApiClient;
import com.tracker.client.Async;
import com.tracker.domain.Category;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.AccountRequest;
import com.tracker.service.dto.BalanceItem;
import com.tracker.service.dto.BalancesResponse;
import com.tracker.service.dto.CategoryRequest;
import com.tracker.service.dto.TemplateRequest;
import com.tracker.service.dto.TemplateView;
import com.tracker.service.dto.TransactionRequest;
import com.tracker.service.dto.TransactionView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class AccountsTab {

    private record BalanceRow(String label, String amount) {
    }

    private final ApiClient api;
    private final Async async;
    private final Label status;

    private final TreeTableView<BalanceRow> balanceTree = new TreeTableView<>();
    private final Label totalLabel = new Label("Загальний підсумок: -");
    private final TableView<TemplateView> templateTable = new TableView<>();
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final TableView<TransactionView> transactionTable = new TableView<>();
    private final VBox root = new VBox(8);

    private List<BalanceItem> accounts = List.of();
    private List<Category> categories = List.of();

    public AccountsTab(ApiClient api, Async async, Label status) {
        this.api = api;
        this.async = async;
        this.status = status;
        buildView();
    }

    public Node view() {
        return root;
    }

    public void refreshAll() {
        loadBalances();
        loadCategories();
        loadTemplates();
        loadTransactions();
    }

    private void buildView() {
        root.setPadding(new Insets(10));

        Button addAccount = new Button("Додати рахунок");
        addAccount.setOnAction(e -> showAccountDialog());
        Button addCategory = new Button("Додати категорію");
        addCategory.setOnAction(e -> showCategoryDialog());
        Button addTransaction = new Button("Додати транзакцію");
        addTransaction.setOnAction(e -> showTransactionDialog());
        Button addTemplate = new Button("Додати шаблон");
        addTemplate.setOnAction(e -> showTemplateDialog());
        Button runTemplates = new Button("Виконати зараз");
        runTemplates.setOnAction(e -> runTemplatesNow());
        Button refresh = new Button("Оновити");
        refresh.setOnAction(e -> refreshAll());
        HBox actions = new HBox(8, addAccount, addCategory, addTransaction, addTemplate, runTemplates, refresh);

        TreeTableColumn<BalanceRow, String> nameColumn = new TreeTableColumn<>("Власник / рахунок");
        nameColumn.setPrefWidth(210);
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue().label()));
        TreeTableColumn<BalanceRow, String> amountColumn = new TreeTableColumn<>("Залишок, UAH");
        amountColumn.setPrefWidth(120);
        amountColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue().amount()));
        balanceTree.getColumns().add(nameColumn);
        balanceTree.getColumns().add(amountColumn);
        balanceTree.setShowRoot(false);
        balanceTree.setPrefHeight(210);

        templateTable.getColumns().add(textColumn("Назва", 110, TemplateView::name));
        templateTable.getColumns().add(textColumn("Рахунок", 110, TemplateView::account));
        templateTable.getColumns().add(textColumn("Категорія", 110, TemplateView::category));
        templateTable.getColumns().add(textColumn("Сума, UAH", 95, t -> money(t.amount())));
        templateTable.getColumns().add(textColumn("День", 50, t -> String.valueOf(t.dayOfMonth())));
        templateTable.getColumns().add(textColumn("Наступна дата", 100, t -> t.nextRunDate().toString()));
        templateTable.getColumns().add(textColumn("Опис", 120, TemplateView::description));
        templateTable.setPrefHeight(210);

        VBox balancesBox = new VBox(6, new Label("Залишки по рахунках"), balanceTree, totalLabel);
        balancesBox.setPrefWidth(340);
        VBox.setVgrow(balanceTree, Priority.ALWAYS);
        VBox templatesBox = new VBox(6, new Label("Періодичні шаблони"), templateTable);
        VBox.setVgrow(templateTable, Priority.ALWAYS);
        HBox.setHgrow(templatesBox, Priority.ALWAYS);
        HBox topPane = new HBox(10, balancesBox, templatesBox);

        fromPicker.setPromptText("Від");
        fromPicker.setEditable(false);
        toPicker.setPromptText("До");
        toPicker.setEditable(false);
        Button apply = new Button("Застосувати");
        apply.setOnAction(e -> loadTransactions());
        Button reset = new Button("Скинути");
        reset.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            loadTransactions();
        });
        HBox filter = new HBox(8, new Label("Період:"), fromPicker, toPicker, apply, reset);
        filter.setAlignment(Pos.CENTER_LEFT);

        transactionTable.getColumns().add(textColumn("Дата", 110, t -> t.date().toString()));
        transactionTable.getColumns().add(textColumn("Рахунок", 170, TransactionView::account));
        transactionTable.getColumns().add(textColumn("Категорія", 170, TransactionView::category));
        transactionTable.getColumns().add(textColumn("Тип", 120, t -> typeLabel(t.type())));
        transactionTable.getColumns().add(textColumn("Сума, UAH", 120, t -> money(t.amount())));
        transactionTable.getColumns().add(textColumn("Опис", 260, TransactionView::description));
        VBox.setVgrow(transactionTable, Priority.ALWAYS);

        root.getChildren().addAll(actions, topPane, filter, transactionTable);
    }

    private <T> TableColumn<T, String> textColumn(String title, double width, Function<T, String> getter) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        return column;
    }

    private void loadBalances() {
        async.run(api::balances, this::showBalances, ErrorHandler::show);
    }

    private void loadCategories() {
        async.run(api::categories, list -> categories = list, ErrorHandler::show);
    }

    private void loadTemplates() {
        async.run(api::templates, list -> templateTable.setItems(FXCollections.observableArrayList(list)),
                ErrorHandler::show);
    }

    private void loadTransactions() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        async.run(() -> api.transactions(from, to), response -> {
            transactionTable.setItems(FXCollections.observableArrayList(response.items()));
            status.setText(response.flyweight().transactions() + " транзакцій / "
                    + response.flyweight().pool() + " об'єктів");
        }, ErrorHandler::show);
    }

    private void showBalances(BalancesResponse response) {
        accounts = response.items();
        Map<String, List<BalanceItem>> byOwner = new LinkedHashMap<>();
        for (BalanceItem item : accounts) {
            byOwner.computeIfAbsent(item.owner(), k -> new ArrayList<>()).add(item);
        }
        TreeItem<BalanceRow> hiddenRoot = new TreeItem<>(new BalanceRow("", ""));
        hiddenRoot.setExpanded(true);
        for (Map.Entry<String, List<BalanceItem>> entry : byOwner.entrySet()) {
            BigDecimal ownerTotal = BigDecimal.ZERO;
            for (BalanceItem item : entry.getValue()) {
                ownerTotal = ownerTotal.add(item.balance());
            }
            TreeItem<BalanceRow> ownerNode = new TreeItem<>(new BalanceRow(entry.getKey(), money(ownerTotal)));
            ownerNode.setExpanded(true);
            for (BalanceItem item : entry.getValue()) {
                ownerNode.getChildren().add(new TreeItem<>(new BalanceRow(item.name(), money(item.balance()))));
            }
            hiddenRoot.getChildren().add(ownerNode);
        }
        balanceTree.setRoot(hiddenRoot);
        totalLabel.setText("Загальний підсумок: " + money(response.total()) + " UAH");
    }

    private void showAccountDialog() {
        TextField name = limited(40);
        TextField owner = limited(40);
        TextField initial = new TextField("0.00");
        GridPane grid = grid();
        grid.addRow(0, new Label("Назва:"), name);
        grid.addRow(1, new Label("Власник:"), owner);
        grid.addRow(2, new Label("Початковий баланс:"), initial);
        confirm("Новий рахунок", grid, () -> {
            AccountRequest request = new AccountRequest(name.getText(), owner.getText(), parseAmount(initial.getText()));
            async.run(() -> api.createAccount(request), created -> loadBalances(), ErrorHandler::show);
        });
    }

    private void showCategoryDialog() {
        TextField name = limited(40);
        GridPane grid = grid();
        grid.addRow(0, new Label("Назва:"), name);
        confirm("Нова категорія", grid, () -> {
            CategoryRequest request = new CategoryRequest(name.getText());
            async.run(() -> api.createCategory(request), created -> loadCategories(), ErrorHandler::show);
        });
    }

    private void showTransactionDialog() {
        List<Category> userCategories = requireAccountsAndCategories();
        if (userCategories.isEmpty()) {
            return;
        }
        DatePicker date = new DatePicker();
        date.setEditable(false);
        ComboBox<BalanceItem> account = new ComboBox<>(FXCollections.observableArrayList(accounts));
        account.setConverter(converter(BalanceItem::name));
        ComboBox<Category> category = new ComboBox<>(FXCollections.observableArrayList(userCategories));
        category.setConverter(converter(Category::name));
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("Дохід", "Витрата"));
        type.getSelectionModel().select("Витрата");
        TextField amount = new TextField();
        TextField description = limited(120);
        GridPane grid = grid();
        grid.addRow(0, new Label("Дата:"), date);
        grid.addRow(1, new Label("Рахунок:"), account);
        grid.addRow(2, new Label("Категорія:"), category);
        grid.addRow(3, new Label("Тип:"), type);
        grid.addRow(4, new Label("Сума:"), amount);
        grid.addRow(5, new Label("Опис:"), description);
        confirm("Нова транзакція", grid, () -> {
            if (date.getValue() == null) {
                throw new ValidationException("Оберіть дату");
            }
            if (account.getValue() == null) {
                throw new ValidationException("Оберіть рахунок");
            }
            if (category.getValue() == null) {
                throw new ValidationException("Оберіть категорію");
            }
            TransactionRequest request = new TransactionRequest(
                    date.getValue(),
                    account.getValue().id(),
                    category.getValue().id(),
                    "Дохід".equals(type.getValue()) ? "INCOME" : "EXPENSE",
                    parseAmount(amount.getText()),
                    description.getText());
            async.run(() -> api.createTransaction(request), created -> {
                loadBalances();
                loadTransactions();
            }, ErrorHandler::show);
        });
    }

    private void showTemplateDialog() {
        List<Category> userCategories = requireAccountsAndCategories();
        if (userCategories.isEmpty()) {
            return;
        }
        TextField name = limited(60);
        ComboBox<BalanceItem> account = new ComboBox<>(FXCollections.observableArrayList(accounts));
        account.setConverter(converter(BalanceItem::name));
        ComboBox<Category> category = new ComboBox<>(FXCollections.observableArrayList(userCategories));
        category.setConverter(converter(Category::name));
        TextField amount = new TextField();
        DatePicker nextRunDate = new DatePicker();
        nextRunDate.setEditable(false);
        TextField description = limited(120);
        GridPane grid = grid();
        grid.addRow(0, new Label("Назва:"), name);
        grid.addRow(1, new Label("Рахунок:"), account);
        grid.addRow(2, new Label("Категорія:"), category);
        grid.addRow(3, new Label("Сума (+ дохід / - витрата):"), amount);
        grid.addRow(4, new Label("Наступна дата:"), nextRunDate);
        grid.addRow(5, new Label("Опис:"), description);
        confirm("Новий шаблон", grid, () -> {
            if (account.getValue() == null) {
                throw new ValidationException("Оберіть рахунок");
            }
            if (category.getValue() == null) {
                throw new ValidationException("Оберіть категорію");
            }
            if (nextRunDate.getValue() == null) {
                throw new ValidationException("Оберіть дату");
            }
            TemplateRequest request = new TemplateRequest(
                    name.getText(),
                    parseAmount(amount.getText()),
                    category.getValue().id(),
                    account.getValue().id(),
                    description.getText(),
                    nextRunDate.getValue());
            async.run(() -> api.createTemplate(request), created -> loadTemplates(), ErrorHandler::show);
        });
    }

    private void runTemplatesNow() {
        async.run(api::runTemplates, response -> {
            loadTemplates();
            loadBalances();
            loadTransactions();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Періодичні операції");
            alert.setHeaderText(null);
            alert.setContentText("Створено транзакцій: " + response.created());
            alert.showAndWait();
        }, ErrorHandler::show);
    }

    private List<Category> requireAccountsAndCategories() {
        if (accounts.isEmpty()) {
            ErrorHandler.show(new ValidationException("Спершу створіть рахунок"));
            return List.of();
        }
        List<Category> userCategories = categories.stream().filter(c -> !c.isSystem()).toList();
        if (userCategories.isEmpty()) {
            ErrorHandler.show(new ValidationException("Спершу створіть категорію"));
            return List.of();
        }
        return userCategories;
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

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
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

    private static BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Вкажіть суму");
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ValidationException("Некоректна сума");
        }
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "INCOME" -> "Дохід";
            case "EXPENSE" -> "Витрата";
            case "FUND_CONTRIBUTION" -> "Внесок у фонд";
            default -> type;
        };
    }
}

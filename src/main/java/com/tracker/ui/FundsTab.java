package com.tracker.ui;

import com.tracker.client.ApiClient;
import com.tracker.client.Async;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.BalanceItem;
import com.tracker.service.dto.ContributionRequest;
import com.tracker.service.dto.FundRequest;
import com.tracker.service.dto.FundView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
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

public class FundsTab {

    private final ApiClient api;
    private final Async async;
    private final Runnable onChanged;

    private final TableView<FundView> fundTable = new TableView<>();
    private final VBox root = new VBox(8);

    private List<BalanceItem> accounts = List.of();
    private List<FundView> funds = List.of();

    public FundsTab(ApiClient api, Async async, Runnable onChanged) {
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
        loadFunds(null);
    }

    private void buildView() {
        root.setPadding(new Insets(10));

        Button addFund = new Button("Додати фонд");
        addFund.setOnAction(e -> showFundDialog());
        Button contribute = new Button("Зробити внесок");
        contribute.setOnAction(e -> showContributeDialog());
        Button refresh = new Button("Оновити");
        refresh.setOnAction(e -> refreshAll());
        HBox actions = new HBox(8, addFund, contribute, refresh);

        fundTable.getColumns().add(textColumn("Назва", 200, FundView::name));
        fundTable.getColumns().add(textColumn("Ціль, UAH", 140, f -> money(f.target())));
        fundTable.getColumns().add(textColumn("Накопичено, UAH", 140, f -> money(f.accumulated())));
        fundTable.getColumns().add(textColumn("Виконано, %", 120, f -> percent(f.percent()) + " %"));
        TableColumn<FundView, BigDecimal> progressCol = new TableColumn<>("Прогрес");
        progressCol.setPrefWidth(260);
        progressCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().percent()));
        progressCol.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();

            {
                bar.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                double progress = Math.max(0.0, Math.min(1.0, item.doubleValue() / 100.0));
                bar.setProgress(progress);
                setGraphic(bar);
            }
        });
        fundTable.getColumns().add(progressCol);
        VBox.setVgrow(fundTable, Priority.ALWAYS);

        root.getChildren().addAll(actions, new Label("Цільові фонди"), fundTable);
    }

    private void loadFunds(Long preferredId) {
        FundView current = fundTable.getSelectionModel().getSelectedItem();
        Long targetId = preferredId != null ? preferredId : (current != null ? current.id() : null);
        async.run(api::funds, list -> {
            funds = list;
            fundTable.setItems(FXCollections.observableArrayList(list));
            if (targetId != null) {
                for (FundView item : list) {
                    if (item.id() == targetId) {
                        fundTable.getSelectionModel().select(item);
                        return;
                    }
                }
            }
            fundTable.getSelectionModel().clearSelection();
        }, ErrorHandler::show);
    }

    private void showFundDialog() {
        TextField name = limited(60);
        TextField target = new TextField();
        GridPane grid = grid();
        grid.addRow(0, new Label("Назва цілі:"), name);
        grid.addRow(1, new Label("Цільова сума, UAH:"), target);

        confirm("Новий фонд", grid, () -> {
            FundRequest request = new FundRequest(
                    name.getText(),
                    parseAmount(target.getText(), "Вкажіть цільову суму", "Некоректна цільова сума"));
            async.run(() -> api.createFund(request), created -> loadFunds(created.id()), ErrorHandler::show);
        });
    }

    private void showContributeDialog() {
        if (funds.isEmpty()) {
            ErrorHandler.show(new ValidationException("Спершу створіть фонд"));
            return;
        }
        if (accounts.isEmpty()) {
            ErrorHandler.show(new ValidationException("Спершу створіть рахунок"));
            return;
        }
        ComboBox<FundView> fund = new ComboBox<>(FXCollections.observableArrayList(funds));
        fund.setConverter(converter(FundView::name));
        FundView selected = fundTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            fund.getSelectionModel().select(selected);
        } else {
            fund.getSelectionModel().select(funds.get(0));
        }

        ComboBox<BalanceItem> account = new ComboBox<>(FXCollections.observableArrayList(accounts));
        account.setConverter(converter(a -> a.name() + " (" + a.owner() + ")"));
        TextField amount = new TextField();

        GridPane grid = grid();
        grid.addRow(0, new Label("Фонд:"), fund);
        grid.addRow(1, new Label("Рахунок:"), account);
        grid.addRow(2, new Label("Сума внеску, UAH:"), amount);

        confirm("Внесок у фонд", grid, () -> {
            if (fund.getValue() == null) {
                throw new ValidationException("Оберіть фонд");
            }
            if (account.getValue() == null) {
                throw new ValidationException("Оберіть рахунок");
            }
            long fundId = fund.getValue().id();
            ContributionRequest request = new ContributionRequest(
                    account.getValue().id(),
                    parseAmount(amount.getText(), "Вкажіть суму", "Некоректна сума"));
            async.run(() -> api.contributeToFund(fundId, request), tx -> {
                loadFunds(fundId);
                onChanged.run();
            }, ErrorHandler::show);
        });
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

    private static BigDecimal parseAmount(String text, String missingMessage, String invalidMessage) {
        if (text == null || text.isBlank()) {
            throw new ValidationException(missingMessage);
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ValidationException(invalidMessage);
        }
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String percent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}

package com.tracker.ui;

import com.tracker.client.ApiClient;
import com.tracker.client.Async;
import com.tracker.domain.ValidationException;
import com.tracker.service.dto.CategoryReport;
import com.tracker.service.dto.CategoryReportRow;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class ReportsTab {

    private static final long MAX_IMPORT_BYTES = 9L * 1024 * 1024;

    private final ApiClient api;
    private final Async async;
    private final Runnable onChanged;

    private final ComboBox<String> kindBox = new ComboBox<>(FXCollections.observableArrayList("Витрати", "Доходи"));
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final Label totalLabel = new Label("Загальна сума: -");
    private final TableView<CategoryReportRow> table = new TableView<>();
    private final PieChart chart = new PieChart();
    private final VBox root = new VBox(8);

    public ReportsTab(ApiClient api, Async async, Runnable onChanged) {
        this.api = api;
        this.async = async;
        this.onChanged = onChanged;
        buildView();
    }

    public Node view() {
        return root;
    }

    public void refreshAll() {
        loadReport();
    }

    private void buildView() {
        root.setPadding(new Insets(10));

        kindBox.getSelectionModel().select("Витрати");
        kindBox.setOnAction(e -> loadReport());

        fromPicker.setPromptText("Від");
        fromPicker.setEditable(false);
        toPicker.setPromptText("До");
        toPicker.setEditable(false);

        Button build = new Button("Побудувати");
        build.setOnAction(e -> loadReport());
        Button reset = new Button("Скинути");
        reset.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            loadReport();
        });
        Button refresh = new Button("Оновити");
        refresh.setOnAction(e -> refreshAll());

        HBox controls = new HBox(
                8,
                new Label("Тип:"),
                kindBox,
                new Label("Період:"),
                fromPicker,
                toPicker,
                build,
                reset,
                refresh);
        controls.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> formatBox = new ComboBox<>(FXCollections.observableArrayList("Excel", "SQLite"));
        formatBox.getSelectionModel().select("Excel");
        Button exportButton = new Button("Експортувати");
        exportButton.setOnAction(e -> exportData(formatBox.getValue()));
        Button importButton = new Button("Імпортувати");
        importButton.setOnAction(e -> importData(formatBox.getValue()));
        HBox exchangeControls = new HBox(
                8,
                new Label("Обмін даними:"),
                formatBox,
                exportButton,
                importButton);
        exchangeControls.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().add(textColumn("Категорія", 200, CategoryReportRow::category));
        table.getColumns().add(textColumn("Сума, UAH", 130, r -> money(r.amount())));
        table.getColumns().add(textColumn("Частка, %", 100, r -> percent(r.percent()) + " %"));
        table.setPrefWidth(450);
        table.setPlaceholder(new Label("Немає даних"));

        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setTitle("Розподіл за категоріями");

        HBox content = new HBox(12, table, chart);
        HBox.setHgrow(chart, Priority.ALWAYS);
        VBox.setVgrow(content, Priority.ALWAYS);

        root.getChildren().addAll(controls, exchangeControls, totalLabel, content);
    }

    private void exportData(String selectedFormat) {
        boolean excel = !"SQLite".equals(selectedFormat);
        String format = excel ? "excel" : "sqlite";
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        async.run(() -> api.exportTransactions(format, from, to), bytes -> saveExport(bytes, excel), ErrorHandler::show);
    }

    private void saveExport(byte[] bytes, boolean excel) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Експорт транзакцій");
        if (excel) {
            chooser.setInitialFileName("transactions.xlsx");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        } else {
            chooser.setInitialFileName("transactions.sqlite");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite (*.sqlite)", "*.sqlite"));
        }
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Files.write(file.toPath(), bytes);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Експорт транзакцій");
            alert.setHeaderText(null);
            alert.setContentText("Файл успішно збережено: " + file.getName());
            alert.showAndWait();
        } catch (IOException e) {
            ErrorHandler.show(new ValidationException("Не вдалося зберегти файл: " + e.getMessage()));
        }
    }

    private void importData(String selectedFormat) {
        boolean excel = !"SQLite".equals(selectedFormat);
        String format = excel ? "excel" : "sqlite";
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Імпорт транзакцій");
        Path samplesDir = Path.of("samples");
        if (Files.isDirectory(samplesDir)) {
            chooser.setInitialDirectory(samplesDir.toFile());
        }
        if (excel) {
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"),
                    new FileChooser.ExtensionFilter("Усі файли (*.*)", "*.*"));
        } else {
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("SQLite (*.sqlite, *.db)", "*.sqlite", "*.db"),
                    new FileChooser.ExtensionFilter("Усі файли (*.*)", "*.*"));
        }
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        byte[] bytes;
        try {
            if (Files.size(file.toPath()) > MAX_IMPORT_BYTES) {
                ErrorHandler.show(new ValidationException("Файл імпорту перевищує 9 МБ"));
                return;
            }
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            ErrorHandler.show(new ValidationException("Не вдалося прочитати файл: " + e.getMessage()));
            return;
        }
        async.run(() -> api.importTransactions(format, bytes), response -> {
            loadReport();
            onChanged.run();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Імпорт транзакцій");
            alert.setHeaderText(null);
            alert.setContentText("Імпортовано транзакцій: " + response.imported());
            alert.showAndWait();
        }, ErrorHandler::show);
    }

    private void loadReport() {
        String kind = "Доходи".equals(kindBox.getValue()) ? "INCOME" : "EXPENSE";
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        async.run(() -> api.categoryReport(kind, from, to), this::showReport, ErrorHandler::show);
    }

    private void showReport(CategoryReport report) {
        String kindLabel = "INCOME".equals(report.kind()) ? "Доходи" : "Витрати";
        totalLabel.setText(kindLabel + ", загальна сума: " + money(report.total()) + " UAH");
        table.setItems(FXCollections.observableArrayList(report.rows()));
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (CategoryReportRow row : report.rows()) {
            pieData.add(new PieChart.Data(
                    row.category() + " (" + percent(row.percent()) + " %)",
                    row.amount().doubleValue()));
        }
        chart.getData().clear();
        chart.layout();
        chart.getData().setAll(pieData);
    }

    private static <T> TableColumn<T, String> textColumn(String title, double width, Function<T, String> getter) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        return column;
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String percent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}

package com.tracker.ui;

import com.tracker.Config;
import com.tracker.api.Json;
import com.tracker.client.ApiClient;
import com.tracker.client.Async;
import com.tracker.client.HttpGateway;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class AppFx extends Application {

    @Override
    public void start(Stage stage) {
        Config config = Config.load(Path.of("config.properties"));
        HttpGateway gateway = new HttpGateway(Json.create(), "http://127.0.0.1:" + config.serverPort());
        ApiClient api = new ApiClient(gateway);
        Async async = new Async();

        Label status = new Label("Транзакції ще не завантажено");
        status.setPadding(new Insets(4, 8, 4, 8));

        AccountsTab accountsTab = new AccountsTab(api, async, status);
        Tab tab = new Tab("Рахунки", accountsTab.view());
        tab.setClosable(false);
        ReceiptsTab receiptsTab = new ReceiptsTab(api, async, accountsTab::refreshAll);
        Tab receipts = new Tab("Чеки", receiptsTab.view());
        receipts.setClosable(false);
        ProductsTab productsTab = new ProductsTab(api, async, accountsTab::refreshAll);
        Tab products = new Tab("Вклади/Кредити", productsTab.view());
        products.setClosable(false);
        FundsTab fundsTab = new FundsTab(api, async, accountsTab::refreshAll);
        Tab funds = new Tab("Фонди", fundsTab.view());
        funds.setClosable(false);
        TabPane tabs = new TabPane(tab, receipts, products, funds);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == receipts) {
                receiptsTab.refreshAll();
            } else if (selected == products) {
                productsTab.refreshAll();
            } else if (selected == funds) {
                fundsTab.refreshAll();
            }
        });

        BorderPane root = new BorderPane();
        root.setCenter(tabs);
        root.setBottom(status);

        stage.setTitle("Особиста бухгалтерія");
        stage.setScene(new Scene(root, 1000, 720));
        stage.show();
        accountsTab.refreshAll();
        receiptsTab.refreshAll();
        productsTab.refreshAll();
        fundsTab.refreshAll();
    }
}

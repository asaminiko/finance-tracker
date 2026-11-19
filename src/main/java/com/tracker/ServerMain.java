package com.tracker;

import com.tracker.api.Api;
import io.javalin.Javalin;
import java.nio.file.Path;
import java.time.Clock;

public class ServerMain {

    public static void main(String[] args) {
        try {
            Config config = Config.load(Path.of("config.properties"));
            config.prepareDirectories();
            ServerContext context = new ServerContext(config, Clock.systemDefaultZone());
            context.database.init();
            context.database.inTx(context.ledger::runTemplates);
            Api api = new Api(context.ledger, context.database, context.txManager, context.gson);
            Javalin app = api.create();
            app.start("127.0.0.1", config.serverPort());
        } catch (Exception e) {
            System.err.println("Помилка запуску сервера: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

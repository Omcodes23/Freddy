package com.freddy.dashboard;

import com.freddy.dashboard.controller.DashboardController;
import com.freddy.dashboard.service.TelemetryClient;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DashboardApp extends Application {

    @Override
    public void start(Stage stage) {

        DashboardController controller = new DashboardController();
        TelemetryClient telemetry = new TelemetryClient();

        controller.npcName = new Label();
        controller.thought = new Label();
        controller.action = new Label();
        controller.position = new Label();

        VBox root = new VBox(10,
                new Label("🤖 FREDDY AI DASHBOARD"),
                controller.npcName,
                controller.thought,
                controller.action,
                controller.position
        );

        controller.update(telemetry.fetch());

        stage.setTitle("Freddy AI Dashboard");
        stage.setScene(new Scene(root, 420, 250));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

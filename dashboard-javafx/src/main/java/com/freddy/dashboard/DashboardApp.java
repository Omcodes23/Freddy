package com.freddy.dashboard;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DashboardApp extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("Freddy Dashboard");
        stage.setScene(new Scene(new Label("Freddy is running"), 300, 200));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

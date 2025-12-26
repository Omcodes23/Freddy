package com.freddy.dashboard;

import com.freddy.dashboard.ui.DashboardUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Professional Scientific Dashboard for Freddy AI
 * Real-time neural substrate monitoring with live POV display
 */
public class FreddyDashboardApp extends Application {

    private TextArea povTextArea;
    private TextArea neuralLogArea;
    private TextArea decisionArea;
    private Label latencyValue;
    private Label frequencyValue;
    private Label cyclesValue;
    private Label successValue;
    private Label positionValue;
    private Label entitiesValue;
    private Label statusLabel;
    
    private Label[] stateSymbols = new Label[4];
    private Label[] stateLabels = new Label[4];
    
    private volatile boolean running = true;
    private static final int DASHBOARD_PORT = 25566;
    private long tickCounter = 0;
    private long lastTickTime = System.currentTimeMillis();
    
    private static final String BG_PRIMARY = "#0a0e14";
    private static final String ACCENT_GREEN = "#2ea043";
    private static final String ACCENT_RED = "#da3633";
    private static final String ACCENT_YELLOW = "#d29922";
    private static final String TEXT_PRIMARY = "#e6edf3";
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("◈ FREDDY NEURAL MONITORING SYSTEM ◈");
        
        // Root layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_PRIMARY + ";");
        root.setPadding(new Insets(15));
        
        // Top: Header with status
        HBox header = createHeader();
        root.setTop(header);
        
        // Center: Main monitoring grid
        GridPane mainGrid = createMainGrid();
        root.setCenter(mainGrid);
        
        Scene scene = new Scene(root, 1600, 1000);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Start telemetry listener
        startTelemetryListener();
        
        primaryStage.setOnCloseRequest(e -> {
            running = false;
            Platform.exit();
            System.exit(0);
        });
    }
    
    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 15, 0));
        
        Label title = new Label("◈ FREDDY NEURAL MONITORING SYSTEM");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 20));
        title.setTextFill(Color.web(ACCENT_GREEN));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusLabel = new Label("● INITIALIZING");
        statusLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        statusLabel.setTextFill(Color.web(ACCENT_YELLOW));
        
        Label timeLabel = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        timeLabel.setFont(Font.font("Consolas", 14));
        timeLabel.setTextFill(Color.web(TEXT_PRIMARY));
        
        // Update time every second
        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(() -> 
                        timeLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                    );
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
        
        header.getChildren().addAll(title, spacer, statusLabel, new Separator(), timeLabel);
        return header;
    }
    
    private GridPane createMainGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        
        // Left Column: POV + Metrics
        VBox leftColumn = new VBox(15);
        VBox povPanel = DashboardUI.createLivePOVPanel();
        povTextArea = (TextArea) povPanel.lookup("#povTextArea");
        
        VBox metricsPanel = DashboardUI.createSystemMetricsPanel();
        latencyValue = (Label) metricsPanel.lookup("#latencyValue");
        frequencyValue = (Label) metricsPanel.lookup("#frequencyValue");
        cyclesValue = (Label) metricsPanel.lookup("#cyclesValue");
        successValue = (Label) metricsPanel.lookup("#successValue");
        positionValue = (Label) metricsPanel.lookup("#positionValue");
        entitiesValue = (Label) metricsPanel.lookup("#entitiesValue");
        
        leftColumn.getChildren().addAll(povPanel, metricsPanel);
        VBox.setVgrow(povPanel, Priority.ALWAYS);
        
        // Right Column: Decision Matrix + State Monitor + Neural Log
        VBox rightColumn = new VBox(15);
        VBox decisionPanel = DashboardUI.createDecisionMatrixPanel();
        decisionArea = (TextArea) decisionPanel.lookup("#decisionArea");
        
        VBox statePanel = DashboardUI.createStateMonitorPanel();
        for (int i = 0; i < 4; i++) {
            stateSymbols[i] = (Label) statePanel.lookup("#stateSymbol" + i);
            stateLabels[i] = (Label) statePanel.lookup("#stateLabel" + i);
        }
        
        VBox logPanel = DashboardUI.createNeuralLogPanel();
        neuralLogArea = (TextArea) logPanel.lookup("#neuralLogArea");
        
        rightColumn.getChildren().addAll(decisionPanel, statePanel, logPanel);
        VBox.setVgrow(logPanel, Priority.ALWAYS);
        
        // Add columns to grid
        grid.add(leftColumn, 0, 0);
        grid.add(rightColumn, 1, 0);
        
        // Column constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(55);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(45);
        grid.getColumnConstraints().addAll(col1, col2);
        
        // Row constraints
        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        grid.getRowConstraints().add(row);
        
        return grid;
    }
    
    private void startTelemetryListener() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(DASHBOARD_PORT)) {
                log("SYSTEM", "Telemetry server initialized on port " + DASHBOARD_PORT);
                Platform.runLater(() -> {
                    statusLabel.setText("● AWAITING CONNECTION");
                    statusLabel.setTextFill(Color.web(ACCENT_YELLOW));
                });
                
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log("SYSTEM", "Neural connection established");
                        Platform.runLater(() -> {
                            statusLabel.setText("● ONLINE");
                            statusLabel.setTextFill(Color.web(ACCENT_GREEN));
                        });
                        
                        BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream())
                        );
                        
                        String line;
                        while ((line = in.readLine()) != null) {
                            processTelemetryMessage(line);
                        }
                        
                        clientSocket.close();
                        log("SYSTEM", "Neural connection terminated");
                        Platform.runLater(() -> {
                            statusLabel.setText("● OFFLINE");
                            statusLabel.setTextFill(Color.web(ACCENT_RED));
                        });
                        
                    } catch (IOException e) {
                        if (running) {
                            log("ERROR", "Connection fault: " + e.getMessage());
                        }
                    }
                }
                
            } catch (IOException e) {
                log("ERROR", "Telemetry server initialization failed: " + e.getMessage());
                Platform.runLater(() -> {
                    statusLabel.setText("● FAULT");
                    statusLabel.setTextFill(Color.web(ACCENT_RED));
                });
            }
        }).start();
    }
    
    private void processTelemetryMessage(String message) {
        Platform.runLater(() -> {
            try {
                if (message.startsWith("TICK:")) {
                    tickCounter++;
                    cyclesValue.setText(String.valueOf(tickCounter));
                    
                    // Calculate frequency
                    long now = System.currentTimeMillis();
                    double freq = 1000.0 / (now - lastTickTime);
                    frequencyValue.setText(String.format("%.2f Hz", freq));
                    lastTickTime = now;
                    
                    updateState(0); // OBSERVE state
                    
                } else if (message.startsWith("OBSERVATION:")) {
                    String obs = message.substring(12).trim();
                    log("OBSERVE", obs);
                    updateState(0);
                    
                } else if (message.startsWith("POV:")) {
                    String povData = message.substring(4).trim();
                    updatePOVDisplay(povData);
                    updateState(0);
                    
                } else if (message.startsWith("POSITION:")) {
                    String pos = message.substring(9).trim();
                    positionValue.setText(pos);
                    
                } else if (message.startsWith("PLAYERS:")) {
                    String players = message.substring(8).trim();
                    entitiesValue.setText(players);
                    
                } else if (message.startsWith("THINKING:")) {
                    String thinking = message.substring(9).trim();
                    log("PROCESS", thinking);
                    updateDecisionMatrix("PROCESSING", thinking);
                    updateState(1); // PROCESS state
                    
                } else if (message.startsWith("LLM_RESPONSE:")) {
                    String response = message.substring(13).trim();
                    log("LLM", response);
                    updateDecisionMatrix("LLM OUTPUT", response);
                    updateState(2); // DECIDE state
                    
                } else if (message.startsWith("RESPONSE_TIME:")) {
                    String time = message.substring(14).trim();
                    latencyValue.setText(time + " ms");
                    
                } else if (message.startsWith("ACTION:")) {
                    String action = message.substring(7).trim();
                    log("EXECUTE", action);
                    updateDecisionMatrix("EXECUTION", action);
                    updateState(3); // EXECUTE state
                    
                } else if (message.startsWith("CHAT:")) {
                    String chat = message.substring(5).trim();
                    log("CHAT", chat);
                    
                } else if (message.startsWith("ERROR:")) {
                    String error = message.substring(6).trim();
                    log("ERROR", error);
                    
                } else {
                    log("DATA", message);
                }
                
            } catch (Exception e) {
                log("ERROR", "Message processing fault: " + e.getMessage());
            }
        });
    }
    
    private void updatePOVDisplay(String rawPOV) {
        // Format POV data for professional display
        StringBuilder formatted = new StringBuilder();
        formatted.append("╔═══════════════════════════════════════════╗\n");
        formatted.append("║   REALTIME SENSORY INPUT - FRAME DATA     ║\n");
        formatted.append("╠═══════════════════════════════════════════╣\n");
        
        if (rawPOV == null || rawPOV.isEmpty()) {
            formatted.append("║ >>> NO VISUAL DATA STREAM                 ║\n");
            formatted.append("║ >>> SENSORS OFFLINE                       ║\n");
        } else {
            formatted.append(rawPOV);
        }
        
        formatted.append("\n╚═══════════════════════════════════════════╝");
        povTextArea.setText(formatted.toString());
    }
    
    private void updateDecisionMatrix(String phase, String data) {
        StringBuilder matrix = new StringBuilder();
        matrix.append("╔════════════════════════════════════════╗\n");
        matrix.append(String.format("║  %s  ", phase));
        for (int i = 0; i < 40 - phase.length(); i++) matrix.append(" ");
        matrix.append("║\n");
        matrix.append("╠════════════════════════════════════════╣\n");
        
        String[] lines = data.split("\n");
        for (String line : lines) {
            matrix.append("║ " + line);
            for (int i = line.length(); i < 39; i++) matrix.append(" ");
            matrix.append("║\n");
        }
        
        matrix.append("╚════════════════════════════════════════╝");
        decisionArea.setText(matrix.toString());
    }
    
    private void updateState(int activeState) {
        String[] colors = {"#2ea043", "#1f6feb", "#d29922", "#ff6b6b"};
        String inactiveColor = "#2d3748";
        
        for (int i = 0; i < 4; i++) {
            if (i == activeState) {
                stateSymbols[i].setTextFill(Color.web(colors[i]));
                stateLabels[i].setTextFill(Color.web(colors[i]));
            } else {
                stateSymbols[i].setTextFill(Color.web(inactiveColor));
                stateLabels[i].setTextFill(Color.web(inactiveColor));
            }
        }
    }
    
    private void log(String category, String message) {
        Platform.runLater(() -> {
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            String formatted = String.format("[%s] %s >> %s\n", timestamp, category, message);
            neuralLogArea.appendText(formatted);
            
            // Auto-scroll to bottom
            neuralLogArea.setScrollTop(Double.MAX_VALUE);
            
            // Limit log size
            String text = neuralLogArea.getText();
            String[] lines = text.split("\n");
            if (lines.length > 500) {
                StringBuilder trimmed = new StringBuilder();
                for (int i = lines.length - 500; i < lines.length; i++) {
                    trimmed.append(lines[i]).append("\n");
                }
                neuralLogArea.setText(trimmed.toString());
            }
        });
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}

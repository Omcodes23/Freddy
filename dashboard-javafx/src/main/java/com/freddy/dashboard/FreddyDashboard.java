package com.freddy.dashboard;

import com.freddy.dashboard.ui.DashboardUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import com.freddy.dashboard.graph.StepGraphVisualizer;

/**
 * Professional Scientific Dashboard for Freddy AI
 * Real-time neural monitoring with live POV visualization
 */
public class FreddyDashboard extends Application {

    private TextArea povTextArea;
    private Label[] viewPanels = new Label[4];  // Four view panels: First-person, Third-person, Map, Timeline
    private TextArea neuralLogArea;
    private TextArea decisionArea;
    private Label latencyValue;
    private Label frequencyValue;
    private Label cyclesValue;
    private Label successValue;
    private Label positionValue;
    private Label entitiesValue;
    private Label statusLabel;
    private StepGraphVisualizer stepGraphVisualizer;  // Goal steps graph visualization
    // Inventory UI refs
    private GridPane inventoryGrid;
    private final java.util.List<Label> inventorySlots = new java.util.ArrayList<>();
    private Label inventoryItemCountLabel;
    private Label inventoryCapacityLabel;
    private Label inventoryWeightLabel;

    private final Label[] stateSymbols = new Label[4];
    private final Label[] stateLabels = new Label[4];

    private volatile boolean running = true;
    private static final int DASHBOARD_PORT = 25566;
    private long tickCounter = 0;
    private long lastTickTime = System.currentTimeMillis();
    private static final boolean TEST_MODE = false;  // Set to true to generate test frames

    // Frame rate tracking for each view panel
    private final long[] panelFrameTimes = new long[4];      // Last frame time for each panel
    private final int[] panelFrameCount = new int[4];        // Total frames received for each panel
    private final double[] panelFPS = new double[4];         // Current FPS for each panel

    private static final String BG_PRIMARY = "#0a0e14";
    private static final String ACCENT_GREEN = "#2ea043";
    private static final String ACCENT_BLUE = "#1f6feb";
    private static final String ACCENT_RED = "#da3633";
    private static final String ACCENT_YELLOW = "#d29922";
    private static final String TEXT_PRIMARY = "#e6edf3";
    private static final String TEXT_SECONDARY = "#8b949e";

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("◈ FREDDY NEURAL MONITORING SYSTEM ◈");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_PRIMARY + ";");
        root.setPadding(new Insets(15));

        HBox header = createHeader();
        root.setTop(header);

        TabPane tabPane = createTabPane();
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1600, 1000);
        primaryStage.setScene(scene);
        primaryStage.show();

        startTelemetryListener();
        
        // Force test frame immediately to verify rendering pipeline works
        log("SYSTEM", "=== RENDERING TEST ===");
        log("SYSTEM", "Attempting to render test frame to Panel 0");
        testRenderPipeline();

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

    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: " + BG_PRIMARY + ";");
        
        Tab missionTab = new Tab("◈ MISSION CONTROL");
        missionTab.setClosable(false);
        missionTab.setContent(createMainGrid());
        
        Tab viewsTab = new Tab("◈ AI VIEWS");
        viewsTab.setClosable(false);
        viewsTab.setContent(createMultiViewPanel());
        
        Tab inventoryTab = new Tab("◈ NPC INVENTORY");
        inventoryTab.setClosable(false);
        inventoryTab.setContent(createInventoryPanel());
        
        Tab goalsTab = new Tab("◈ GOALS & DIRECTIVES");
        goalsTab.setClosable(false);
        goalsTab.setContent(createGoalsPanel());
        
        tabPane.getTabs().addAll(missionTab, viewsTab, inventoryTab, goalsTab);
        return tabPane;
    }
    
    private VBox createInventoryPanel() {
        VBox inventoryPanel = new VBox(15);
        inventoryPanel.setPadding(new Insets(20));
        inventoryPanel.setStyle("-fx-background-color: " + BG_PRIMARY + ";");
        
        Label title = new Label("◈ NPC INVENTORY");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(ACCENT_BLUE));
        
        // Inventory grid
        inventoryGrid = new GridPane();
        inventoryGrid.setHgap(10);
        inventoryGrid.setVgap(10);
        inventoryGrid.setPadding(new Insets(15));
        inventoryGrid.setStyle("-fx-background-color: #1c2128; -fx-border-color: " + ACCENT_BLUE + "; -fx-border-width: 1;");
        
        // Create 36 inventory slots (2 rows of 18)
        inventorySlots.clear();
        for (int i = 0; i < 36; i++) {
            Label slot = new Label("[ ]");
            slot.setPrefWidth(40);
            slot.setPrefHeight(40);
            slot.setStyle(
                "-fx-background-color: #0f1419; " +
                "-fx-border-color: #2b3d47; " +
                "-fx-border-width: 1; " +
                "-fx-text-fill: " + TEXT_SECONDARY + "; " +
                "-fx-font-family: 'Consolas'; " +
                "-fx-font-size: 10px; " +
                "-fx-alignment: center;"
            );
            
            int row = i / 18;
            int col = i % 18;
            inventoryGrid.add(slot, col, row);
            inventorySlots.add(slot);
        }
        
        ScrollPane scrollPane = new ScrollPane(inventoryGrid);
        scrollPane.setStyle("-fx-background-color: " + BG_PRIMARY + "; -fx-control-inner-background: " + BG_PRIMARY + ";");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Stats
        HBox statsBox = new HBox(20);
        statsBox.setPadding(new Insets(10));
        statsBox.setStyle("-fx-background-color: #1c2128; -fx-border-color: " + ACCENT_BLUE + "; -fx-border-width: 1;");
        
        inventoryItemCountLabel = new Label("📦 Items: 0");
        inventoryItemCountLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        inventoryItemCountLabel.setTextFill(Color.web(ACCENT_BLUE));
        
        inventoryCapacityLabel = new Label("🔖 Capacity: 0 / 64");
        inventoryCapacityLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        inventoryCapacityLabel.setTextFill(Color.web(ACCENT_BLUE));
        
        inventoryWeightLabel = new Label("⚖️ Weight: 0 kg");
        inventoryWeightLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        inventoryWeightLabel.setTextFill(Color.web(ACCENT_BLUE));
        
        statsBox.getChildren().addAll(inventoryItemCountLabel, inventoryCapacityLabel, inventoryWeightLabel);
        
        inventoryPanel.getChildren().addAll(title, scrollPane, statsBox);
        return inventoryPanel;
    }
    
    private VBox createGoalsPanel() {
        VBox goalsPanel = new VBox(20);
        goalsPanel.setPadding(new Insets(20));
        goalsPanel.setStyle("-fx-background-color: " + BG_PRIMARY + ";");
        
        Label title = new Label("◈ AI MISSION DIRECTIVES");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(ACCENT_GREEN));
        
        Label description = new Label("Select a primary goal for Freddy to pursue autonomously:");
        description.setFont(Font.font("Consolas", 12));
        description.setTextFill(Color.web(TEXT_PRIMARY));
        
        ComboBox<String> goalSelector = new ComboBox<>();
        goalSelector.setPromptText("Choose Goal...");
        goalSelector.getItems().addAll(
            "🎯 FOLLOW PLAYER",
            "💎 MINE DIAMONDS",
            "🌳 GATHER WOOD",
            "🪨 GATHER STONE",
            "🌾 FARM CROPS",
            "⚔️ HUNT MOBS",
            "🗺️ EXPLORE AREA",
            "🏠 BUILD STRUCTURE"
        );
        goalSelector.setPrefWidth(400);
        goalSelector.setStyle(
            "-fx-background-color: #1c2128; " +
            "-fx-text-fill: " + TEXT_PRIMARY + "; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 14px;"
        );
        
        Button activateButton = new Button("► ACTIVATE GOAL");
        activateButton.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        activateButton.setStyle(
            "-fx-background-color: " + ACCENT_GREEN + "; " +
            "-fx-text-fill: #0a0e14; " +
            "-fx-padding: 10 20;"
        );
        
        Label statusLabel = new Label("⚠️ GOAL SYSTEM: READY");
        statusLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        statusLabel.setTextFill(Color.web(ACCENT_YELLOW));
        
        // Step visualization area
        VBox stepsContainer = new VBox(10);
        stepsContainer.setPadding(new Insets(15));
        stepsContainer.setStyle(
            "-fx-background-color: #1c2128; " +
            "-fx-border-color: " + ACCENT_BLUE + "; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5;"
        );
        
        Label stepsTitle = new Label("📋 GOAL STEP GRAPH (Object Graph Visualizer Style)");
        stepsTitle.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        stepsTitle.setTextFill(Color.web(ACCENT_BLUE));
        
        // Graph visualizer with nodes and edges
        stepGraphVisualizer = new StepGraphVisualizer(800, 500);
        stepGraphVisualizer.setStyle("-fx-background-color: #0a0e14;");
        
        stepsContainer.getChildren().addAll(stepsTitle, stepGraphVisualizer);
        VBox.setVgrow(stepsContainer, Priority.ALWAYS);
        
        activateButton.setOnAction(e -> {
            String selectedGoal = goalSelector.getValue();
            if (selectedGoal != null && !selectedGoal.isEmpty()) {
                log("GOAL", "Button clicked: " + selectedGoal);
                statusLabel.setText("✅ GOAL ACTIVATED: " + selectedGoal);
                statusLabel.setTextFill(Color.web(ACCENT_GREEN));
                
                // Send goal to plugin via command socket
                sendGoalCommand(selectedGoal);
                log("GOAL", "Command sent to plugin");
            } else {
                log("WARN", "No goal selected!");
                statusLabel.setText("⚠️ Please select a goal first");
                statusLabel.setTextFill(Color.web(ACCENT_YELLOW));
            }
        });
        
        goalsPanel.getChildren().addAll(
            title, 
            description, 
            goalSelector, 
            activateButton, 
            statusLabel, 
            stepsContainer
        );
        
        return goalsPanel;
    }
    
    /**
     * Send goal command to plugin via TCP 25567
     */
    private void sendGoalCommand(String goalName) {
        new Thread(() -> {
            try {
                log("CMD", "Connecting to plugin command server at 127.0.0.1:25567...");
                Socket socket = new Socket("127.0.0.1", 25567);
                java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
                
                String command = "GOAL:" + goalName;
                log("CMD", "Sending command: " + command);
                out.println(command);
                out.flush();
                
                log("CMD", "✓ Command sent successfully");
                
                out.close();
                socket.close();
                
            } catch (java.net.ConnectException e) {
                log("ERROR", "Cannot connect to plugin (port 25567): Is Minecraft server running with plugin?");
                Platform.runLater(() -> {
                    statusLabel.setText("❌ Plugin not reachable (port 25567)");
                    statusLabel.setTextFill(Color.web(ACCENT_RED));
                });
            } catch (Exception e) {
                log("ERROR", "Failed to send goal command: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }, "GoalCommandSender").start();
    }

    private GridPane createMainGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        VBox leftColumn = new VBox(15);
        
        // Split into two sections: Raytracing + Metrics
        VBox povPanel = DashboardUI.createLivePOVPanel();
        povTextArea = (TextArea) povPanel.lookup("#povTextArea");
        povPanel.setPrefHeight(400);  // Fixed height for raytracing

        VBox metricsPanel = DashboardUI.createSystemMetricsPanel();
        latencyValue = (Label) metricsPanel.lookup("#latencyValue");
        frequencyValue = (Label) metricsPanel.lookup("#frequencyValue");
        cyclesValue = (Label) metricsPanel.lookup("#cyclesValue");
        successValue = (Label) metricsPanel.lookup("#successValue");
        positionValue = (Label) metricsPanel.lookup("#positionValue");
        entitiesValue = (Label) metricsPanel.lookup("#entitiesValue");

        leftColumn.getChildren().addAll(povPanel, metricsPanel);
        VBox.setVgrow(povPanel, Priority.NEVER);
        VBox.setVgrow(metricsPanel, Priority.ALWAYS);

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

        grid.add(leftColumn, 0, 0);
        grid.add(rightColumn, 1, 0);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(55);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(45);
        grid.getColumnConstraints().addAll(col1, col2);

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

                        // Handle each client in a separate thread
                        new Thread(() -> {
                            try {
                                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                                log("SYSTEM", "Client reader started - waiting for messages...");

                                String line;
                                int messageCount = 0;
                                while ((line = in.readLine()) != null) {
                                    messageCount++;
                                    if (messageCount % 10 == 0) {
                                        log("SYSTEM", "Received " + messageCount + " messages so far");
                                    }
                                    processTelemetryMessage(line);
                                }

                                clientSocket.close();
                                log("SYSTEM", "Client disconnected after " + messageCount + " messages");
                            } catch (IOException e) {
                                if (running) {
                                    log("ERROR", "Connection fault: " + e.getMessage());
                                }
                            }
                        }).start();

                    } catch (IOException e) {
                        if (running) {
                            log("ERROR", "Listener fault: " + e.getMessage());
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
                // Log ALL messages for debugging
                if (message.length() > 100) {
                    log("RECV", message.substring(0, 80) + "... (" + message.length() + " chars)");
                } else {
                    log("RECV", message);
                }
                
                if (message.startsWith("TICK:")) {
                    tickCounter++;
                    cyclesValue.setText(String.valueOf(tickCounter));

                    long now = System.currentTimeMillis();
                    double freq = 1000.0 / Math.max(1, (now - lastTickTime));
                    frequencyValue.setText(String.format("%.2f Hz", freq));
                    lastTickTime = now;

                    updateState(0);

                } else if (message.startsWith("POV:")) {
                    String povData = message.substring(4).trim();
                    // Decode escape sequences: \n back to actual newlines
                    povData = povData.replace("\\n", "\n");
                    updatePOVDisplay(povData);
                    updateState(0);

                } else if (message.startsWith("VIDEO_FP:")) {
                    // First-person view (primary NPC POV)
                    String payload = message.substring(9).trim();
                    String npcName = null;
                    String base64Data = payload;
                    int sep = payload.indexOf(":");
                    if (sep > 0) {
                        npcName = payload.substring(0, sep).trim();
                        base64Data = payload.substring(sep + 1).trim();
                    }
                    if (npcName != null && !npcName.isEmpty()) {
                        log("VIDEO_FP", "Frame from NPC '" + npcName + "' (" + base64Data.length() + " chars)");
                    } else {
                        log("VIDEO_FP", "First-person frame received (" + base64Data.length() + " chars)");
                    }
                    updateViewFrame(0, base64Data);  // Panel 0: First-person
                    log("VIDEO_FP", "Frame update called");

                } else if (message.startsWith("VIDEO_3P:")) {
                    // Third-person view
                    String payload = message.substring(9).trim();
                    String npcName = null;
                    String base64Data = payload;
                    int sep = payload.indexOf(":");
                    if (sep > 0) {
                        npcName = payload.substring(0, sep).trim();
                        base64Data = payload.substring(sep + 1).trim();
                    }
                    if (npcName != null && !npcName.isEmpty()) {
                        log("VIDEO_3P", "Frame from NPC '" + npcName + "' (" + base64Data.length() + " chars)");
                    } else {
                        log("VIDEO_3P", "Third-person frame received (" + base64Data.length() + " chars)");
                    }
                    updateViewFrame(1, base64Data);  // Panel 1: Third-person
                    
                } else if (message.startsWith("VIDEO_MAP:")) {
                    // Map view
                    String payload = message.substring(10).trim();
                    String npcName = null;
                    String base64Data = payload;
                    int sep = payload.indexOf(":");
                    if (sep > 0) {
                        npcName = payload.substring(0, sep).trim();
                        base64Data = payload.substring(sep + 1).trim();
                    }
                    if (npcName != null && !npcName.isEmpty()) {
                        log("VIDEO_MAP", "Frame from NPC '" + npcName + "' (" + base64Data.length() + " chars)");
                    } else {
                        log("VIDEO_MAP", "Map frame received (" + base64Data.length() + " chars)");
                    }
                    updateViewFrame(2, base64Data);  // Panel 2: Map view
                    
                } else if (message.startsWith("VIDEO_REPLAY:")) {
                    // Timeline replay - DO NOT SHOW VIDEO STREAMS
                    // Just log but don't update the panel
                    String payload = message.substring(13).trim();
                    String npcName = null;
                    int sep = payload.indexOf(":");
                    if (sep > 0) {
                        npcName = payload.substring(0, sep).trim();
                    }
                    if (npcName != null && !npcName.isEmpty()) {
                        log("VIDEO_REPLAY", "Frame from NPC '" + npcName + "' (ignored - timeline view disabled)");
                    } else {
                        log("VIDEO_REPLAY", "Timeline frame received (ignored - timeline view disabled)");
                    }
                    // Don't call updateViewFrame(3, base64Data) - user doesn't want video in timeline

                } else if (message.startsWith("VIDEO:")) {
                    // Legacy: treat as first-person
                    String base64Data = message.substring(6).trim();
                    log("VIDEO", "Legacy frame received (" + base64Data.length() + " chars Base64)");
                    updateViewFrame(0, base64Data);
                    log("VIDEO", "Frame update called");

                } else if (message.startsWith("OBSERVATION:")) {
                    String obs = message.substring(12).trim();
                    log("OBSERVE", obs);
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
                    updateState(1);

                } else if (message.startsWith("LLM_RESPONSE:")) {
                    String response = message.substring(13).trim();
                    log("LLM", response);
                    updateDecisionMatrix("LLM OUTPUT", response);
                    updateState(2);

                } else if (message.startsWith("RESPONSE_TIME:")) {
                    String time = message.substring(14).trim();
                    latencyValue.setText(time + " ms");

                } else if (message.startsWith("ACTION:")) {
                    String action = message.substring(7).trim();
                    log("EXECUTE", action);
                    updateDecisionMatrix("EXECUTION", action);
                    updateState(3);

                } else if (message.startsWith("CHAT:")) {
                    String chat = message.substring(5).trim();
                    log("CHAT", chat);

                } else if (message.startsWith("GOAL_STEPS:")) {
                    String stepsJson = message.substring(11).trim();
                    log("GOAL_STEPS", "Received steps: " + stepsJson);
                    updateGoalSteps(stepsJson);

                } else if (message.startsWith("GOAL_STEP_UPDATE:")) {
                    String payload = message.substring(17).trim();
                    String id = extractJsonValue(payload, "id");
                    String status = extractJsonValue(payload, "status");
                    if (id != null && !id.isEmpty() && status != null && !status.isEmpty()) {
                        log("STEP_UPDATE", id + " -> " + status);
                        if (stepGraphVisualizer != null) {
                            stepGraphVisualizer.updateStepStatus(id, status);
                        }
                    }

                } else if (message.startsWith("INVENTORY:")) {
                    String payload = message.substring(10).trim();
                    updateInventoryUI(payload);

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

    private void updateInventoryUI(String payload) {
        // Payload format: MATERIAL=COUNT,MATERIAL=COUNT
        try {
            String[] parts = payload.isEmpty() ? new String[0] : payload.split(",");
            // Clear slots
            for (Label slot : inventorySlots) slot.setText("[ ]");
            int totalItems = 0;
            int slotIndex = 0;
            for (String part : parts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String mat = kv[0];
                    int count = Integer.parseInt(kv[1]);
                    totalItems += count;
                    if (slotIndex < inventorySlots.size()) {
                        inventorySlots.get(slotIndex).setText("[" + mat + " x" + count + "]");
                        slotIndex++;
                    }
                }
            }
            inventoryItemCountLabel.setText("📦 Items: " + totalItems);
            inventoryCapacityLabel.setText("🔖 Capacity: " + parts.length + " / 64");
            inventoryWeightLabel.setText("⚖️ Weight: " + Math.max(0, totalItems / 8) + " kg");
        } catch (Exception e) {
            log("ERROR", "Inventory parse error: " + e.getMessage());
        }
    }

    private void updatePOVDisplay(String rawPOV) {
        // Display the visual stream directly without additional formatting
        // The visual stream from WorldVisualization already has proper formatting
        if (rawPOV == null || rawPOV.isEmpty()) {
            povTextArea.setText(
                "╔════════════════════════════════════════════════════════════╗\n" +
                "║                  ⚠ NO VISUAL FEED                         ║\n" +
                "║             Waiting for NPC data stream...                ║\n" +
                "╚════════════════════════════════════════════════════════════╝"
            );
        } else {
            povTextArea.setText(rawPOV);
        }
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

    /**
     * Create multi-perspective view panel with 2-panel layout
     * LEFT: First-person POV (primary video streaming)
     * RIGHT: Timeline replay view
     */
    private VBox createMultiViewPanel() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(15));
        container.setStyle("-fx-background-color: " + BG_PRIMARY + ";");

        Label title = new Label("◈ AI VIDEO STREAM (Left: First-Person POV | Right: Timeline)");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(ACCENT_GREEN));

        HBox mainContent = new HBox(15);
        
        // LEFT PANEL: First-person POV (MAIN VIDEO)
        VBox leftPanel = new VBox(10);
        leftPanel.setStyle(
            "-fx-border-color: " + ACCENT_GREEN + "; " +
            "-fx-border-width: 2; " +
            "-fx-background-color: #0f1419; " +
            "-fx-padding: 15"
        );
        
        Label leftTitle = new Label("👁️ FIRST-PERSON POV (NPC Decision Analysis)");
        leftTitle.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        leftTitle.setTextFill(Color.web(ACCENT_GREEN));
        
        // Video panel for first-person view
        viewPanels[0] = new Label();
        viewPanels[0].setStyle(
            "-fx-background-color: #1a1f26; " +
            "-fx-text-fill: " + TEXT_SECONDARY + "; " +
            "-fx-font-size: 11px; " +
            "-fx-font-family: 'Consolas';"
        );
        viewPanels[0].setMaxWidth(Double.MAX_VALUE);
        viewPanels[0].setMaxHeight(Double.MAX_VALUE);
        viewPanels[0].setAlignment(Pos.CENTER);
        viewPanels[0].setText("\n\n[WAITING FOR VIDEO STREAM...]\n\nStreaming first-person view\nfrom AI entity perspective\n\n");
        viewPanels[0].setWrapText(true);
        
        VBox.setVgrow(viewPanels[0], Priority.ALWAYS);
        leftPanel.getChildren().addAll(leftTitle, viewPanels[0]);
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        
        // RIGHT PANEL: Timeline replay
        VBox rightPanel = new VBox(10);
        rightPanel.setStyle(
            "-fx-border-color: " + ACCENT_BLUE + "; " +
            "-fx-border-width: 2; " +
            "-fx-background-color: #0f1419; " +
            "-fx-padding: 15"
        );
        
        Label rightTitle = new Label("⏱️ TIMELINE REPLAY VIEW");
        rightTitle.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        rightTitle.setTextFill(Color.web(ACCENT_BLUE));
        
        // Video panel for timeline view
        viewPanels[3] = new Label();
        viewPanels[3].setStyle(
            "-fx-background-color: #1a1f26; " +
            "-fx-text-fill: " + TEXT_SECONDARY + "; " +
            "-fx-font-size: 11px; " +
            "-fx-font-family: 'Consolas';"
        );
        viewPanels[3].setMaxWidth(Double.MAX_VALUE);
        viewPanels[3].setMaxHeight(Double.MAX_VALUE);
        viewPanels[3].setAlignment(Pos.CENTER);
        viewPanels[3].setText("\n\n[WAITING FOR TIMELINE DATA...]\n\nHistorical playback\nof recorded actions\n\n");
        viewPanels[3].setWrapText(true);
        
        VBox.setVgrow(viewPanels[3], Priority.ALWAYS);
        rightPanel.getChildren().addAll(rightTitle, viewPanels[3]);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        
        // Initialize unused panels (3P, MAP) to avoid NullPointerException
        viewPanels[1] = new Label("[Third-Person - Not displayed]");
        viewPanels[2] = new Label("[Map View - Not displayed]");
        
        mainContent.getChildren().addAll(leftPanel, rightPanel);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        container.getChildren().addAll(title, mainContent);
        return container;
    }


    /**
     * Create video frame panel (ready for client mod video streaming)
     */
    private VBox createVideoFramePanel() {
        VBox panel = new VBox(10);
        panel.setStyle(
            "-fx-background-color: #1a2332; " +
            "-fx-border-color: " + ACCENT_BLUE + "; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5; " +
            "-fx-padding: 15;"
        );
        
        Label title = new Label("▣ CLIENT VIDEO FEED (REQUIRES MOD)");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        title.setTextFill(Color.web(ACCENT_BLUE));
        
        Label placeholder = new Label();
        placeholder.setStyle(
            "-fx-background-color: #0a0e14; " +
            "-fx-border-color: #2d3748; " +
            "-fx-border-width: 1; " +
            "-fx-padding: 20; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 12px; " +
            "-fx-text-fill: " + TEXT_SECONDARY + ";"
        );
        placeholder.setMaxWidth(Double.MAX_VALUE);
        placeholder.setMaxHeight(Double.MAX_VALUE);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setText(
            "╔════════════════════════════════════════════════════════════╗\n" +
            "║                                                            ║\n" +
            "║              ⚠️ VIDEO STREAM NOT AVAILABLE                 ║\n" +
            "║                                                            ║\n" +
            "║   This section will display real-time video when:         ║\n" +
            "║                                                            ║\n" +
            "║   1. Client-side mod is installed (Forge/Fabric)          ║\n" +
            "║   2. Mod captures NPC perspective                          ║\n" +
            "║   3. Frames are sent via VIDEO: telemetry                  ║\n" +
            "║                                                            ║\n" +
            "║   Currently using: RAYTRACING TEXT (above)                 ║\n" +
            "║                                                            ║\n" +
            "╚════════════════════════════════════════════════════════════╝"
        );
        
        VBox.setVgrow(placeholder, Priority.ALWAYS);
        panel.getChildren().addAll(title, placeholder);
        
        return panel;
    }

    private void log(String category, String message) {
        Platform.runLater(() -> {
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            String formatted = String.format("[%s] %s >> %s\n", timestamp, category, message);
            neuralLogArea.appendText(formatted);

            neuralLogArea.setScrollTop(Double.MAX_VALUE);

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

    /**
     * Update any view panel with new frame data by panel index
     * Also tracks and displays frame rate (FPS) for each view
     */
    private void updateViewFrame(int panelIndex, String base64Data) {
        log("TRACE", "updateViewFrame called: panel=" + panelIndex + ", dataLen=" + (base64Data != null ? base64Data.length() : 0));
        
        Platform.runLater(() -> {
            try {
                if (base64Data == null || base64Data.isEmpty()) {
                    log("WARN", "Panel " + panelIndex + ": Empty base64 data received");
                    return;
                }
                
                log("TRACE", "Panel " + panelIndex + ": Starting decode of " + base64Data.length() + " chars");
                
                // Calculate frame rate
                long now = System.currentTimeMillis();
                long timeDelta = now - panelFrameTimes[panelIndex];
                panelFrameTimes[panelIndex] = now;
                panelFrameCount[panelIndex]++;
                
                if (timeDelta > 0) {
                    panelFPS[panelIndex] = 1000.0 / timeDelta;  // Convert to FPS
                }
                
                // Decode Base64 to bytes
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                log("TRACE", "Panel " + panelIndex + ": Base64 decoded to " + imageBytes.length + " bytes");
                
                // Convert bytes to JavaFX Image
                ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                Image image = new Image(bais);
                log("TRACE", "Panel " + panelIndex + ": Image created: " + image.getWidth() + "x" + image.getHeight());
                
                if (image.getWidth() == 0 || image.getHeight() == 0) {
                    log("ERROR", "Panel " + panelIndex + ": Image has zero dimensions!");
                    return;
                }
                
                // Create ImageView and set image
                ImageView imageView = new ImageView(image);
                imageView.setPreserveRatio(true);
                // Increase size for first-person POV panel (panel 0)
                if (panelIndex == 0) {
                    imageView.setFitWidth(600);
                    imageView.setFitHeight(450);
                } else {
                    imageView.setFitWidth(300);
                    imageView.setFitHeight(250);
                }
                
                // Format frame statistics
                String frameStats = String.format(
                    "Frame #%d | FPS: %.1f | Δ%d ms",
                    panelFrameCount[panelIndex],
                    panelFPS[panelIndex],
                    timeDelta
                );
                
                // Update the appropriate view panel
                if (panelIndex >= 0 && panelIndex < viewPanels.length) {
                    log("TRACE", "Panel " + panelIndex + ": viewPanels[" + panelIndex + "] = " + (viewPanels[panelIndex] != null ? "NOT NULL" : "NULL!"));
                    
                    if (viewPanels[panelIndex] == null) {
                        log("ERROR", "Panel " + panelIndex + ": viewPanels[" + panelIndex + "] is NULL - panel was never initialized!");
                        return;
                    }
                    
                    // Create a container with the image and stats
                    VBox panelContent = new VBox(5);
                    panelContent.setStyle("-fx-alignment: top-left; -fx-padding: 5;");
                    
                    // Stats label at top
                    Label statsLabel = new Label(frameStats);
                    statsLabel.setFont(Font.font("Consolas", 9));
                    statsLabel.setTextFill(Color.web(ACCENT_GREEN));
                    statsLabel.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-padding: 4; -fx-text-fill: #2ea043;");
                    
                    // Image
                    StackPane imageContainer = new StackPane(imageView);
                    imageContainer.setStyle("-fx-background-color: #0a0e14;");
                    VBox.setVgrow(imageContainer, Priority.ALWAYS);
                    
                    panelContent.getChildren().addAll(statsLabel, imageContainer);
                    
                    viewPanels[panelIndex].setGraphic(panelContent);
                    viewPanels[panelIndex].setText("");
                    
                    // Highlight with green border to show active stream
                    String borderColor = ACCENT_GREEN;
                    viewPanels[panelIndex].setStyle(
                        "-fx-background-color: #0a0e14; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-width: 2; " +
                        "-fx-padding: 0;"
                    );
                    
                    log("RENDER", "✓ Panel " + panelIndex + " rendered successfully (" + frameStats + ")");
                } else {
                    log("ERROR", "Panel " + panelIndex + ": Invalid panel index (valid: 0-3)");
                }
                
            } catch (IllegalArgumentException e) {
                log("ERROR", "Panel " + panelIndex + ": Invalid base64 - " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                log("ERROR", "Panel " + panelIndex + ": Exception during render - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Update video frame from Base64 encoded JPEG data (legacy support)
     */
    private void updateVideoFrame(String base64Data) {
        // Route to first-person view by default
        updateViewFrame(0, base64Data);
    }

    /**
     * Test rendering pipeline - creates a simple colored image and renders it to Panel 0
     */
    private void testRenderPipeline() {
        new Thread(() -> {
            try {
                Thread.sleep(2000);  // Wait for UI to fully render
                
                log("TEST", "Creating red test image (240x180)...");
                java.awt.image.BufferedImage testImg = new java.awt.image.BufferedImage(240, 180, java.awt.image.BufferedImage.TYPE_INT_RGB);
                
                // Fill with red
                for (int y = 0; y < 180; y++) {
                    for (int x = 0; x < 240; x++) {
                        testImg.setRGB(x, y, 0xFF0000);  // Red
                    }
                }
                
                // Add white border
                for (int x = 0; x < 240; x++) {
                    testImg.setRGB(x, 0, 0xFFFFFF);
                    testImg.setRGB(x, 179, 0xFFFFFF);
                }
                for (int y = 0; y < 180; y++) {
                    testImg.setRGB(0, y, 0xFFFFFF);
                    testImg.setRGB(239, y, 0xFFFFFF);
                }
                
                // Add text: "TEST"
                java.awt.Graphics2D g2d = testImg.createGraphics();
                g2d.setColor(java.awt.Color.WHITE);
                g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 48));
                java.awt.FontMetrics fm = g2d.getFontMetrics();
                String text = "TEST";
                int x = (240 - fm.stringWidth(text)) / 2;
                int y = (180 + fm.getAscent()) / 2;
                g2d.drawString(text, x, y);
                g2d.dispose();
                
                log("TEST", "Encoding test image to JPEG...");
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(testImg, "jpg", baos);
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                
                log("TEST", "Test image encoded: " + base64.length() + " chars");
                log("TEST", "Calling updateViewFrame(0, base64) directly...");
                
                updateViewFrame(0, base64);
                
                log("TEST", "=== END RENDERING TEST ===");
                
            } catch (Exception e) {
                log("TEST_ERROR", "Test frame failed: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Update goal steps visualization from JSON
     * Format: [{"id":"...", "label":"...", "status":"PENDING|IN_PROGRESS|COMPLETED", "dependsOn":[...]}]
     */
    private void updateGoalSteps(String stepsJson) {
        Platform.runLater(() -> {
            try {
                log("STEPS", "Parsing steps JSON: " + stepsJson.substring(0, Math.min(100, stepsJson.length())) + "...");
                
                if (stepGraphVisualizer == null) {
                    log("ERROR", "stepGraphVisualizer not initialized!");
                    return;
                }
                
                // Parse JSON to StepData objects
                List<StepGraphVisualizer.StepData> stepDataList = parseStepsJson(stepsJson);
                
                if (stepDataList.isEmpty()) {
                    log("WARN", "No steps parsed from JSON");
                    return;
                }
                
                log("STEPS", "✓ Parsed " + stepDataList.size() + " steps, rendering graph...");
                
                // Update graph visualization
                stepGraphVisualizer.setSteps(stepDataList);
                
                log("STEPS", "✓ Step graph rendered with " + stepDataList.size() + " nodes");
                
            } catch (Exception e) {
                log("ERROR", "Failed to update goal steps: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Parse JSON array of steps into StepData objects
     */
    private List<StepGraphVisualizer.StepData> parseStepsJson(String stepsJson) {
        List<StepGraphVisualizer.StepData> stepDataList = new ArrayList<>();
        
        try {
            String trimmed = stepsJson.trim();
            if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
            if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
            
            String[] steps = trimmed.split("\\},\\{");
            
            for (String step : steps) {
                step = step.replace("{", "").replace("}", "");
                
                String id = extractJsonValue(step, "id");
                String label = extractJsonValue(step, "label");
                String status = extractJsonValue(step, "status");
                List<String> dependsOn = extractJsonArray(step, "dependsOn");
                
                stepDataList.add(new StepGraphVisualizer.StepData(id, label, status, dependsOn));
            }
        } catch (Exception e) {
            log("ERROR", "JSON parsing error: " + e.getMessage());
        }
        
        return stepDataList;
    }
    
    /**
     * Extract array value from JSON string
     */
    private List<String> extractJsonArray(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            int keyIndex = json.indexOf("\"" + key + "\"");
            if (keyIndex == -1) return result;
            
            int colonIndex = json.indexOf(":", keyIndex);
            int startIndex = json.indexOf("[", colonIndex);
            int endIndex = json.indexOf("]", startIndex);
            
            if (startIndex > 0 && endIndex > startIndex) {
                String arrayContent = json.substring(startIndex + 1, endIndex);
                if (!arrayContent.trim().isEmpty()) {
                    String[] items = arrayContent.split(",");
                    for (String item : items) {
                        String cleaned = item.trim().replace("\"", "");
                        if (!cleaned.isEmpty()) {
                            result.add(cleaned);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors for array
        }
        return result;
    }
    
    private String extractJsonValue(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex == -1) return "";
        
        int colonIndex = json.indexOf(":", keyIndex);
        int startIndex = json.indexOf("\"", colonIndex) + 1;
        int endIndex = json.indexOf("\"", startIndex);
        
        if (startIndex > 0 && endIndex > startIndex) {
            return json.substring(startIndex, endIndex);
        }
        return "";
    }

    public static void main(String[] args) {
        launch(args);
    }
}

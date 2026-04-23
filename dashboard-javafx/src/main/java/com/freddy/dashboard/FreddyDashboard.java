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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextField;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.collections.FXCollections;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.freddy.dashboard.graph.StepGraphVisualizer;

/**
 * Professional Scientific Dashboard for Freddy AI
 * Real-time neural monitoring with live POV visualization
 */
public class FreddyDashboard extends Application {

    private TextArea povTextArea;
    private Label[] viewPanels = new Label[4];
    private TextArea neuralLogArea;
    private TextArea decisionArea;
    private Label latencyValue;
    private Label frequencyValue;
    private Label cyclesValue;
    private Label successValue;
    private Label positionValue;
    private Label entitiesValue;
    private Label statusLabel;
    private StepGraphVisualizer stepGraphVisualizer;
    private Canvas travelMapCanvas;
    private GraphicsContext travelMapGc;
    private final Deque<TravelPoint> travelTrail = new ArrayDeque<>();
    private Label travelMapSummary;
    private GridPane inventoryGrid;
    private TilePane inventoryTiles;
    private final java.util.List<Label> inventorySlots = new java.util.ArrayList<>();
    private final Map<String, Image> inventoryIconCache = new HashMap<>();
    private Label inventoryItemCountLabel;
    private Label inventoryCapacityLabel;
    private Label inventoryWeightLabel;
    private ComboBox<String> goalSelectorControl;
    private ComboBox<String> actionSelectorControl;
    private ComboBox<String> playerSelectorControl;
    private ComboBox<String> itemSelectorControl;
    private TextField amountFieldControl;
    private ComboBox<String> goalBuildTemplateSelector;
    private ComboBox<String> goalCraftItemSelector;
    private TextField goalCraftAmountField;
    private ComboBox<String> goalFightMobSelector;
    private Label goalStatusControl;
    private Label actionStatusControl;
    private Label missionSnapshotLabel;
    private final List<String> knownPlayers = new ArrayList<>();
    private String currentGoal = "none";
    private String currentAction = "none";
    private String currentPosition = "unknown";
    private String currentPlayers = "unknown";
    private String currentMissionMode = "AI MISSION";
    private String goalCatalogState = "unknown";
    private String advancedFeaturesState = "unknown";
    private String goalQueueState = "unknown";
    private String workflowSafetyState = "unknown";
    private String advancedWorldState = "unknown";
    private long totalActions = 0;
    private long completedSteps = 0;

    private final Label[] stateSymbols = new Label[4];
    private final Label[] stateLabels = new Label[4];

    private volatile boolean running = true;
    private static final int DASHBOARD_PORT = 25566;
    private long tickCounter = 0;
    private long lastTickTime = System.currentTimeMillis();
    private static final boolean TEST_MODE = false;

    private final long[] panelFrameTimes = new long[4];
    private final int[] panelFrameCount = new int[4];
    private final double[] panelFPS = new double[4];

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
        
        Tab viewsTab = new Tab("◈ TRAVEL MAP");
        viewsTab.setClosable(false);
        viewsTab.setContent(createMultiViewPanel());
        
        Tab inventoryTab = new Tab("◈ NPC INVENTORY");
        inventoryTab.setClosable(false);
        inventoryTab.setContent(createInventoryPanel());
        
        Tab goalsTab = new Tab("◈ GOALS & ACTIONS");
        goalsTab.setClosable(false);
        goalsTab.setContent(createGoalsPanel());
        
        tabPane.getTabs().addAll(missionTab, viewsTab, inventoryTab, goalsTab);
        return tabPane;
    }

    private VBox createActionsPanel() {
        return createGoalsPanel();
    }
    
    private VBox createInventoryPanel() {
        VBox inventoryPanel = new VBox(15);
        inventoryPanel.setPadding(new Insets(20));
        inventoryPanel.setStyle("-fx-background-color: " + BG_PRIMARY + ";");
        
        Label title = new Label("◈ NPC INVENTORY");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(ACCENT_BLUE));
        
        // Fixed responsive grid: 9 columns x 4 rows (all 36 slots visible, no scroll)
        inventoryGrid = new GridPane();
        inventoryGrid.setHgap(10);
        inventoryGrid.setVgap(10);
        inventoryGrid.setPadding(new Insets(15));
        inventoryGrid.setStyle("-fx-background-color: #1c2128; -fx-border-color: " + ACCENT_BLUE + "; -fx-border-width: 1;");

        inventoryGrid.getColumnConstraints().clear();
        for (int c = 0; c < 9; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 9.0);
            col.setHgrow(Priority.ALWAYS);
            inventoryGrid.getColumnConstraints().add(col);
        }

        inventoryGrid.getRowConstraints().clear();
        for (int r = 0; r < 4; r++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(100.0 / 4.0);
            row.setVgrow(Priority.ALWAYS);
            inventoryGrid.getRowConstraints().add(row);
        }
        
        // Create inventory slots
        inventorySlots.clear();
        for (int i = 0; i < 36; i++) {
            Label slot = new Label("[ ]");
            slot.setPrefWidth(124);
            slot.setPrefHeight(124);
            slot.setMinWidth(80);
            slot.setMinHeight(80);
            slot.setMaxWidth(Double.MAX_VALUE);
            slot.setMaxHeight(Double.MAX_VALUE);
            slot.setWrapText(true);
            slot.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
            slot.setGraphicTextGap(10);
            slot.setStyle(
                "-fx-background-color: #0f1419; " +
                "-fx-border-color: #2b3d47; " +
                "-fx-border-width: 1; " +
                "-fx-text-fill: " + TEXT_SECONDARY + "; " +
                "-fx-font-family: 'Consolas'; " +
                "-fx-font-size: 13px; " +
                "-fx-alignment: center;"
            );
            GridPane.setHgrow(slot, Priority.ALWAYS);
            GridPane.setVgrow(slot, Priority.ALWAYS);
            inventoryGrid.add(slot, i % 9, i / 9);
            inventorySlots.add(slot);
        }
        VBox.setVgrow(inventoryGrid, Priority.ALWAYS);
        
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
        
        inventoryPanel.getChildren().addAll(title, inventoryGrid, statsBox);
        return inventoryPanel;
    }
    
    private VBox createGoalsPanel() {
        VBox root = new VBox();
        root.setStyle("-fx-background-color: " + BG_PRIMARY + ";");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: " + BG_PRIMARY + "; -fx-background-color: " + BG_PRIMARY + ";");

        VBox goalsPanel = new VBox(20);
        goalsPanel.setPadding(new Insets(20));
        goalsPanel.setStyle("-fx-background-color: " + BG_PRIMARY + ";");
        
        Label title = new Label("◈ UNIFIED AI MISSION SYSTEM");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(ACCENT_GREEN));
        
        Label description = new Label("Choose a goal and execute it. The dashboard runs in a single AI mission mode.");
        description.setFont(Font.font("Consolas", 12));
        description.setTextFill(Color.web(TEXT_PRIMARY));

        Label missionModeLabel = new Label("Mode: AI MISSION");
        missionModeLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
        missionModeLabel.setTextFill(Color.web(ACCENT_YELLOW));
        
        goalSelectorControl = new ComboBox<>();
        goalSelectorControl.setPromptText("Choose Goal...");
        goalSelectorControl.getItems().addAll(
            "🎯 FOLLOW PLAYER",
            "💎 MINE DIAMONDS",
            "🌳 GATHER WOOD",
            "🪨 GATHER STONE",
            "⚔️ HUNT MOBS",
            "🤖 AUTOPILOT",
            "🏠 BUILD STRUCTURE",
            "↩️ RETURN TO PLAYER",
            "🛡️ PROTECT PLAYER",
            "🛠️ CRAFT ITEM",
            "⚔️ FIGHT MOB",
            "🚀 SPEEDRUN"
        );
        goalSelectorControl.setPrefWidth(400);
        goalSelectorControl.setMaxWidth(Double.MAX_VALUE);
        goalSelectorControl.setStyle(
            "-fx-background-color: #1c2128; " +
            "-fx-text-fill: " + TEXT_PRIMARY + "; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 14px;"
        );

        goalBuildTemplateSelector = new ComboBox<>();
        goalBuildTemplateSelector.setPromptText("Select Structure Template...");
        goalBuildTemplateSelector.getItems().addAll("HOUSE_6X6", "WALL_10", "HUT_4X4", "TOWER_7", "PILLAR_SMALL");
        goalBuildTemplateSelector.setValue("HOUSE_6X6");
        goalBuildTemplateSelector.setPrefWidth(240);

        goalCraftItemSelector = new ComboBox<>();
        goalCraftItemSelector.setPromptText("Select Item to Craft...");
        goalCraftItemSelector.getItems().addAll(
            "WOODEN_SWORD", "WOODEN_PICKAXE", "WOODEN_AXE", "WOODEN_SHOVEL",
            "STONE_SWORD", "STONE_PICKAXE", "STONE_AXE", "STONE_SHOVEL", "CHEST",
            "FURNACE", "CRAFTING_TABLE"
            );
        goalCraftItemSelector.setValue("WOODEN_SWORD");
        goalCraftItemSelector.setPrefWidth(240);

        goalCraftAmountField = new TextField("1");
        goalCraftAmountField.setPromptText("Qty");
        goalCraftAmountField.setPrefWidth(90);
        goalCraftAmountField.setMinWidth(80);

        goalFightMobSelector = new ComboBox<>();
        goalFightMobSelector.setPromptText("Select Mob...");
        goalFightMobSelector.getItems().addAll("ZOMBIE", "SKELETON", "SPIDER", "BREEZE", "IRON_GOLEM", "WITHER");
        goalFightMobSelector.setValue("ZOMBIE");
        goalFightMobSelector.setPrefWidth(180);

        HBox goalOptionsRow = new HBox(10, goalBuildTemplateSelector, goalCraftItemSelector, goalCraftAmountField, goalFightMobSelector);
        goalOptionsRow.setAlignment(Pos.CENTER_LEFT);
        

        Button activateButton = new Button("► ACTIVATE GOAL");
        activateButton.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        activateButton.setStyle(
            "-fx-background-color: " + ACCENT_GREEN + "; " +
            "-fx-text-fill: #0a0e14; " +
            "-fx-padding: 10 20;"
        );
        
        goalStatusControl = new Label("⚠️ GOAL SYSTEM: READY");
        goalStatusControl.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        goalStatusControl.setTextFill(Color.web(ACCENT_YELLOW));

        Button clearGoalButton = new Button("✖ CLEAR GOAL");
        clearGoalButton.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        clearGoalButton.setStyle(
            "-fx-background-color: #da3633; " +
            "-fx-text-fill: #e6edf3; " +
            "-fx-padding: 8 12;"
        );
        
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
        
        Label stepsTitle = new Label("📋 CONCRETE GOAL PLAN");
        stepsTitle.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        stepsTitle.setTextFill(Color.web(ACCENT_BLUE));
        
        // Graph visualizer with nodes and edges
        stepGraphVisualizer = new StepGraphVisualizer(800, 500);
        stepGraphVisualizer.setStyle("-fx-background-color: #0a0e14;");
        stepGraphVisualizer.setMinHeight(320);
        stepGraphVisualizer.setMaxWidth(Double.MAX_VALUE);
        stepGraphVisualizer.prefWidthProperty().bind(stepsContainer.widthProperty().subtract(30));
        
        stepsContainer.getChildren().addAll(stepsTitle, stepGraphVisualizer);
        VBox.setVgrow(stepsContainer, Priority.ALWAYS);
        
        activateButton.setOnAction(e -> {
            String selectedGoal = goalSelectorControl.getValue();
            if (selectedGoal != null && !selectedGoal.isEmpty()) {
                log("GOAL", "Button clicked: " + selectedGoal);
                goalStatusControl.setText("✅ GOAL ACTIVATED: " + selectedGoal);
                goalStatusControl.setTextFill(Color.web(ACCENT_GREEN));
                currentGoal = selectedGoal;
                currentMissionMode = "AI MISSION";
                updateMissionSnapshot();
                
                // Show loader while planning steps
                if (stepGraphVisualizer != null) {
                    stepGraphVisualizer.setLoading(true);
                }
                
                // Send goal to plugin via command socket
                sendGoalCommand(buildGoalPayload(selectedGoal));
                log("GOAL", "Command sent to plugin");
            } else {
                log("WARN", "No goal selected!");
                goalStatusControl.setText("⚠️ Please select a goal first");
                goalStatusControl.setTextFill(Color.web(ACCENT_YELLOW));
            }
        });

        clearGoalButton.setOnAction(e -> {
            sendRawCommand("GOAL_CLEAR_FORCE");
            currentGoal = "none";
            updateMissionSnapshot();
            goalStatusControl.setText("⚠️ GOAL CLEARED");
            goalStatusControl.setTextFill(Color.web(ACCENT_YELLOW));
        });

        HBox goalPromptRow = new HBox(10, clearGoalButton);
        goalPromptRow.setAlignment(Pos.CENTER_LEFT);

        actionSelectorControl = new ComboBox<>();
        actionSelectorControl.setPromptText("Choose Action...");
        actionSelectorControl.getItems().addAll(
            "👁️ OBSERVE",
            "🌪️ WANDER",
            "🧘 IDLE",
            "⬆️ JUMP",
            "🧭 LOOK AT",
            "🚶 MOVE TO",
            "🏠 RETURN HOME",
            "👣 FOLLOW PLAYER",
            "🛡️ PROTECT PLAYER",
            "🛠️ CRAFT ITEM",
            "⛏️ MINE NEARBY",
            "🔨 BREAK BLOCK",
            "🧱 PLACE BLOCK",
            "⚔️ ATTACK NEARBY",
            "🛡️ SHIELD",
            "↩️ DODGE",
            "💬 CHAT",
            "🙂 SAY HELLO",
            "👋 SAY GOODBYE",
            "❔ CONFUSED",
            "🧺 PICKUP ITEMS"
        );
        actionSelectorControl.setPrefWidth(400);
        actionSelectorControl.setMaxWidth(Double.MAX_VALUE);
        actionSelectorControl.setStyle(
            "-fx-background-color: #1c2128; " +
            "-fx-text-fill: " + TEXT_PRIMARY + "; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 14px;"
        );


        playerSelectorControl = new ComboBox<>();
        playerSelectorControl.setPromptText("Select Player...");
        playerSelectorControl.setPrefWidth(240);
        playerSelectorControl.setMaxWidth(Double.MAX_VALUE);
        playerSelectorControl.setItems(FXCollections.observableArrayList());

        itemSelectorControl = new ComboBox<>();
        itemSelectorControl.setPromptText("Select Item to Craft...");
        itemSelectorControl.setPrefWidth(240);
        itemSelectorControl.getItems().addAll(
            "WOODEN_SWORD", "STONE_PICKAXE", "BREAD", "TORCH", "CHEST", "FURNACE",
            "CRAFTING_TABLE", "OAK_PLANKS", "COBBLESTONE", "STONE", "OAK_LOG"
        );

        TextField targetField = new TextField();
        targetField.setPromptText("Optional custom target override");
        targetField.setPrefWidth(300);
        targetField.setMaxWidth(Double.MAX_VALUE);

        amountFieldControl = new TextField("1");
        amountFieldControl.setPromptText("Amount");
        amountFieldControl.setPrefWidth(100);
        amountFieldControl.setMinWidth(90);
        amountFieldControl.setMaxWidth(120);

        Button executeActionButton = new Button("► EXECUTE ACTION");
        executeActionButton.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        executeActionButton.setStyle(
            "-fx-background-color: " + ACCENT_BLUE + "; " +
            "-fx-text-fill: #0a0e14; " +
            "-fx-padding: 10 20;"
        );

        actionStatusControl = new Label("⚠️ ACTION SYSTEM: READY");
        actionStatusControl.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        actionStatusControl.setTextFill(Color.web(ACCENT_YELLOW));

        executeActionButton.setOnAction(e -> {
            String selectedAction = actionSelectorControl.getValue();
            if (selectedAction == null || selectedAction.isEmpty()) {
                actionStatusControl.setText("⚠️ Please select an action first");
                actionStatusControl.setTextFill(Color.web(ACCENT_YELLOW));
                return;
            }

            String payload = toActionCommand(
                selectedAction,
                playerSelectorControl.getValue(),
                itemSelectorControl.getValue(),
                amountFieldControl.getText(),
                targetField.getText()
            );
            if (payload == null) {
                actionStatusControl.setText("⚠️ Required selection missing (player/item)");
                actionStatusControl.setTextFill(Color.web(ACCENT_YELLOW));
                return;
            }

            log("ACTION", "Sending: " + payload);
            actionStatusControl.setText("✅ Sent: " + payload);
            actionStatusControl.setTextFill(Color.web(ACCENT_GREEN));
            currentAction = selectedAction;
            currentMissionMode = "DIRECT ACTION";
            updateMissionSnapshot();
            sendActionCommand(payload);
        });

        HBox actionRow = new HBox(10, playerSelectorControl, itemSelectorControl, amountFieldControl, targetField, executeActionButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(targetField, Priority.ALWAYS);

        VBox actionHints = new VBox(8,
            new Label("Examples:"),
            new Label("- OBSERVE / WANDER / RETURN HOME"),
            new Label("- FOLLOW PLAYER + Steve"),
            new Label("- PROTECT PLAYER + Steve"),
            new Label("- CRAFT ITEM + WOODEN_SWORD + 1")
        );
        for (var node : actionHints.getChildren()) {
            if (node instanceof Label label) {
                label.setFont(Font.font("Consolas", 12));
                label.setTextFill(Color.web(TEXT_SECONDARY));
            }
        }
        
        goalsPanel.getChildren().addAll(
            title, 
            description, 
            missionModeLabel,
            goalSelectorControl,
            goalOptionsRow,
            activateButton, 
            goalStatusControl,
            goalPromptRow,
            stepsContainer
        );

        goalSelectorControl.setOnAction(e -> {
            String selected = goalSelectorControl.getValue() == null ? "" : goalSelectorControl.getValue().toUpperCase();
            boolean buildGoal = selected.contains("BUILD STRUCTURE");
            boolean craftGoal = selected.contains("CRAFT ITEM");
            boolean fightGoal = selected.contains("FIGHT MOB");

            goalBuildTemplateSelector.setManaged(buildGoal);
            goalBuildTemplateSelector.setVisible(buildGoal);
            goalCraftItemSelector.setManaged(craftGoal);
            goalCraftItemSelector.setVisible(craftGoal);
            goalCraftAmountField.setManaged(craftGoal);
            goalCraftAmountField.setVisible(craftGoal);
            goalFightMobSelector.setManaged(fightGoal);
            goalFightMobSelector.setVisible(fightGoal);
        });
        goalSelectorControl.getOnAction().handle(null);

        actionSelectorControl.setOnAction(e -> {
            String selected = actionSelectorControl.getValue() == null ? "" : actionSelectorControl.getValue().toUpperCase();
            boolean needsPlayer = selected.contains("FOLLOW PLAYER")
                || selected.contains("PROTECT PLAYER")
                || selected.contains("SAY HELLO")
                || selected.contains("SAY GOODBYE")
                || selected.contains("LOOK AT");
            boolean needsItem = selected.contains("CRAFT ITEM") || selected.contains("PLACE BLOCK");

            playerSelectorControl.setDisable(!needsPlayer);
            itemSelectorControl.setDisable(!needsItem);
            amountFieldControl.setDisable(!needsItem);
        });
        actionSelectorControl.getOnAction().handle(null);

        scrollPane.setContent(goalsPanel);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.getChildren().add(scrollPane);
        return root;
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

    private void sendRawCommand(String command) {
        new Thread(() -> {
            try {
                Socket socket = new Socket("127.0.0.1", 25567);
                java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
                log("CMD", "Sending command: " + command);
                out.println(command);
                out.flush();
                out.close();
                socket.close();
            } catch (Exception e) {
                log("ERROR", "Failed to send command: " + command + " - " + e.getMessage());
            }
        }, "RawCommandSender").start();
    }

    private void sendActionCommand(String command) {
        new Thread(() -> {
            try {
                log("CMD", "Connecting to plugin command server at 127.0.0.1:25567...");
                Socket socket = new Socket("127.0.0.1", 25567);
                java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);

                log("CMD", "Sending command: " + command);
                out.println(command);
                out.flush();

                log("CMD", "✓ Action command sent successfully");

                out.close();
                socket.close();

            } catch (java.net.ConnectException e) {
                log("ERROR", "Cannot connect to plugin (port 25567): Is Minecraft server running with plugin?");
            } catch (Exception e) {
                log("ERROR", "Failed to send action command: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, "ActionCommandSender").start();
    }

    private String toGoalToken(String uiGoal) {
        if (uiGoal == null) {
            return "EXPLORE_AREA";
        }
        String value = uiGoal.toUpperCase();
        if (value.contains("FOLLOW PLAYER")) return "FOLLOW_PLAYER";
        if (value.contains("MINE DIAMONDS")) return "MINE_DIAMONDS";
        if (value.contains("GATHER WOOD")) return "GATHER_WOOD";
        if (value.contains("GATHER STONE")) return "GATHER_STONE";
        if (value.contains("HUNT MOBS")) return "HUNT_ANIMALS";
        if (value.contains("BUILD STRUCTURE")) return "BUILD_STRUCTURE";
        if (value.contains("AUTOPILOT")) return "AUTOPILOT";
        if (value.contains("RETURN TO PLAYER")) return "RETURN_TO_PLAYER";
        if (value.contains("PROTECT PLAYER")) return "PROTECT_PLAYER";
        if (value.contains("CRAFT ITEM") || value.contains("CREATE ITEM")) return "CREATE_ITEM";
        if (value.contains("FIGHT MOB")) return "FIGHT_MOB";
        if (value.contains("SPEEDRUN")) return "SPEEDRUN";
        return "EXPLORE_AREA";
    }

    private String buildGoalPayload(String uiGoal) {
        String token = toGoalToken(uiGoal);
        
        int amount = 1;
        try {
            amount = Math.max(1, Integer.parseInt(goalCraftAmountField == null ? "1" : goalCraftAmountField.getText().trim()));
        } catch (Exception ignore) {
            amount = 1;
        }

        if ("BUILD_STRUCTURE".equalsIgnoreCase(token)) {
            String template = goalBuildTemplateSelector == null ? "HOUSE_6X6" : goalBuildTemplateSelector.getValue();
            if (template == null || template.isBlank()) {
                template = "HOUSE_6X6";
            }
            return "BUILD_STRUCTURE:" + template.trim().toUpperCase();
        }

        if ("CREATE_ITEM".equalsIgnoreCase(token)) {
            String item = goalCraftItemSelector == null ? "WOODEN_SWORD" : goalCraftItemSelector.getValue();
            if (item == null || item.isBlank()) {
                item = "WOODEN_SWORD";
            }
            return "CREATE_ITEM:" + item.trim().toUpperCase() + ":" + amount;
        }

        if ("FIGHT_MOB".equalsIgnoreCase(token)) {
            String mob = goalFightMobSelector == null ? "ZOMBIE" : goalFightMobSelector.getValue();
            if (mob == null || mob.isBlank()) {
                mob = "ZOMBIE";
            }
            return "FIGHT_MOB:" + mob.trim().toUpperCase();
        }

        // Add amount parameters to gather goals
        if (List.of("GATHER_WOOD", "GATHER_STONE", "MINE_DIAMONDS", "HUNT_ANIMALS").contains(token)) {
            return token + ":" + amount;
        }

        return token;
    }

    private String toActionCommand(String uiAction, String selectedPlayer, String selectedItem, String amountRaw, String customTarget) {
        if (uiAction == null) {
            return null;
        }

        String normalized = uiAction.toUpperCase();
        String override = customTarget == null ? "" : customTarget.trim();
        String player = (override.isEmpty() ? selectedPlayer : override);

        if (normalized.contains("OBSERVE")) {
            return "OBSERVE";
        }
        if (normalized.contains("WANDER")) {
            return "ACTION:WANDER";
        }
        if (normalized.contains("IDLE")) {
            return "ACTION:IDLE";
        }
        if (normalized.contains("JUMP")) {
            return "ACTION:JUMP";
        }
        if (normalized.contains("LOOK AT")) {
            if (player != null && !player.isBlank()) {
                return "ACTION:LOOK_AT:" + player.trim();
            }
            return override.isEmpty() ? "ACTION:LOOK_AT" : "ACTION:LOOK_AT:" + override;
        }
        if (normalized.contains("MOVE TO")) {
            if (override.isEmpty()) {
                return null;
            }
            return "ACTION:MOVE_TO:" + override;
        }
        if (normalized.contains("RETURN HOME")) {
            return "RETURN_HOME";
        }
        if (normalized.contains("FOLLOW PLAYER")) {
            if (player == null || player.isBlank()) {
                return null;
            }
            return "ACTION:FOLLOW:" + player.trim();
        }
        if (normalized.contains("PROTECT PLAYER")) {
            if (player == null || player.isBlank()) {
                return null;
            }
            return "ACTION:PROTECT:" + player.trim();
        }
        if (normalized.contains("MINE NEARBY")) {
            return "ACTION:MINE_NEARBY";
        }
        if (normalized.contains("BREAK BLOCK")) {
            return override.isEmpty() ? "ACTION:BREAK_BLOCK" : "ACTION:BREAK_BLOCK:" + override;
        }
        if (normalized.contains("PLACE BLOCK")) {
            if (selectedItem != null && !selectedItem.isBlank()) {
                return override.isEmpty() ? "ACTION:PLACE_BLOCK:" + selectedItem.trim() : "ACTION:PLACE_BLOCK:" + selectedItem.trim() + ":" + override;
            }
            return null;
        }
        if (normalized.contains("ATTACK NEARBY")) {
            return "ACTION:ATTACK_NEARBY";
        }
        if (normalized.contains("SHIELD")) {
            return "ACTION:SHIELD";
        }
        if (normalized.contains("DODGE")) {
            return "ACTION:DODGE";
        }
        if (normalized.contains("CHAT")) {
            return override.isEmpty() ? "ACTION:CHAT:Hello!" : "ACTION:CHAT:" + override;
        }
        if (normalized.contains("SAY HELLO")) {
            if (player == null || player.isBlank()) {
                return null;
            }
            return "ACTION:SAY_HELLO:" + player.trim();
        }
        if (normalized.contains("SAY GOODBYE")) {
            if (player == null || player.isBlank()) {
                return null;
            }
            return "ACTION:SAY_GOODBYE:" + player.trim();
        }
        if (normalized.contains("CONFUSED")) {
            return "ACTION:CONFUSED";
        }
        if (normalized.contains("PICKUP ITEMS")) {
            return "ACTION:PICKUP_ITEMS";
        }
        if (normalized.contains("CRAFT ITEM")) {
            String item = (override.isEmpty() ? selectedItem : override);
            if (item == null || item.isBlank()) {
                return null;
            }
            int amount = 1;
            try {
                amount = Math.max(1, Integer.parseInt(amountRaw == null ? "1" : amountRaw.trim()));
            } catch (NumberFormatException ignore) {
                amount = 1;
            }
            return "CRAFT:" + item.trim() + ":" + amount;
        }

        return null;
    }

    private void updatePlayerSelector(String playersRaw) {
        List<String> parsed = parsePlayerList(playersRaw);
        knownPlayers.clear();
        knownPlayers.addAll(parsed);

        if (playerSelectorControl != null) {
            String selected = playerSelectorControl.getValue();
            playerSelectorControl.setItems(FXCollections.observableArrayList(parsed));
            if (selected != null && parsed.contains(selected)) {
                playerSelectorControl.setValue(selected);
            } else if (!parsed.isEmpty()) {
                playerSelectorControl.setValue(parsed.get(0));
            }
        }
    }

    private List<String> parsePlayerList(String playersRaw) {
        List<String> parsed = new ArrayList<>();
        if (playersRaw == null || playersRaw.isBlank()) {
            return parsed;
        }

        String normalized = playersRaw.replace("[", "").replace("]", "").trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("none") || normalized.equalsIgnoreCase("unknown")) {
            return parsed;
        }

        String[] tokens = normalized.split(",");
        for (String token : tokens) {
            String player = token.trim();
            if (!player.isEmpty()) {
                parsed.add(player);
            }
        }
        return parsed;
    }

    private void updateMissionSnapshot() {
        if (missionSnapshotLabel == null) {
            return;
        }

        missionSnapshotLabel.setText(
            "Status: live telemetry connected\n" +
            "Mode: " + currentMissionMode + "\n" +
            "Current goal: " + currentGoal + "\n" +
            "Last action: " + currentAction + "\n" +
            "Position: " + currentPosition + "\n" +
            "Players: " + currentPlayers + "\n" +
            "Goal queue: " + goalQueueState + "\n" +
            "Workflow safety: " + workflowSafetyState + "\n" +
            "Advanced state: " + advancedWorldState + "\n" +
            "Available goals: " + goalCatalogState + "\n" +
            "Advanced features: " + advancedFeaturesState + "\n\n" +
            "GOALS tab runs mission-driven AI with peer advanced features."
        );
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

                } else if (message.startsWith("VIDEO")) {
                    // Video feeds are intentionally disabled in dashboard UI.
                    log("VIDEO", "Video stream packet ignored (travel map mode)");

                } else if (message.startsWith("OBSERVATION:")) {
                    String obs = message.substring(12).trim();
                    log("OBSERVE", obs);
                    updateState(0);

                } else if (message.startsWith("POSITION:")) {
                    String pos = message.substring(9).trim();
                    positionValue.setText(pos);
                    currentPosition = pos;
                    updateMissionSnapshot();
                    updateTravelMap(pos);

                } else if (message.startsWith("PLAYERS:")) {
                    String players = message.substring(8).trim();
                    entitiesValue.setText(players);
                    currentPlayers = players;
                    updatePlayerSelector(players);
                    updateMissionSnapshot();

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
                    currentAction = action;
                    totalActions++;
                    if (successValue != null) {
                        double rate = totalActions == 0 ? 0.0 : (completedSteps * 100.0 / totalActions);
                        successValue.setText(String.format("%.0f%%", Math.max(0.0, Math.min(100.0, rate))));
                    }
                    updateMissionSnapshot();
                    updateState(3);

                } else if (message.startsWith("GOAL:")) {
                    String goal = message.substring(5).trim();
                    log("GOAL", goal);
                    currentGoal = goal;
                    updateMissionSnapshot();

                } else if (message.startsWith("GOAL_CATALOG:")) {
                    goalCatalogState = message.substring(13).trim();
                    updateMissionSnapshot();

                } else if (message.startsWith("ADV_FEATURES:")) {
                    advancedFeaturesState = message.substring(13).trim();
                    updateMissionSnapshot();

                } else if (message.startsWith("GOAL_QUEUE:")) {
                    goalQueueState = message.substring(11).trim();
                    updateMissionSnapshot();

                } else if (message.startsWith("WORKFLOW_SAFETY:")) {
                    workflowSafetyState = message.substring(16).trim();
                    updateMissionSnapshot();

                } else if (message.startsWith("ADV_STATE:")) {
                    advancedWorldState = message.substring(10).trim();
                    updateMissionSnapshot();

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
                        if ("COMPLETED".equalsIgnoreCase(status)) {
                            completedSteps++;
                            if (successValue != null) {
                                double rate = totalActions == 0 ? 100.0 : (completedSteps * 100.0 / totalActions);
                                successValue.setText(String.format("%.0f%%", Math.max(0.0, Math.min(100.0, rate))));
                            }
                        }
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
            for (Label slot : inventorySlots) {
                slot.setText("[ ]");
                slot.setGraphic(null);
            }
            int totalItems = 0;
            int slotIndex = 0;
            for (String part : parts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String mat = kv[0].trim();
                    int count = Integer.parseInt(kv[1]);
                    totalItems += count;
                    if (slotIndex < inventorySlots.size()) {
                        Label slot = inventorySlots.get(slotIndex);
                        slot.setText(mat + "\nx" + count);
                        ImageView icon = createItemIconView(mat);
                        if (icon != null) {
                            slot.setGraphic(icon);
                        }
                        slotIndex++;
                    }
                }
            }
            inventoryItemCountLabel.setText("📦 Items: " + totalItems);
            inventoryCapacityLabel.setText("🔖 Types: " + parts.length + " / " + inventorySlots.size() + " slots");
            inventoryWeightLabel.setText("⚖️ Weight: " + Math.max(0, totalItems / 8) + " kg");
        } catch (Exception e) {
            log("ERROR", "Inventory parse error: " + e.getMessage());
        }
    }

    private ImageView createItemIconView(String material) {
        String key = normalizeMaterialKey(material);
        Image image = inventoryIconCache.get(key);
        if (image == null) {
            image = loadInventoryIcon(key);
            if (image != null) {
                inventoryIconCache.put(key, image);
            }
        }

        if (image == null) {
            return null;
        }

        ImageView icon = new ImageView(image);
        icon.setFitWidth(44);
        icon.setFitHeight(44);
        icon.setPreserveRatio(true);
        icon.setSmooth(false);
        return icon;
    }

    private String normalizeMaterialKey(String material) {
        String key = material == null ? "" : material.trim().toLowerCase();
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return key;
    }

    private Image loadInventoryIcon(String materialKey) {
        if (materialKey.isEmpty()) {
            return null;
        }

        String itemUrl = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/textures/item/" + materialKey + ".png";
        Image itemImage = new Image(itemUrl, true);
        if (!itemImage.isError()) {
            return itemImage;
        }

        String blockUrl = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/textures/block/" + materialKey + ".png";
        Image blockImage = new Image(blockUrl, true);
        if (!blockImage.isError()) {
            return blockImage;
        }

        return null;
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
     * Create travel map panel showing Freddy's recent movement trail.
     */
    private VBox createMultiViewPanel() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(15));
        container.setStyle("-fx-background-color: " + BG_PRIMARY + ";");

        Label title = new Label("◈ TRAVEL MAP (Recent Coordinates + Mission Snapshot)");
        title.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(ACCENT_GREEN));

        HBox mainContent = new HBox(15);
        mainContent.setFillHeight(true);

        VBox leftPanel = new VBox(10);
        leftPanel.setMinWidth(360);
        leftPanel.setPrefWidth(980);
        leftPanel.setStyle(
            "-fx-border-color: " + ACCENT_GREEN + "; " +
            "-fx-border-width: 2; " +
            "-fx-background-color: #0f1419; " +
            "-fx-padding: 15"
        );

        Label leftTitle = new Label("🗺️ MOVEMENT TRAIL");
        leftTitle.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        leftTitle.setTextFill(Color.web(ACCENT_GREEN));

        travelMapCanvas = new Canvas(640, 420);
        leftPanel.widthProperty().addListener((obs, oldVal, newVal) -> {
            double nextWidth = Math.max(320, newVal.doubleValue() - 30);
            travelMapCanvas.setWidth(nextWidth);
            drawTravelMap();
        });
        leftPanel.heightProperty().addListener((obs, oldVal, newVal) -> {
            double nextHeight = Math.max(220, newVal.doubleValue() - 110);
            travelMapCanvas.setHeight(nextHeight);
            drawTravelMap();
        });
        travelMapGc = travelMapCanvas.getGraphicsContext2D();
        travelMapGc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));

        travelMapSummary = new Label("Waiting for POSITION telemetry...");
        travelMapSummary.setWrapText(true);
        travelMapSummary.setFont(Font.font("Consolas", 12));
        travelMapSummary.setTextFill(Color.web(TEXT_SECONDARY));

        leftPanel.getChildren().addAll(leftTitle, travelMapCanvas, travelMapSummary);
        VBox.setVgrow(travelMapCanvas, Priority.ALWAYS);
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        VBox rightPanel = new VBox(10);
        rightPanel.setMinWidth(280);
        rightPanel.setPrefWidth(420);
        rightPanel.setStyle(
            "-fx-border-color: " + ACCENT_BLUE + "; " +
            "-fx-border-width: 2; " +
            "-fx-background-color: #0f1419; " +
            "-fx-padding: 15"
        );

        Label rightTitle = new Label("🧭 LIVE MISSION SNAPSHOT");
        rightTitle.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        rightTitle.setTextFill(Color.web(ACCENT_BLUE));

        missionSnapshotLabel = new Label();
        missionSnapshotLabel.setStyle(
            "-fx-background-color: #1a1f26; " +
            "-fx-text-fill: " + TEXT_SECONDARY + "; " +
            "-fx-font-size: 12px; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-padding: 18;"
        );
        missionSnapshotLabel.setMaxWidth(Double.MAX_VALUE);
        missionSnapshotLabel.setMaxHeight(Double.MAX_VALUE);
        missionSnapshotLabel.setAlignment(Pos.TOP_LEFT);
        missionSnapshotLabel.setWrapText(true);
        updateMissionSnapshot();

        VBox.setVgrow(missionSnapshotLabel, Priority.ALWAYS);
        rightPanel.getChildren().addAll(rightTitle, missionSnapshotLabel);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        viewPanels[1] = new Label("[Not used]");
        viewPanels[2] = new Label("[Not used]");
        viewPanels[3] = missionSnapshotLabel;

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
        if (neuralLogArea == null) {
            return;
        }
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
        if (panelIndex < 0 || panelIndex >= viewPanels.length) {
            return;
        }
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

    private void updateTravelMap(String pos) {
        Platform.runLater(() -> {
            try {
                String[] parts = pos.split(",");
                if (parts.length < 3) {
                    return;
                }

                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());

                travelTrail.addLast(new TravelPoint(x, y, z));
                while (travelTrail.size() > 180) {
                    travelTrail.removeFirst();
                }

                drawTravelMap();
            } catch (Exception e) {
                log("ERROR", "Travel map update failed: " + e.getMessage());
            }
        });
    }

    private void drawTravelMap() {
        if (travelMapGc == null || travelMapCanvas == null) {
            return;
        }

        double width = travelMapCanvas.getWidth();
        double height = travelMapCanvas.getHeight();

        travelMapGc.setFill(Color.web("#0a0e14"));
        travelMapGc.fillRect(0, 0, width, height);

        travelMapGc.setStroke(Color.web("#1c2128"));
        for (int x = 0; x < width; x += 50) {
            travelMapGc.strokeLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += 50) {
            travelMapGc.strokeLine(0, y, width, y);
        }

        if (travelTrail.isEmpty()) {
            travelMapGc.setFill(Color.web(TEXT_SECONDARY));
            travelMapGc.fillText("Waiting for Freddy position updates...", 30, 40);
            if (travelMapSummary != null) {
                travelMapSummary.setText("Waiting for POSITION telemetry...");
            }
            return;
        }

        List<TravelPoint> points = new ArrayList<>(travelTrail);
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (TravelPoint point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
        }

        double rangeX = Math.max(1.0, maxX - minX);
        double rangeZ = Math.max(1.0, maxZ - minZ);
        double scale = Math.min((width - 80) / rangeX, (height - 80) / rangeZ);

        for (int i = 0; i < points.size(); i++) {
            TravelPoint point = points.get(i);
            double px = 40 + (point.x - minX) * scale;
            double py = 40 + (point.z - minZ) * scale;

            if (i > 0) {
                TravelPoint prev = points.get(i - 1);
                double prevX = 40 + (prev.x - minX) * scale;
                double prevY = 40 + (prev.z - minZ) * scale;
                travelMapGc.setStroke(Color.web("#2ea043"));
                travelMapGc.strokeLine(prevX, prevY, px, py);
            }

            boolean isLast = i == points.size() - 1;
            travelMapGc.setFill(isLast ? Color.web("#1f6feb") : Color.web("#58a6ff"));
            travelMapGc.fillOval(px - (isLast ? 5 : 3), py - (isLast ? 5 : 3), isLast ? 10 : 6, isLast ? 10 : 6);
        }

        TravelPoint last = points.get(points.size() - 1);
        travelMapGc.setFill(Color.web(TEXT_PRIMARY));
        travelMapGc.fillText(String.format("Last: X=%.1f, Y=%.1f, Z=%.1f", last.x, last.y, last.z), 24, 24);
        travelMapGc.fillText(String.format("Trail points: %d", points.size()), 24, 42);

        if (travelMapSummary != null) {
            travelMapSummary.setText(String.format(
                "Trail points: %d | Last position: X=%.1f, Y=%.1f, Z=%.1f | Bounds: X[%.1f..%.1f], Z[%.1f..%.1f]",
                points.size(), last.x, last.y, last.z, minX, maxX, minZ, maxZ
            ));
        }
    }

    private static class TravelPoint {
        final double x;
        final double y;
        final double z;

        TravelPoint(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
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
                stepGraphVisualizer.setLoading(false);
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
            if (trimmed.isEmpty() || trimmed.equals("[]")) {
                return stepDataList;
            }

            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{[^\\{\\}]*\\}").matcher(trimmed);
            while (matcher.find()) {
                String step = matcher.group();
                String id = extractJsonValue(step, "id");
                String label = extractJsonValue(step, "label");
                String status = extractJsonValue(step, "status");
                List<String> dependsOn = extractJsonArray(step, "dependsOn");
                if (!id.isEmpty() && !label.isEmpty()) {
                    stepDataList.add(new StepGraphVisualizer.StepData(id, label, status, dependsOn));
                }
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

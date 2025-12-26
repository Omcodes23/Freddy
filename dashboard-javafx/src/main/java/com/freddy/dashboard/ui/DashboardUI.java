package com.freddy.dashboard.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.LinkedList;

/**
 * Professional Scientific Dashboard - NASA Mission Control Style
 * Monochromatic, data-dense, real-time monitoring interface
 */
public class DashboardUI {
    
    // Professional color scheme
    private static final String BG_PRIMARY = "#0a0e14";
    private static final String BG_SECONDARY = "#151a23";
    private static final String BG_PANEL = "#1a2332";
    private static final String BORDER_COLOR = "#2d3748";
    private static final String TEXT_PRIMARY = "#e6edf3";
    private static final String TEXT_SECONDARY = "#8b949e";
    private static final String ACCENT_GREEN = "#2ea043";
    private static final String ACCENT_BLUE = "#1f6feb";
    private static final String ACCENT_YELLOW = "#d29922";
    private static final String ACCENT_RED = "#da3633";
    private static final String MONO_FONT = "Consolas";
    
    // ===== LIVE POV PANEL (REAL-TIME AI VISION) =====
    
    public static VBox createLivePOVPanel() {
        VBox panel = createProfessionalPanel("▣ REALTIME VISUAL CORTEX", ACCENT_GREEN);
        
        // Live POV text area (updates every tick)
        TextArea povArea = new TextArea();
        povArea.setEditable(false);
        povArea.setWrapText(true);
        povArea.setFont(Font.font(MONO_FONT, 11));
        povArea.setStyle(
            "-fx-control-inner-background: " + BG_PANEL + ";" +
            "-fx-text-fill: " + ACCENT_GREEN + ";" +
            "-fx-font-family: '" + MONO_FONT + "';" +
            "-fx-highlight-fill: " + ACCENT_GREEN + "20;" +
            "-fx-highlight-text-fill: " + TEXT_PRIMARY + ";"
        );
        povArea.setPrefHeight(400);
        povArea.setText(">>> AWAITING NEURAL INPUT STREAM...\n>>> INITIALIZING VISUAL CORTEX...");
        povArea.setId("povTextArea"); // ID for external updates
        
        VBox.setVgrow(povArea, Priority.ALWAYS);
        panel.getChildren().add(povArea);
        return panel;
    }
    
    // ===== METRICS PANEL (SYSTEM TELEMETRY) =====
    
    public static VBox createSystemMetricsPanel() {
        VBox panel = createProfessionalPanel("⬢ SYSTEM TELEMETRY", ACCENT_BLUE);
        
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.setPadding(new Insets(10, 0, 0, 0));
        
        // Metric rows with IDs for updates
        int row = 0;
        addMetricRow(grid, row++, "NEURAL LATENCY", "--- ms", ACCENT_YELLOW, "latencyValue");
        addMetricRow(grid, row++, "DECISION FREQ", "--- Hz", ACCENT_BLUE, "frequencyValue");
        addMetricRow(grid, row++, "TOTAL CYCLES", "---", ACCENT_GREEN, "cyclesValue");
        addMetricRow(grid, row++, "SUCCESS RATE", "---", ACCENT_GREEN, "successValue");
        addMetricRow(grid, row++, "POSITION X/Y/Z", "--- / --- / ---", TEXT_SECONDARY, "positionValue");
        addMetricRow(grid, row++, "ENTITIES TRACKED", "---", ACCENT_BLUE, "entitiesValue");
        
        panel.getChildren().add(grid);
        return panel;
    }
    
    // ===== NEURAL ACTIVITY LOG =====
    
    public static VBox createNeuralLogPanel() {
        VBox panel = createProfessionalPanel("◉ NEURAL ACTIVITY LOG", ACCENT_YELLOW);
        
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setFont(Font.font(MONO_FONT, 10));
        logArea.setStyle(
            "-fx-control-inner-background: " + BG_PANEL + ";" +
            "-fx-text-fill: " + TEXT_SECONDARY + ";" +
            "-fx-font-family: '" + MONO_FONT + "';"
        );
        logArea.setPrefHeight(300);
        logArea.setText("[00:00:00.000] SYSTEM >> Initializing neural substrate...\n[00:00:00.001] SYSTEM >> Awaiting connection to game server...");
        logArea.setId("neuralLogArea");
        
        VBox.setVgrow(logArea, Priority.ALWAYS);
        panel.getChildren().add(logArea);
        return panel;
    }
    
    // ===== DECISION MATRIX =====
    
    public static VBox createDecisionMatrixPanel() {
        VBox panel = createProfessionalPanel("⬢ DECISION MATRIX", ACCENT_BLUE);
        
        TextArea decisionArea = new TextArea();
        decisionArea.setEditable(false);
        decisionArea.setWrapText(true);
        decisionArea.setFont(Font.font(MONO_FONT, 10));
        decisionArea.setStyle(
            "-fx-control-inner-background: " + BG_PANEL + ";" +
            "-fx-text-fill: " + ACCENT_BLUE + ";" +
            "-fx-font-family: '" + MONO_FONT + "';"
        );
        decisionArea.setPrefHeight(250);
        decisionArea.setText(
            "╔════════════════════════════════╗\n" +
            "║  DECISION PROCESS ANALYSIS     ║\n" +
            "╠════════════════════════════════╣\n" +
            "║ [IDLE] Awaiting input vector   ║\n" +
            "╚════════════════════════════════╝"
        );
        decisionArea.setId("decisionArea");
        
        VBox.setVgrow(decisionArea, Priority.ALWAYS);
        panel.getChildren().add(decisionArea);
        return panel;
    }
    
    // ===== STATE MONITOR =====
    
    public static VBox createStateMonitorPanel() {
        VBox panel = createProfessionalPanel("◈ STATE MONITOR", ACCENT_YELLOW);
        
        GridPane stateGrid = new GridPane();
        stateGrid.setHgap(15);
        stateGrid.setVgap(10);
        stateGrid.setPadding(new Insets(10, 0, 0, 0));
        
        String[] states = {"OBSERVE", "PROCESS", "DECIDE", "EXECUTE"};
        String[] symbols = {"◉", "◉", "◉", "◉"};
        
        for (int i = 0; i < states.length; i++) {
            HBox stateBox = new HBox(8);
            stateBox.setAlignment(Pos.CENTER_LEFT);
            
            Label symbol = new Label(symbols[i]);
            symbol.setFont(Font.font(MONO_FONT, FontWeight.BOLD, 16));
            symbol.setTextFill(Color.web(TEXT_SECONDARY));
            symbol.setId("stateSymbol" + i);
            
            Label stateLabel = new Label(states[i]);
            stateLabel.setFont(Font.font(MONO_FONT, FontWeight.BOLD, 11));
            stateLabel.setTextFill(Color.web(TEXT_SECONDARY));
            stateLabel.setId("stateLabel" + i);
            
            stateBox.getChildren().addAll(symbol, stateLabel);
            stateGrid.add(stateBox, i % 2, i / 2);
        }
        
        panel.getChildren().add(stateGrid);
        return panel;
    }
    
    // ===== HELPER METHODS =====
    
    private static VBox createProfessionalPanel(String title, String accentColor) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setStyle(
            "-fx-background-color: " + BG_SECONDARY + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 2;" +
            "-fx-background-radius: 2;"
        );
        
        // Title with accent line
        VBox titleBox = new VBox(5);
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font(MONO_FONT, FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.web(accentColor));
        
        Separator accentLine = new Separator();
        accentLine.setStyle("-fx-background-color: " + accentColor + ";");
        accentLine.setPrefHeight(2);
        
        titleBox.getChildren().addAll(titleLabel, accentLine);
        panel.getChildren().add(titleBox);
        
        return panel;
    }
    
    private static void addMetricRow(GridPane grid, int row, String label, String value, String color, String id) {
        Label nameLabel = new Label(label);
        nameLabel.setFont(Font.font(MONO_FONT, 10));
        nameLabel.setTextFill(Color.web(TEXT_SECONDARY));
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font(MONO_FONT, FontWeight.BOLD, 12));
        valueLabel.setTextFill(Color.web(color));
        valueLabel.setId(id); // For external updates
        
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }
    
    /**
     * Format POV data in professional monitoring format
     */
    public static String formatPOVData(String rawPOV) {
        if (rawPOV == null || rawPOV.isEmpty()) {
            return ">>> NO VISUAL DATA STREAM\n>>> SENSORS OFFLINE";
        }
        
        StringBuilder formatted = new StringBuilder();
        formatted.append("╔═══════════════════════════════════════════╗\n");
        formatted.append("║   REALTIME SENSORY INPUT - FRAME DATA     ║\n");
        formatted.append("╠═══════════════════════════════════════════╣\n");
        formatted.append(rawPOV);
        formatted.append("\n╚═══════════════════════════════════════════╝");
        
        return formatted.toString();
    }
}

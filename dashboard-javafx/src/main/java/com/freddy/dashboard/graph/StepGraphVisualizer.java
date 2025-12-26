package com.freddy.dashboard.graph;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.*;

/**
 * Graph visualizer for goal steps inspired by Object Graph Visualizer
 * Shows nodes (steps) and edges (dependencies) in a 2D directed graph
 */
public class StepGraphVisualizer extends Pane {
    
    private static final double NODE_WIDTH = 200;
    private static final double NODE_HEIGHT = 60;
    private static final double VERTICAL_SPACING = 100;
    private static final double HORIZONTAL_SPACING = 250;
    
    private static final Color COLOR_PENDING = Color.web("#8b949e");
    private static final Color COLOR_IN_PROGRESS = Color.web("#1f6feb");
    private static final Color COLOR_COMPLETED = Color.web("#2ea043");
    private static final Color COLOR_FAILED = Color.web("#da3633");
    private static final Color BG_NODE = Color.web("#1c2128");
    private static final Color BG_CANVAS = Color.web("#0a0e14");
    private static final Color TEXT_COLOR = Color.web("#e6edf3");
    
    private Canvas canvas;
    private GraphicsContext gc;
    private List<StepNode> nodes = new ArrayList<>();
    
    public StepGraphVisualizer(double width, double height) {
        canvas = new Canvas(width, height);
        gc = canvas.getGraphicsContext2D();
        getChildren().add(canvas);
        
        // Initial empty state
        drawEmptyState();
    }
    
    public void setSteps(List<StepData> steps) {
        nodes.clear();
        
        if (steps == null || steps.isEmpty()) {
            drawEmptyState();
            return;
        }
        
        // Create nodes
        Map<String, StepNode> nodeMap = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            StepData step = steps.get(i);
            StepNode node = new StepNode(
                step.getId(),
                step.getLabel(),
                step.getStatus(),
                i
            );
            nodes.add(node);
            nodeMap.put(step.getId(), node);
        }
        
        // Set dependencies
        for (StepData step : steps) {
            StepNode node = nodeMap.get(step.getId());
            for (String depId : step.getDependsOn()) {
                StepNode depNode = nodeMap.get(depId);
                if (depNode != null) {
                    node.dependencies.add(depNode);
                }
            }
        }
        
        // Layout nodes
        layoutNodes();
        
        // Draw graph
        draw();
    }
    
    private void layoutNodes() {
        // Simple vertical layout with horizontal offset for dependencies
        Map<StepNode, Integer> levels = new HashMap<>();
        for (StepNode node : nodes) {
            computeLevel(node, levels);
        }
        
        // Position nodes based on their level
        Map<Integer, List<StepNode>> nodesByLevel = new HashMap<>();
        for (StepNode node : nodes) {
            int level = levels.get(node);
            nodesByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(node);
        }
        
        double startY = 50;
        for (int level = 0; level <= Collections.max(levels.values()); level++) {
            List<StepNode> levelNodes = nodesByLevel.getOrDefault(level, Collections.emptyList());
            double startX = (canvas.getWidth() - (levelNodes.size() * (NODE_WIDTH + HORIZONTAL_SPACING) - HORIZONTAL_SPACING)) / 2;
            
            for (int i = 0; i < levelNodes.size(); i++) {
                StepNode node = levelNodes.get(i);
                node.x = startX + i * (NODE_WIDTH + HORIZONTAL_SPACING);
                node.y = startY + level * VERTICAL_SPACING;
            }
        }
    }
    
    private int computeLevel(StepNode node, Map<StepNode, Integer> levels) {
        if (levels.containsKey(node)) {
            return levels.get(node);
        }
        
        if (node.dependencies.isEmpty()) {
            levels.put(node, 0);
            return 0;
        }
        
        int maxDepLevel = 0;
        for (StepNode dep : node.dependencies) {
            maxDepLevel = Math.max(maxDepLevel, computeLevel(dep, levels));
        }
        
        int level = maxDepLevel + 1;
        levels.put(node, level);
        return level;
    }
    
    private boolean loading = false;
    
    private void draw() {
        // Clear canvas
        gc.setFill(BG_CANVAS);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        if (loading) {
            gc.setFill(COLOR_IN_PROGRESS);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("Generating plan...", canvas.getWidth() / 2, canvas.getHeight() / 2);
            return;
        }
        
        // Draw edges first (behind nodes)
        for (StepNode node : nodes) {
            for (StepNode dep : node.dependencies) {
                drawEdge(dep, node);
            }
        }
        
        // Draw nodes
        for (StepNode node : nodes) {
            drawNode(node);
        }
    }
    
    public void setLoading(boolean loading) {
        this.loading = loading;
        draw();
    }

    /**
     * Update the status of a step by id and redraw the graph
     */
    public void updateStepStatus(String stepId, String status) {
        if (stepId == null || status == null) return;
        for (StepNode node : nodes) {
            if (stepId.equals(node.id)) {
                node.status = status;
                break;
            }
        }
        draw();
    }
    
    private void drawEdge(StepNode from, StepNode to) {
        double x1 = from.x + NODE_WIDTH / 2;
        double y1 = from.y + NODE_HEIGHT;
        double x2 = to.x + NODE_WIDTH / 2;
        double y2 = to.y;
        
        gc.setStroke(Color.web("#2d3748"));
        gc.setLineWidth(2);
        gc.strokeLine(x1, y1, x2, y2);
        
        // Draw arrow
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double arrowSize = 10;
        double x3 = x2 - arrowSize * Math.cos(angle - Math.PI / 6);
        double y3 = y2 - arrowSize * Math.sin(angle - Math.PI / 6);
        double x4 = x2 - arrowSize * Math.cos(angle + Math.PI / 6);
        double y4 = y2 - arrowSize * Math.sin(angle + Math.PI / 6);
        
        gc.strokeLine(x2, y2, x3, y3);
        gc.strokeLine(x2, y2, x4, y4);
    }
    
    private void drawNode(StepNode node) {
        // Get color based on status
        Color statusColor = getStatusColor(node.status);
        
        // Draw node background
        gc.setFill(BG_NODE);
        gc.fillRoundRect(node.x, node.y, NODE_WIDTH, NODE_HEIGHT, 10, 10);
        
        // Draw node border
        gc.setStroke(statusColor);
        gc.setLineWidth(3);
        gc.strokeRoundRect(node.x, node.y, NODE_WIDTH, NODE_HEIGHT, 10, 10);
        
        // Draw step number badge
        double badgeSize = 30;
        gc.setFill(statusColor);
        gc.fillOval(node.x + 10, node.y + NODE_HEIGHT / 2 - badgeSize / 2, badgeSize, badgeSize);
        
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.valueOf(node.order + 1), node.x + 10 + badgeSize / 2, node.y + NODE_HEIGHT / 2 + 5);
        
        // Draw label
        gc.setFill(TEXT_COLOR);
        gc.setFont(Font.font("Consolas", 11));
        gc.setTextAlign(TextAlignment.LEFT);
        
        String label = node.label;
        if (label.length() > 22) {
            label = label.substring(0, 22) + "...";
        }
        gc.fillText(label, node.x + 50, node.y + NODE_HEIGHT / 2 + 5);
        
        // Draw status icon
        String statusIcon = getStatusIcon(node.status);
        gc.setFont(Font.font("Consolas", 16));
        gc.setFill(statusColor);
        gc.fillText(statusIcon, node.x + NODE_WIDTH - 30, node.y + NODE_HEIGHT / 2 + 5);
    }
    
    private Color getStatusColor(String status) {
        switch (status.toUpperCase()) {
            case "IN_PROGRESS": return COLOR_IN_PROGRESS;
            case "COMPLETED": return COLOR_COMPLETED;
            case "FAILED": return COLOR_FAILED;
            default: return COLOR_PENDING;
        }
    }
    
    private String getStatusIcon(String status) {
        switch (status.toUpperCase()) {
            case "IN_PROGRESS": return "⟳";
            case "COMPLETED": return "✓";
            case "FAILED": return "✗";
            default: return "○";
        }
    }
    
    private void drawEmptyState() {
        gc.setFill(BG_CANVAS);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        gc.setFill(COLOR_PENDING);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("No goal selected", canvas.getWidth() / 2, canvas.getHeight() / 2 - 20);
        
        gc.setFont(Font.font("Consolas", 12));
        gc.fillText("Select a goal and click ACTIVATE to see step graph", canvas.getWidth() / 2, canvas.getHeight() / 2 + 10);
    }
    
    // Node class
    private static class StepNode {
        String id;
        String label;
        String status;
        int order;
        double x, y;
        List<StepNode> dependencies = new ArrayList<>();
        
        StepNode(String id, String label, String status, int order) {
            this.id = id;
            this.label = label;
            this.status = status;
            this.order = order;
        }
    }
    
    // Step data class
    public static class StepData {
        private String id;
        private String label;
        private String status;
        private List<String> dependsOn;
        
        public StepData(String id, String label, String status, List<String> dependsOn) {
            this.id = id;
            this.label = label;
            this.status = status;
            this.dependsOn = dependsOn != null ? dependsOn : new ArrayList<>();
        }
        
        public String getId() { return id; }
        public String getLabel() { return label; }
        public String getStatus() { return status; }
        public List<String> getDependsOn() { return dependsOn; }
    }
}

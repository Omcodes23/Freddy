package com.freddy.plugin.npc;

import java.util.*;

/**
 * Goal/Task system for NPC directives
 */
public class Goal {
    public enum GoalType {
        GATHER_WOOD,
        GATHER_STONE,
        MINE_DIAMONDS,
        BUILD_STRUCTURE,
        EXPLORE_AREA,
        RETURN_HOME,
        FOLLOW_PLAYER,
        HUNT_ANIMALS,
        FARM_CROPS
    }
    
    public enum GoalStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    private String id;
    private GoalType type;
    private GoalStatus status;
    private String description;
    private Map<String, Object> parameters;
    private long createdAt;
    private long completedAt;
    private List<GoalStep> steps = new ArrayList<>();
    private int currentStepIndex = 0;
    
    public Goal(GoalType type, String description) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.description = description;
        this.status = GoalStatus.PENDING;
        this.parameters = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
    }
    
    public String getId() {
        return id;
    }
    
    public GoalType getType() {
        return type;
    }
    
    public GoalStatus getStatus() {
        return status;
    }
    
    public void setStatus(GoalStatus status) {
        this.status = status;
        if (status == GoalStatus.COMPLETED || status == GoalStatus.FAILED) {
            this.completedAt = System.currentTimeMillis();
        }
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }
    
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getCompletedAt() {
        return completedAt;
    }
    
    public long getElapsedTime() {
        long endTime = completedAt > 0 ? completedAt : System.currentTimeMillis();
        return endTime - createdAt;
    }
    
    // Step management
    public void setSteps(List<GoalStep> steps) {
        this.steps = steps;
        this.currentStepIndex = 0;
    }
    
    public List<GoalStep> getSteps() {
        return steps;
    }
    
    public GoalStep getCurrentStep() {
        if (steps.isEmpty() || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }
    
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }
    
    public void completeCurrentStep() {
        if (currentStepIndex < steps.size()) {
            GoalStep s = steps.get(currentStepIndex);
            s.setStatus(GoalStep.StepStatus.COMPLETED);
            // Telemetry: notify dashboard that a step completed
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) {
                    t.send("GOAL_STEP_UPDATE:{\"id\":\"" + s.getId() + "\",\"status\":\"COMPLETED\"}");
                }
            } catch (Exception ignore) { }
            currentStepIndex++;
        }
    }
    
    public boolean hasMoreSteps() {
        return currentStepIndex < steps.size();
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s (%s) - %s", 
            id.substring(0, 8), type, status, description);
    }
}

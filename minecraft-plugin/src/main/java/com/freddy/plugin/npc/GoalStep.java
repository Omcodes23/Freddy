package com.freddy.plugin.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GoalStep {
    public enum StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

    private final String id;
    private String label;
    private StepStatus status;
    private final List<String> dependsOn;

    public GoalStep(String label) {
        this.id = UUID.randomUUID().toString();
        this.label = label;
        this.status = StepStatus.PENDING;
        this.dependsOn = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    public List<String> getDependsOn() { return dependsOn; }
    public void addDependency(String stepId) { this.dependsOn.add(stepId); }

    public String toJson() {
        StringBuilder deps = new StringBuilder();
        deps.append("[");
        for (int i = 0; i < dependsOn.size(); i++) {
            deps.append("\"").append(dependsOn.get(i)).append("\"");
            if (i < dependsOn.size() - 1) deps.append(",");
        }
        deps.append("]");
        return String.format("{\"id\":\"%s\",\"label\":\"%s\",\"status\":\"%s\",\"dependsOn\":%s}",
                id, escape(label), status.name(), deps.toString());
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

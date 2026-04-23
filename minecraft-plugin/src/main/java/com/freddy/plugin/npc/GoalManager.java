package com.freddy.plugin.npc;

import com.freddy.plugin.advanced.WorkflowSafety;

import java.util.*;

/**
 * Manages NPC goals and directives
 */
public class GoalManager {
    private Goal currentGoal;
    private Queue<Goal> goalQueue = new LinkedList<>();
    private List<Goal> completedGoals = new ArrayList<>();
    private final WorkflowSafety workflowSafety = new WorkflowSafety();
    private final Map<String, Integer> stepFailureCounts = new HashMap<>();
    private final Map<String, Long> stepCooldownUntil = new HashMap<>();
    private final Map<String, Integer> goalFailureCounts = new HashMap<>();
    private static final int MAX_STEP_FAILURES = 3;
    private static final int MAX_GOAL_FAILURES = 5;
    private static final long STEP_COOLDOWN_MS = 2000L;
    
    /**
     * Set active goal
     */
    public void setGoal(Goal goal) {
        if (currentGoal != null && currentGoal.getStatus() == Goal.GoalStatus.IN_PROGRESS) {
            goalQueue.add(currentGoal);
        }
        
        this.currentGoal = goal;
        goal.setStatus(Goal.GoalStatus.IN_PROGRESS);
        if (goal != null) {
            goalFailureCounts.put(goal.getId(), 0);
        }
    }
    
    /**
     * Get current active goal
     */
    public Goal getCurrentGoal() {
        return currentGoal;
    }
    
    /**
     * Mark current goal as completed
     */
    public void completeCurrentGoal() {
        if (currentGoal != null) {
            currentGoal.setStatus(Goal.GoalStatus.COMPLETED);
            completedGoals.add(currentGoal);
            goalFailureCounts.remove(currentGoal.getId());
            moveToNextGoal();
        }
    }
    
    /**
     * Mark current goal as failed
     */
    public void failCurrentGoal(String reason) {
        if (currentGoal != null) {
            currentGoal.setStatus(Goal.GoalStatus.FAILED);
            currentGoal.setParameter("failReason", reason);
            completedGoals.add(currentGoal);
            goalFailureCounts.remove(currentGoal.getId());
            moveToNextGoal();
        }
    }
    
    /**
     * Move to next queued goal
     */
    private void moveToNextGoal() {
        if (!goalQueue.isEmpty()) {
            Goal nextGoal = goalQueue.poll();
            setGoal(nextGoal);
        } else {
            currentGoal = null;
        }
    }
    
    /**
     * Queue a goal to execute after current goal
     */
    public void queueGoal(Goal goal) {
        goal.setStatus(Goal.GoalStatus.PENDING);
        goalQueue.add(goal);
    }
    
    /**
     * Get all active and queued goals
     */
    public List<Goal> getAllGoals() {
        List<Goal> all = new ArrayList<>();
        if (currentGoal != null) {
            all.add(currentGoal);
        }
        all.addAll(goalQueue);
        return all;
    }
    
    /**
     * Get completed goals
     */
    public List<Goal> getCompletedGoals() {
        return new ArrayList<>(completedGoals);
    }

    /**
     * Convenience helper for optional reactive systems.
     */
    public boolean hasActiveGoal() {
        return currentGoal != null && currentGoal.getStatus() == Goal.GoalStatus.IN_PROGRESS;
    }

    /**
     * Convenience helper for diagnostics.
     */
    public int getPendingGoalCount() {
        return goalQueue.size();
    }

    public int getCompletedGoalCount() {
        return completedGoals.size();
    }

    public String getCurrentGoalType() {
        return currentGoal == null ? "none" : currentGoal.getType().name();
    }
    
    /**
     * Clear all goals
     */
    public void clearAllGoals() {
        currentGoal = null;
        goalQueue.clear();
        workflowSafety.reset();
        stepFailureCounts.clear();
        stepCooldownUntil.clear();
        goalFailureCounts.clear();
    }

    public boolean canExecuteStep(Goal goal, GoalStep step) {
        if (goal == null || step == null) {
            return false;
        }

        String key = stepKey(goal, step);
        if (workflowSafety.isBlacklisted(key)) {
            return false;
        }

        long now = System.currentTimeMillis();
        return now >= stepCooldownUntil.getOrDefault(key, 0L);
    }

    public void recordStepFailure(Goal goal, GoalStep step, String reason) {
        if (goal == null || step == null) {
            return;
        }

        String key = stepKey(goal, step);
        int failures = stepFailureCounts.getOrDefault(key, 0) + 1;
        stepFailureCounts.put(key, failures);
        stepCooldownUntil.put(key, System.currentTimeMillis() + STEP_COOLDOWN_MS);

        int goalFailures = goalFailureCounts.getOrDefault(goal.getId(), 0) + 1;
        goalFailureCounts.put(goal.getId(), goalFailures);

        if (failures >= MAX_STEP_FAILURES) {
            workflowSafety.blacklistAction(key);
        }

        try {
            com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
            if (t != null) {
                t.send("WORKFLOW_SAFETY:stepFailures=" + failures + ",goalFailures=" + goalFailures
                    + ",blacklisted=" + workflowSafetyBlacklistedCount()
                    + ",reason=" + sanitize(reason));
            }
        } catch (Exception ignore) { }
    }

    public void recordStepSuccess(Goal goal, GoalStep step) {
        if (goal == null || step == null) {
            return;
        }

        String key = stepKey(goal, step);
        stepFailureCounts.remove(key);
        stepCooldownUntil.remove(key);
    }

    public boolean shouldFailGoal(Goal goal) {
        if (goal == null) {
            return false;
        }
        return goalFailureCounts.getOrDefault(goal.getId(), 0) >= MAX_GOAL_FAILURES;
    }

    public int getGoalFailureCount() {
        if (currentGoal == null) {
            return 0;
        }
        return goalFailureCounts.getOrDefault(currentGoal.getId(), 0);
    }

    public int getStepFailureCount(Goal goal, GoalStep step) {
        if (goal == null || step == null) {
            return 0;
        }
        return stepFailureCounts.getOrDefault(stepKey(goal, step), 0);
    }

    public int workflowSafetyBlacklistedCount() {
        // The blacklist itself is intentionally encapsulated; estimate via step counters.
        int count = 0;
        for (Map.Entry<String, Integer> entry : stepFailureCounts.entrySet()) {
            if (entry.getValue() >= MAX_STEP_FAILURES) {
                count++;
            }
        }
        return count;
    }

    private String stepKey(Goal goal, GoalStep step) {
        return goal.getType().name() + ":" + step.getLabel().toLowerCase(Locale.ROOT);
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "unknown";
        }
        return raw.replace(",", " ").replace("\n", " ").trim();
    }
}

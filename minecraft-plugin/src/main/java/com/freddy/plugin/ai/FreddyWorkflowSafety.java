package com.freddy.plugin.ai;

import java.util.LinkedList;
import java.util.Queue;

/**
 * FreddyWorkflowSafety - Safety guards for workflow execution
 * 
 * Prevents infinite loops, resource abuse, and other dangerous behaviors.
 */
public class FreddyWorkflowSafety {

    // Limits
    public static final int MAX_STEPS_PER_WORKFLOW = 10;
    public static final int MAX_MINING_RADIUS = 50;
    public static final int MAX_KILLS_PER_STEP = 10;
    public static final int MAX_CRAFT_ATTEMPTS = 3;
    public static final long STEP_TIMEOUT_MS = 60000; // 60 seconds
    public static final int MAX_REPLAN_ATTEMPTS = 3;

    // Loop detection (store last 5 workflow hashes)
    private final Queue<String> recentWorkflowHashes = new LinkedList<>();
    private static final int MAX_HISTORY = 5;

    // Re-planning counter
    private int replanAttempts = 0;

    /**
     * Check if workflow exceeds step limit
     */
    public boolean exceedsStepLimit(FreddyWorkflow workflow) {
        return workflow.exceedsStepLimit(MAX_STEPS_PER_WORKFLOW);
    }

    /**
     * Check if workflow is a duplicate (loop detection)
     */
    public boolean isDuplicateWorkflow(FreddyWorkflow workflow) {
        String hash = workflow.getHash();
        return recentWorkflowHashes.contains(hash);
    }

    /**
     * Record workflow execution for loop detection
     */
    public void recordWorkflow(FreddyWorkflow workflow) {
        String hash = workflow.getHash();
        recentWorkflowHashes.offer(hash);

        // Keep only last N workflows
        while (recentWorkflowHashes.size() > MAX_HISTORY) {
            recentWorkflowHashes.poll();
        }
    }

    /**
     * Check if re-planning limit exceeded
     */
    public boolean canReplan() {
        return replanAttempts < MAX_REPLAN_ATTEMPTS;
    }

    /**
     * Increment re-planning counter
     */
    public void incrementReplan() {
        replanAttempts++;
    }

    /**
     * Reset re-planning counter (call on success)
     */
    public void resetReplan() {
        replanAttempts = 0;
    }

    /**
     * Get remaining re-plan attempts
     */
    public int getRemainingReplans() {
        return Math.max(0, MAX_REPLAN_ATTEMPTS - replanAttempts);
    }

    /**
     * Get current replan attempts count
     */
    public int getReplanAttempts() {
        return replanAttempts;
    }

    /**
     * Clear all safety state
     */
    public void reset() {
        recentWorkflowHashes.clear();
        replanAttempts = 0;
    }
}

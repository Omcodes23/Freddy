package com.freddy.plugin.ai.feedback;

import com.freddy.plugin.ai.action.Action;
import com.freddy.plugin.ai.goal.FailureReason;
import com.freddy.plugin.ai.FreddyWorldState;

/**
 * Failure - Captured context when an action fails
 * 
 * Used by the feedback loop to:
 * - Determine if replanning is needed
 * - Prevent repeating the same failed action
 * - Provide diagnostic information
 */
public class Failure {

    public final Action action;
    public final FailureReason reason;
    public final long timestamp;
    public final FreddyWorldState worldStateSnapshot;

    public Failure(Action action, FailureReason reason, FreddyWorldState worldState) {
        this.action = action;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
        this.worldStateSnapshot = worldState;
    }

    /**
     * Check if this failure is recent (within last N seconds)
     */
    public boolean isRecent(int seconds) {
        long age = System.currentTimeMillis() - timestamp;
        return age < (seconds * 1000L);
    }

    /**
     * Get failure description
     */
    public String describe() {
        return String.format("Action '%s' failed: %s (at %d)",
                action.describe(), reason, timestamp);
    }

    @Override
    public String toString() {
        return describe();
    }
}

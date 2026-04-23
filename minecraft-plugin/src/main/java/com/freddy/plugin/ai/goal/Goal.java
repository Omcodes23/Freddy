package com.freddy.plugin.ai.goal;

import org.bukkit.Material;

/**
 * Goal - Represents a persistent objective for Freddy
 * 
 * Unlike immediate commands, goals persist until completed or blocked.
 * The autonomous agent continuously works toward active goals.
 * 
 * Example: Goal(IRON_SWORD, 1) will cause Freddy to:
 * 1. Determine missing materials
 * 2. Plan gathering approach
 * 3. Execute gathering
 * 4. Craft the sword
 * 5. Retry on failures
 * 6. Mark as COMPLETED or BLOCKED
 */
public class Goal {

    public final Material target;
    public final int quantity;

    public GoalStatus status;
    public FailureReason lastFailure;

    // Tracking
    public final long createdAt;
    public long lastAttemptAt;
    public int attemptCount;

    public Goal(Material target, int quantity) {
        this.target = target;
        this.quantity = quantity;
        this.status = GoalStatus.ACTIVE;
        this.createdAt = System.currentTimeMillis();
        this.lastAttemptAt = 0;
        this.attemptCount = 0;
    }

    /**
     * Mark this goal as completed
     */
    public void complete() {
        this.status = GoalStatus.COMPLETED;
    }

    /**
     * Mark this goal as blocked with a reason
     */
    public void block(FailureReason reason) {
        this.status = GoalStatus.BLOCKED;
        this.lastFailure = reason;
    }

    /**
     * Record an attempt to work on this goal
     */
    public void recordAttempt() {
        this.attemptCount++;
        this.lastAttemptAt = System.currentTimeMillis();
    }

    /**
     * Check if this goal has been attempted too many times
     */
    public boolean hasExceededAttempts() {
        return attemptCount > 10; // Max 10 attempts before blocking
    }

    /**
     * Get age of this goal in seconds
     */
    public long getAgeSeconds() {
        return (System.currentTimeMillis() - createdAt) / 1000;
    }

    @Override
    public String toString() {
        return String.format("Goal{%s x%d, status=%s, attempts=%d}",
                target.name(), quantity, status, attemptCount);
    }
}

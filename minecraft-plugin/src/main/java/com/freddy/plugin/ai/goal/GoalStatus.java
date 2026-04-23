package com.freddy.plugin.ai.goal;

/**
 * GoalStatus - Lifecycle states for autonomous goals
 * 
 * ACTIVE: Goal is currently being worked on
 * COMPLETED: Goal was successfully achieved
 * BLOCKED: Goal cannot proceed without external intervention
 */
public enum GoalStatus {
    /**
     * Goal is currently being worked on by the agent
     */
    ACTIVE,

    /**
     * Goal was successfully completed
     */
    COMPLETED,

    /**
     * Goal is blocked and cannot proceed
     * Requires external intervention (player help, world changes, etc.)
     */
    BLOCKED
}

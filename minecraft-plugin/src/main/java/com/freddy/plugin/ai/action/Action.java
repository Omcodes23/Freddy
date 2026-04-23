package com.freddy.plugin.ai.action;

import com.freddy.plugin.ai.FreddyWorldState;

import java.util.function.Consumer;

/**
 * Action - Interface for executable actions
 * 
 * Actions are "dumb" executors that perform specific tasks.
 * They never make decisions, never call LLM, never choose alternatives.
 * 
 * Design principles:
 * - Actions execute asynchronously with callbacks
 * - Actions report results honestly
 * - Actions can be retried if they return FAILED_TEMPORARY
 * - Actions are blacklisted if they return FAILED_PERMANENT
 * 
 * IMPORTANT: All actions are asynchronous to match underlying Bukkit/workflow
 * patterns.
 */
public interface Action {

    /**
     * Execute this action asynchronously
     * 
     * @param state    Current world state
     * @param callback Called when action completes with the result
     */
    void executeAsync(FreddyWorldState state, Consumer<ActionResult> callback);

    /**
     * Get human-readable description of this action
     */
    String describe();

    /**
     * Can this action be retried after failure?
     */
    boolean canRetry();

    /**
     * Estimated duration in milliseconds (for timeout purposes)
     */
    int getEstimatedDuration();
}

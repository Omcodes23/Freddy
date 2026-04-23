package com.freddy.plugin.ai.action;

/**
 * ActionResult - Outcome of executing an action
 * 
 * Used by the feedback loop to determine next steps:
 * - SUCCESS: Continue to next action
 * - FAILED_TEMPORARY: Retry possible (mob moved, block obstructed)
 * - FAILED_PERMANENT: Cannot retry (no tool, impossible action)
 */
public enum ActionResult {
    /**
     * Action completed successfully
     */
    SUCCESS,

    /**
     * Action failed but can be retried
     * Examples: mob not found, path blocked temporarily
     */
    FAILED_TEMPORARY,

    /**
     * Action failed permanently and cannot be retried
     * Examples: no tool, impossible to reach, invalid target
     */
    FAILED_PERMANENT
}

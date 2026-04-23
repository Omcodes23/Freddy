package com.freddy.plugin.ai.goal;

/**
 * FailureReason - Categorizes why an action or goal failed
 * 
 * Used by the feedback loop to determine if replanning is possible
 * or if the goal should be marked as BLOCKED.
 */
public enum FailureReason {
    /**
     * Action failed during execution (mob escaped, block unreachable, etc.)
     */
    EXECUTION,

    /**
     * Required materials are not available and cannot be gathered
     */
    MISSING_MATERIALS,

    /**
     * Cannot find a valid path to the target
     */
    NO_VALID_PATH,

    /**
     * Environmental conditions prevent completion (lava, void, etc.)
     */
    ENVIRONMENTAL,

    /**
     * Action took too long without progress
     */
    TIMEOUT,

    /**
     * No furnace available for smelting
     */
    NO_FURNACE,

    /**
     * No fuel available for smelting
     */
    NO_FUEL,

    /**
     * Recipe or conversion is unknown
     */
    UNKNOWN_RECIPE
}

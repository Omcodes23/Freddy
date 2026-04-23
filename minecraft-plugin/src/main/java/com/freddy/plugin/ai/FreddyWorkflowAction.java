package com.freddy.plugin.ai;

/**
 * FreddyWorkflowAction - Allowed Freddy commands
 * 
 * Defines the atomic commands that the LLM can output.
 * These are then executed deterministically by Java.
 */
public enum FreddyWorkflowAction {
    /**
     * Mine blocks from the world
     * Command: FREDDY_MINE
     * Target: Material name (e.g., "OAK_LOG", "IRON_ORE")
     */
    FREDDY_MINE,

    /**
     * Hunt and kill mobs for drops
     * Command: FREDDY_KILL
     * Target: EntityType name (e.g., "COW", "ZOMBIE")
     */
    FREDDY_KILL,

    /**
     * Craft items using inventory
     * Command: FREDDY_CRAFT
     * Target: Material name (e.g., "CRAFTING_TABLE", "STICK")
     */
    FREDDY_CRAFT
}

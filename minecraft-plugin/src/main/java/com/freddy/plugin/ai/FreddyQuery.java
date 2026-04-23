package com.freddy.plugin.ai;

/**
 * FreddyQuery - Optional Input for Targeted Perception
 * 
 * This class allows the LLM (or any caller) to request specific information
 * from the perception system, such as finding the nearest block of a certain
 * type or locating a specific mob.
 * 
 * JSON-friendly and LLM-compatible.
 */
public class FreddyQuery {

    // =========================
    // FILTERS
    // =========================
    // Optional: specific block type to search for (e.g., "LAVA", "DIAMOND_ORE")
    public String blockType;

    // Optional: specific mob type to search for (e.g., "ZOMBIE", "SKELETON")
    public String mobType;

    // =========================
    // SCAN CONSTRAINTS
    // =========================
    // Scan radius (default: 6 blocks)
    public int radius = 6;

    // If true, only return the nearest match; if false, return first match found
    public boolean nearestOnly = true;

    // =========================
    // HELPERS
    // =========================
    /**
     * Check if this query is empty (no filters specified)
     */
    public boolean isEmpty() {
        return blockType == null && mobType == null;
    }
}

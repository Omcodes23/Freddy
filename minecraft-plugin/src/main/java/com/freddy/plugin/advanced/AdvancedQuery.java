package com.freddy.plugin.advanced;

/**
 * Optional targeted scan request.
 */
public class AdvancedQuery {
    public String blockType;
    public String mobType;
    public int radius = 10;
    public boolean nearestOnly = true;

    public boolean isEmpty() {
        return (blockType == null || blockType.isBlank()) && (mobType == null || mobType.isBlank());
    }
}

package com.freddy.ai;

import java.util.List;

/**
 * Immutable snapshot of Freddy's observations at a moment in time.
 * Represents what Freddy perceives about the world.
 */
public record Observation(
    List<String> nearbyPlayers,      // Players within perception range
    double currentX,                  // Freddy's X coordinate
    double currentY,                  // Freddy's Y coordinate
    double currentZ,                  // Freddy's Z coordinate
    int worldTime,                    // 0-24000 (Minecraft time)
    String lastInteractionPlayer,     // Who last talked to Freddy?
    long lastInteractionTimeMs,       // When did they talk? (timestamp)
    String lastAction,                // What was the last action Freddy took?
    long lastActionTimeMs,            // When did Freddy last act? (timestamp)
    // --- Environmental context (new) ---
    double health,                    // 0-20 hearts
    int foodLevel,                    // 0-20 hunger
    String biome,                     // e.g., "PLAINS", "FOREST"
    String weather,                   // "CLEAR", "RAIN", "THUNDER"
    int lightLevel,                   // 0-15
    List<String> nearbyBlocks,        // Notable blocks nearby (resources etc.)
    List<String> nearbyEntities,      // Mobs/animals nearby
    List<String> inventorySummary     // Key items in inventory
) {

    /**
     * Backward-compatible constructor (for existing callers)
     */
    public Observation(
            List<String> nearbyPlayers,
            double currentX, double currentY, double currentZ,
            int worldTime,
            String lastInteractionPlayer, long lastInteractionTimeMs,
            String lastAction, long lastActionTimeMs) {
        this(nearbyPlayers, currentX, currentY, currentZ, worldTime,
             lastInteractionPlayer, lastInteractionTimeMs,
             lastAction, lastActionTimeMs,
             20.0, 20, "UNKNOWN", "CLEAR", 15,
             List.of(), List.of(), List.of());
    }
    
    @Override
    public String toString() {
        return String.format(
            "Observation{players=%s, pos=(%.1f,%.1f,%.1f), " +
            "time=%d, health=%.1f, food=%d, biome=%s, blocks=%d, entities=%d}",
            nearbyPlayers, currentX, currentY, currentZ,
            worldTime, health, foodLevel, biome,
            nearbyBlocks != null ? nearbyBlocks.size() : 0,
            nearbyEntities != null ? nearbyEntities.size() : 0
        );
    }
    
    public boolean isPlayerNearby(String playerName) {
        return nearbyPlayers != null && nearbyPlayers.contains(playerName);
    }
    
    public long timeSinceLastInteraction() {
        if (lastInteractionTimeMs == 0) return Long.MAX_VALUE;
        return (System.currentTimeMillis() - lastInteractionTimeMs) / 1000;
    }
    
    public long timeSinceLastAction() {
        if (lastActionTimeMs == 0) return Long.MAX_VALUE;
        return (System.currentTimeMillis() - lastActionTimeMs) / 1000;
    }
    
    public boolean isDayTime() {
        return worldTime >= 0 && worldTime < 12000;
    }
    
    public String getTimeOfDay() {
        if (worldTime < 6000) return "morning";
        else if (worldTime < 12000) return "noon";
        else if (worldTime < 18000) return "evening";
        else return "night";
    }

    public boolean isLowHealth() {
        return health <= 8.0;
    }

    public boolean isHungry() {
        return foodLevel < 8;
    }
}

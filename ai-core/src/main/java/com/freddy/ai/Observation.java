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
    long lastActionTimeMs             // When did Freddy last act? (timestamp)
) {
    
    @Override
    public String toString() {
        return String.format(
            "Observation{players=%s, pos=(%.1f,%.1f,%.1f), " +
            "time=%d, lastPlayer=%s, lastAction=%s}",
            nearbyPlayers, currentX, currentY, currentZ,
            worldTime, lastInteractionPlayer, lastAction
        );
    }
    
    /**
     * Check if a specific player is nearby
     */
    public boolean isPlayerNearby(String playerName) {
        return nearbyPlayers != null && nearbyPlayers.contains(playerName);
    }
    
    /**
     * Time since last interaction (in seconds)
     */
    public long timeSinceLastInteraction() {
        if (lastInteractionTimeMs == 0) return Long.MAX_VALUE;
        return (System.currentTimeMillis() - lastInteractionTimeMs) / 1000;
    }
    
    /**
     * Time since last action (in seconds)
     */
    public long timeSinceLastAction() {
        if (lastActionTimeMs == 0) return Long.MAX_VALUE;
        return (System.currentTimeMillis() - lastActionTimeMs) / 1000;
    }
    
    /**
     * Is it day or night in Minecraft?
     * Day: 0-12000, Night: 12000-24000
     */
    public boolean isDayTime() {
        return worldTime >= 0 && worldTime < 12000;
    }
    
    /**
     * Get time of day as a readable string
     */
    public String getTimeOfDay() {
        if (worldTime < 6000) {
            return "morning";
        } else if (worldTime < 12000) {
            return "noon";
        } else if (worldTime < 18000) {
            return "evening";
        } else {
            return "night";
        }
    }
}

package com.freddy.ai;

import java.util.List;

/**
 * Builds context-aware prompts for the LLM.
 * Includes full environmental and inventory context so the AI can make informed decisions.
 */
public class PromptBuilder {
    
    private final String npcName;
    
    public PromptBuilder(String npcName) {
        this.npcName = npcName;
    }
    
    /**
     * Build the main LLM prompt from observation
     */
    public String buildPrompt(Observation observation) {
        StringBuilder sb = new StringBuilder();
        
        // System role and personality
        sb.append("You are ").append(npcName).append(", an autonomous AI character in Minecraft.\n");
        sb.append("You are proactive, curious, and avoid standing still.\n");
        sb.append("You make decisions based on your surroundings, health, and inventory.\n");
        sb.append("Do NOT idle repeatedly. Choose a meaningful action every tick.\n\n");
        
        // Current situation
        sb.append("CURRENT STATUS:\n");
        sb.append("- Position: X=").append(String.format("%.1f", observation.currentX()))
            .append(", Y=").append(String.format("%.1f", observation.currentY()))
            .append(", Z=").append(String.format("%.1f", observation.currentZ())).append("\n");
        sb.append("- Health: ").append(String.format("%.1f", observation.health())).append("/20");
        if (observation.isLowHealth()) sb.append(" [LOW!]");
        sb.append("\n");
        sb.append("- Hunger: ").append(observation.foodLevel()).append("/20");
        if (observation.isHungry()) sb.append(" [HUNGRY!]");
        sb.append("\n");
        sb.append("- Time: ").append(observation.getTimeOfDay());
        sb.append(observation.isDayTime() ? " (day)" : " (night - mobs spawn!)");
        sb.append("\n");
        sb.append("- Biome: ").append(observation.biome() != null ? observation.biome() : "Unknown").append("\n");
        sb.append("- Weather: ").append(observation.weather() != null ? observation.weather() : "Clear").append("\n");
        sb.append("- Light level: ").append(observation.lightLevel()).append("/15\n");
        
        // Nearby players
        sb.append("- Nearby players: ");
        if (observation.nearbyPlayers() == null || observation.nearbyPlayers().isEmpty()) {
            sb.append("[none]");
        } else {
            sb.append(String.join(", ", observation.nearbyPlayers()));
        }
        sb.append("\n");
        
        // Nearby blocks/resources
        List<String> blocks = observation.nearbyBlocks();
        if (blocks != null && !blocks.isEmpty()) {
            sb.append("- Nearby resources/blocks: ");
            sb.append(String.join(", ", blocks.subList(0, Math.min(blocks.size(), 10))));
            sb.append("\n");
        }
        
        // Nearby entities (mobs/animals)
        List<String> entities = observation.nearbyEntities();
        if (entities != null && !entities.isEmpty()) {
            sb.append("- Nearby mobs/animals: ");
            sb.append(String.join(", ", entities.subList(0, Math.min(entities.size(), 8))));
            sb.append("\n");
        }

        // Inventory
        List<String> inv = observation.inventorySummary();
        if (inv != null && !inv.isEmpty()) {
            sb.append("- Inventory: ");
            sb.append(String.join(", ", inv.subList(0, Math.min(inv.size(), 12))));
            sb.append("\n");
        } else {
            sb.append("- Inventory: [empty]\n");
        }
        
        // Recent activity
        if (observation.lastInteractionPlayer() != null && 
            !observation.lastInteractionPlayer().isEmpty()) {
            long secAgo = observation.timeSinceLastInteraction();
            if (secAgo < 300) {
                sb.append("- Last interaction: ").append(observation.lastInteractionPlayer())
                    .append(" (").append(secAgo).append("s ago)\n");
            }
        }
        
        if (observation.lastAction() != null && !observation.lastAction().isEmpty() &&
            !observation.lastAction().equals("Initialized")) {
            sb.append("- Last action: ").append(observation.lastAction()).append("\n");
        }
        
        sb.append("\n");
        
        // Available actions
        
        sb.append("AVAILABLE ACTIONS (reply with ONE):\n");
        sb.append("1. \"Follow [player]\" - follow a nearby player\n");
        sb.append("2. \"Look at [player]\" - face and observe\n");
        sb.append("3. \"Attack [entity]\" - attack a mob (e.g., \"Attack zombie\")\n");
        sb.append("4. \"Mine [block]\" - mine a block (e.g., \"Mine oak_log\", \"Mine stone\")\n");
        sb.append("5. \"Walk to X Z\" - move to nearby location (within 50 blocks!)\n");
        sb.append("6. \"Say [message]\" - chat in game\n");
        sb.append("7. \"Wander\" - explore randomly nearby\n");
        sb.append("8. \"Idle\" - rest (avoid using this)\n\n");
        
        // Decision priorities based on context
        sb.append("PRIORITIES:\n");
        if (observation.isLowHealth()) {
            sb.append("- YOUR HEALTH IS LOW! Eat food or retreat to safety.\n");
        }
        if (observation.isHungry()) {
            sb.append("- You are hungry. Find food (hunt animals or eat from inventory).\n");
        }
        if (!observation.isDayTime()) {
            sb.append("- It's nighttime. Be cautious of hostile mobs.\n");
        }
        if (observation.nearbyPlayers() != null && !observation.nearbyPlayers().isEmpty()) {
            sb.append("- Players are nearby. Consider interacting with them.\n");
        }
        if (blocks != null && !blocks.isEmpty()) {
            sb.append("- Useful resources are visible. Consider mining them.\n");
        }
        
        sb.append("\nReply with ONLY ONE action. Be proactive like a real Minecraft player.\n");
        
        return sb.toString();
    }
    
    public String getSystemRole() {
        return "You are " + npcName + ", an autonomous AI character in Minecraft.";
    }
    
    /**
     * Build a simplified prompt for faster LLM responses
     */
    public String buildSimplePrompt(Observation observation) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(npcName).append(" in Minecraft. ");
        
        if (observation.nearbyPlayers() != null && !observation.nearbyPlayers().isEmpty()) {
            sb.append("Players nearby: ").append(observation.nearbyPlayers()).append(". ");
        } else {
            sb.append("No players nearby. ");
        }
        
        if (observation.isLowHealth()) sb.append("Health is low! ");
        if (observation.isHungry()) sb.append("Hungry! ");
        
        sb.append("What do you do? Reply with ONE action: ");
        sb.append("\"Walk to X Z\", \"Follow [player]\", \"Mine [block]\", \"Attack [mob]\", \"Wander\", or \"Say [message]\"");
        
        return sb.toString();
    }
}

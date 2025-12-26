package com.freddy.ai;

/**
 * Builds context-aware prompts for the LLM.
 * Responsible for formatting observations into natural language.
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
        sb.append("You are ").append(npcName).append(", a friendly AI character in Minecraft.\n");
        sb.append("You are proactive, curious, and avoid standing still.\n");
        sb.append("You speak naturally, like a player in the game.\n");
        sb.append("You make decisions based on what's happening around you.\n");
        sb.append("If players are nearby, prefer to engage/follow them. If no players, explore new ground.\n");
        sb.append("Do NOT idle repeatedly. Choose a meaningful action every tick.\n\n");
        
        // Current situation
        sb.append("CURRENT SITUATION:\n");
        sb.append("- Location: X=").append(String.format("%.1f", observation.currentX()))
            .append(", Y=").append(String.format("%.1f", observation.currentY()))
            .append(", Z=").append(String.format("%.1f", observation.currentZ())).append("\n");
        
        // Nearby players
        sb.append("- Nearby players: ");
        if (observation.nearbyPlayers() == null || observation.nearbyPlayers().isEmpty()) {
            sb.append("[none]");
        } else {
            sb.append(observation.nearbyPlayers());
        }
        sb.append("\n");
        
        // Time of day
        sb.append("- Time: ").append(observation.getTimeOfDay());
        if (observation.isDayTime()) {
            sb.append(" (day)");
        } else {
            sb.append(" (night)");
        }
        sb.append("\n");
        
        // Recent activity
        if (observation.lastInteractionPlayer() != null && 
            !observation.lastInteractionPlayer().isEmpty()) {
            long secAgo = observation.timeSinceLastInteraction();
            if (secAgo < 300) { // Within 5 minutes
                sb.append("- Last interaction: ").append(observation.lastInteractionPlayer())
                    .append(" (").append(secAgo).append(" seconds ago)\n");
            }
        }
        
        if (observation.lastAction() != null && !observation.lastAction().isEmpty() &&
            !observation.lastAction().equals("Initialized")) {
            sb.append("- Last action: ").append(observation.lastAction()).append("\n");
        }
        
        sb.append("\n");
        
        // Available actions with REALISTIC examples based on current position
        int currentX = (int) observation.currentX();
        int currentZ = (int) observation.currentZ();
        int nearbyX1 = currentX + 15;
        int nearbyZ1 = currentZ - 20;
        int nearbyX2 = currentX - 10;
        int nearbyZ2 = currentZ + 25;
        
        sb.append("AVAILABLE ACTIONS:\n");
        sb.append("1. \"Follow [player]\" - trail a nearby player (e.g., \"Follow aimbotxomega\")\n");
        sb.append("2. \"Look at [player]\" - face and observe (e.g., \"Look at aimbotxomega\")\n");
        sb.append("3. \"Attack [entity]\" - attack a mob (e.g., \"Attack zombie\")\n");
        sb.append("4. \"Mine [block]\" - break a nearby block (e.g., \"Mine stone\", \"Mine tree\")\n");
        sb.append("5. \"Walk to X Z\" - explore NEARBY locations only! From your current position (")
            .append(currentX).append(" ").append(currentZ)
            .append("), you could walk to (").append(nearbyX1).append(" ").append(nearbyZ1)
            .append(") or (").append(nearbyX2).append(" ").append(nearbyZ2).append(")\n");
        sb.append("6. \"Say [message]\" - chat (e.g., \"Say Hello there!\")\n");
        sb.append("7. \"Wander\" - move randomly nearby\n");
        sb.append("8. \"Idle\" - only if you truly have nothing to do (avoid)\n");
        sb.append("\nCRITICAL: When using 'Walk to X Z', coordinates MUST be within 50 blocks!\n");
        sb.append("Your current position: (").append(currentX).append(", ").append(currentZ).append(")\n");
        sb.append("Valid range: X between ").append(currentX - 50).append(" and ").append(currentX + 50);
        sb.append(", Z between ").append(currentZ - 50).append(" and ").append(currentZ + 50).append("\n\n");
        
        // Instruction
        sb.append("What should you do RIGHT NOW?\n");
        sb.append("Rules:\n");
        sb.append("- If players are nearby, consider following them, talking to them, or showing off by mining/building.\n");
        sb.append("- If you see trees or stone, consider mining them to gather resources.\n");
        sb.append("- If mobs are nearby, consider attacking them.\n");
        sb.append("- If no players nearby, explore, mine resources, or wander.\n");
        sb.append("- Do NOT return Idle repeatedly.\n");
        sb.append("- Be proactive like a real Minecraft player!\n");
        sb.append("- Reply with ONLY ONE action, nothing else.\n\n");
        
        // Examples with NEARBY coordinates
        sb.append("Example responses:\n");
        sb.append("- \"Mine tree\" (if you see trees nearby)\n");
        sb.append("- \"Mine stone\" (if you see stone nearby)\n");
        sb.append("- \"Attack zombie\" (if hostile mob nearby)\n");
        sb.append("- \"Follow aimbotxomega\"\n");
        sb.append("- \"Look at aimbotxomega\"\n");
        sb.append("- \"Walk to ").append(nearbyX1).append(" ").append(nearbyZ1).append("\" (nearby)\n");
        sb.append("- \"Say Hi there!\"\n");
        sb.append("- \"Wander\"\n");
        
        return sb.toString();
    }
    
    /**
     * Extract just the system role (for testing)
     */
    public String getSystemRole() {
        return "You are " + npcName + ", a friendly AI character in Minecraft.";
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
        
        sb.append("What do you do? Reply with ONE action: ");
        sb.append("\"Walk to X Z\", \"Follow [player]\", \"Idle\", \"Wander\", or \"Say [message]\"");
        
        return sb.toString();
    }
}

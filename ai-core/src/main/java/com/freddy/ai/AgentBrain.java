//package com.freddy.ai;
//
//import com.freddy.llm.LLMClient;
//
//public class AgentBrain {
//
//    private final String npcName;
//
//    public AgentBrain(String npcName) {
//        this.npcName = npcName;
//    }
//
//    public String respond(String playerName, String message) {
//
//        String prompt = """
//        You are Freddy, a friendly AI character inside Minecraft.
//        You speak casually like a human player.
//        Player %s says: "%s"
//        Reply briefly, friendly, and in character.
//        """.formatted(playerName, message);
//
//        return LLMClient.ask(prompt);
//    }
//}

package com.freddy.ai;

import com.freddy.common.telemetry.FreddySnapshot;
import com.freddy.common.TelemetryClient;
import com.freddy.llm.LLMClient;

import java.util.logging.Logger;

/**
 * The brain of Freddy - handles decision making and autonomous thinking.
 */
public class AgentBrain {

    private final String npcName;
    private final PromptBuilder promptBuilder;
    private final Logger logger;
    
    private String currentThought = "Initializing";
    private String currentAction = "Waiting";
    private int idleStreak = 0;
    private int wanderStreak = 0;

    public AgentBrain(String npcName) {
        this.npcName = npcName;
        this.promptBuilder = new PromptBuilder(npcName);
        this.logger = Logger.getLogger(AgentBrain.class.getName());
    }

    /**
     * Think about what to do next based on observations
     * This is the core autonomous thinking method
     */
    public Action think(Observation observation) {
        return think(observation, null);
    }
    
    /**
     * Think with telemetry support
     */
    public Action think(Observation observation, TelemetryClient telemetry) {
        try {
            // Build prompt with context
            String prompt = promptBuilder.buildPrompt(observation);
            
            // Send prompt to dashboard
            if (telemetry != null && telemetry.isConnected()) {
                telemetry.sendThinking(prompt);
            }
            
            // Ask LLM what to do
            logger.info("🤔 Asking LLM for decision...");
            String response = LLMClient.ask(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                logger.warning("⚠️ LLM returned empty response, defaulting to IDLE");
                currentThought = "Thinking...";
                return new Action.Idle();
            }
            
            logger.info("💡 LLM Response: " + response);
            
            // Send LLM response to dashboard
            if (telemetry != null && telemetry.isConnected()) {
                telemetry.sendLLMResponse(response);
            }
            
            // Parse response into action
            Action action = ActionParser.parse(response);

            // Heuristic guardrails: avoid endless idle/looping
            action = refineAction(observation, action);
            
            // Update current thought
            currentThought = response.length() > 100 ? 
                response.substring(0, 97) + "..." : response;
            
            return action;
            
        } catch (Exception e) {
            logger.severe("❌ Error in think(): " + e.getMessage());
            e.printStackTrace();
            currentThought = "Error occurred";
            return new Action.Idle();
        }
    }

    public void setThought(String thought) {
        this.currentThought = thought;
    }

    public void setAction(String action) {
        this.currentAction = action;
    }
    
    public String getCurrentThought() {
        return currentThought;
    }
    
    public String getCurrentAction() {
        return currentAction;
    }
    
    public String getNpcName() {
        return npcName;
    }

    public FreddySnapshot snapshot(
            String npcName,
            double x,
            double y,
            double z
    ) {
        return new FreddySnapshot(
                npcName,
                currentThought,
                currentAction,
                x, y, z
        );
    }

    /**
     * Apply simple heuristics to keep the agent autonomous even when LLM is timid.
     */
    private Action refineAction(Observation observation, Action proposed) {
        if (proposed == null) {
            return new Action.Wander();
        }

        boolean hasPlayers = observation.nearbyPlayers() != null && !observation.nearbyPlayers().isEmpty();
        String firstPlayer = hasPlayers ? observation.nearbyPlayers().get(0) : null;

        switch (proposed.type) {
            case IDLE -> {
                idleStreak++;
                wanderStreak = 0;

                // If players are around, prefer to follow instead of idling
                if (hasPlayers) {
                    return new Action.FollowPlayer(firstPlayer);
                }

                // Avoid idling more than twice in a row; explore instead
                if (idleStreak > 2) {
                    return new Action.Wander();
                }
            }
            case WANDER -> {
                wanderStreak++;
                idleStreak = 0;

                // If wandering too long and players exist, go engage
                if (hasPlayers && wanderStreak > 2) {
                    return new Action.FollowPlayer(firstPlayer);
                }
            }
            case WALK_TO -> {
                // Validate WALK_TO isn't too far away
                if (proposed instanceof Action.WalkTo walkToAction) {
                    double targetX = walkToAction.x;
                    double targetZ = walkToAction.z;
                    double currentX = observation.currentX();
                    double currentZ = observation.currentZ();
                    
                    double distance = Math.sqrt(
                        Math.pow(targetX - currentX, 2) + 
                        Math.pow(targetZ - currentZ, 2)
                    );
                    
                    // If target is >50 blocks away, follow nearest player instead (or wander)
                    if (distance > 50) {
                        logger.warning("⚠️ WALK_TO target too far (" + (int)distance + " blocks), switching to " + 
                                     (hasPlayers ? "FOLLOW" : "WANDER"));
                        return hasPlayers ? new Action.FollowPlayer(firstPlayer) : new Action.Wander();
                    }
                }
                
                idleStreak = 0;
                wanderStreak = 0;
            }
            default -> {
                idleStreak = 0;
                wanderStreak = 0;
            }
        }

        return proposed;
    }
}


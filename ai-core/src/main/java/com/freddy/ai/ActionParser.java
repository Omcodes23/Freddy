package com.freddy.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LLM text responses into Action objects.
 * Uses regex patterns to extract action type and parameters.
 */
public class ActionParser {
    
    // Regex patterns for different action types
    private static final Pattern WALK_TO_PATTERN = 
        Pattern.compile("walk\\s+to\\s+([-\\d.]+)\\s+([-\\d.]+)\\s*([-\\d.]+)?", 
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern FOLLOW_PATTERN = 
        Pattern.compile("follow\\s+([\\w]+)", 
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern LOOK_AT_PATTERN = 
        Pattern.compile("look\\s+at\\s+([\\w]+)", 
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern SAY_PATTERN = 
        Pattern.compile("say\\s+(.+?)(?:\\n|$)", 
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern RESPOND_PATTERN = 
        Pattern.compile("respond[:\\s]+(.+?)(?:\\n|$)", 
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern MINE_PATTERN = 
        Pattern.compile("mine\\s+([\\w]+)", 
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern ATTACK_PATTERN = 
        Pattern.compile("attack\\s+([\\w]+)", 
            Pattern.CASE_INSENSITIVE);
    
    /**
     * Parse LLM response into Action
     * @param response The raw text response from LLM
     * @return Parsed Action object, or Idle if parsing fails
     */
    public static Action parse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return new Action.Idle();
        }
        
        String trimmed = response.trim();
        String lower = trimmed.toLowerCase();
        
        // Try WALK_TO pattern: "Walk to 150 -50" or "Walk to 150 64 -50"
        Matcher walkMatcher = WALK_TO_PATTERN.matcher(trimmed);
        if (walkMatcher.find()) {
            try {
                double x = Double.parseDouble(walkMatcher.group(1));
                double z = Double.parseDouble(walkMatcher.group(2));
                return new Action.WalkTo(x, z);
            } catch (NumberFormatException e) {
                // Fall through to next pattern
            }
        }
        
        // Try FOLLOW pattern: "Follow OnlyOm"
        Matcher followMatcher = FOLLOW_PATTERN.matcher(trimmed);
        if (followMatcher.find()) {
            String playerName = followMatcher.group(1);
            return new Action.FollowPlayer(playerName);
        }
        
        // Try LOOK_AT pattern: "Look at OnlyOm"
        Matcher lookMatcher = LOOK_AT_PATTERN.matcher(trimmed);
        if (lookMatcher.find()) {
            String target = lookMatcher.group(1);
            return new Action.LookAt(target);
        }
        
        // Try SAY pattern: "Say Hi there!"
        Matcher sayMatcher = SAY_PATTERN.matcher(trimmed);
        if (sayMatcher.find()) {
            String message = sayMatcher.group(1).trim();
            if (!message.isEmpty()) {
                return new Action.Respond(message);
            }
        }
        
        // Try RESPOND pattern: "Respond: Hello friend!"
        Matcher respondMatcher = RESPOND_PATTERN.matcher(trimmed);
        if (respondMatcher.find()) {
            String message = respondMatcher.group(1).trim();
            if (!message.isEmpty()) {
                return new Action.Respond(message);
            }
        }
        
        // Try MINE pattern: "Mine tree" or "Mine stone"
        Matcher mineMatcher = MINE_PATTERN.matcher(trimmed);
        if (mineMatcher.find()) {
            String blockType = mineMatcher.group(1);
            // Return a simplified mine action (GameActions will find nearest block)
            return new Action.MineBlock(blockType, 0, 0, 0);
        }
        
        // Try ATTACK pattern: "Attack zombie"
        Matcher attackMatcher = ATTACK_PATTERN.matcher(trimmed);
        if (attackMatcher.find()) {
            String entityName = attackMatcher.group(1);
            return new Action.AttackEntity(entityName);
        }
        
        // Check for keyword-based actions
        if (lower.contains("idle") || lower.contains("stand still") || 
            lower.contains("do nothing") || lower.contains("wait")) {
            return new Action.Idle();
        }
        
        if (lower.contains("wander") || lower.contains("explore randomly")) {
            return new Action.Wander();
        }
        
        if (lower.contains("eat")) {
            return new Action.EatFood();
        }
        
        // If response contains coordinates without "walk to", try to extract them
        Pattern coordPattern = Pattern.compile("([-\\d.]+)[,\\s]+([-\\d.]+)");
        Matcher coordMatcher = coordPattern.matcher(trimmed);
        if (coordMatcher.find()) {
            try {
                double x = Double.parseDouble(coordMatcher.group(1));
                double z = Double.parseDouble(coordMatcher.group(2));
                return new Action.WalkTo(x, z);
            } catch (NumberFormatException e) {
                // Fall through
            }
        }
        
        // Default to IDLE if we can't parse anything
        return new Action.Idle();
    }
    
    /**
     * Parse with logging for debugging
     */
    public static Action parseWithLogging(String response, java.util.logging.Logger logger) {
        logger.info("Parsing LLM response: " + response);
        Action action = parse(response);
        logger.info("Parsed action: " + action);
        return action;
    }
}

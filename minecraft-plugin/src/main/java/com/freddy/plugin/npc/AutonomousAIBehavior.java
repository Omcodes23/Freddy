package com.freddy.plugin.npc;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.logging.Logger;

/**
 * Autonomous AI Behavior System - makes decisions and executes autonomously
 */
public class AutonomousAIBehavior {
    private static final Logger logger = Logger.getLogger("AI Behavior");
    
    private NPCController npcController;
    private AIActionExecutor executor;
    private GoalManager goalManager;
    private Player npcEntity;
    private int followSearchRadius = 20;
    
    private int tickCounter = 0;
    private int decisionInterval = 40;  // Make decisions every 2 seconds at 20 TPS
    
    public AutonomousAIBehavior(NPCController controller, AIActionExecutor executor, GoalManager goals, Player npc) {
        this.npcController = controller;
        this.executor = executor;
        this.goalManager = goals;
        this.npcEntity = npc;
    }

    /**
     * Update NPC entity reference (called from brain loop each tick)
     */
    public void setNPCEntity(Player npc) {
        this.npcEntity = npc;
    }
    
    /**
     * Main tick - called every game tick (20 TPS)
     */
    public void tick() {
        tickCounter++;
        
        // Execute queued actions every tick
        npcController.tick();
        
        // Make AI decisions every 2 seconds
        if (tickCounter >= decisionInterval) {
            tickCounter = 0;
            makeDecision();
        }
    }
    
    /**
     * AI Decision Making Logic
     */
    private void makeDecision() {
        if (npcEntity == null) {
            logger.warning("[AI] NPC entity is null");
            return;
        }
        
        Goal currentGoal = goalManager.getCurrentGoal();
        NPCInventory inventory = npcController.getInventory();
        
        // If no goal, explore
        if (currentGoal == null) {
            logger.info("[AI] No goal - exploring");
            executor.explore(40);
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null && tickCounter % decisionInterval == 0) {
                    t.send("ACTION:EXPLORING_NO_GOAL");
                }
            } catch (Exception ignore) { }
            return;
        }
        
        // Check if goal has steps
        if (currentGoal.getSteps().isEmpty()) {
            // Old simple goal execution (backward compatibility)
            executeSimpleGoal(currentGoal, inventory);
            return;
        }
        
        // Step-by-step execution
        GoalStep currentStep = currentGoal.getCurrentStep();
        if (currentStep == null) {
            // All steps completed
            logger.info("[AI] ✅ All steps completed for goal: " + currentGoal.getType());
            goalManager.completeCurrentGoal();
            return;
        }
        
        // Mark step as in progress
        if (currentStep.getStatus() == GoalStep.StepStatus.PENDING) {
            currentStep.setStatus(GoalStep.StepStatus.IN_PROGRESS);
            logger.info("[AI] 🔄 Starting step " + (currentGoal.getCurrentStepIndex() + 1) + "/" + currentGoal.getSteps().size() + ": " + currentStep.getLabel());
            // Telemetry: step started
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) {
                    t.send("GOAL_STEP_UPDATE:{\"id\":\"" + currentStep.getId() + "\",\"status\":\"IN_PROGRESS\"}");
                    t.send("ACTION:STEP " + (currentGoal.getCurrentStepIndex() + 1) + "/" + currentGoal.getSteps().size() + ": " + currentStep.getLabel());
                }
            } catch (Exception ignore) { }
        }
        
        // Execute current step
        executeStep(currentGoal, currentStep, inventory);
        
        // Auto-eat if hungry
        if (npcEntity.getFoodLevel() < 10) {
            logger.info("[AI] Eating food...");
            npcController.eatFood();
        }
        
        // Auto-pickup nearby items
        npcController.pickupNearbyItems();
    }
    
    private void executeStep(Goal goal, GoalStep step, NPCInventory inventory) {
        String goalType = goal.getType().name();
        String stepLabel = step.getLabel().toLowerCase();
        
        // Determine what to do based on step label and goal type
        switch (goalType) {
            case "GATHER_WOOD":
                // Check if we have enough wood
                if (inventory.getCount(Material.OAK_LOG) + inventory.getCount(Material.BIRCH_LOG) + 
                    inventory.getCount(Material.SPRUCE_LOG) + inventory.getCount(Material.JUNGLE_LOG) >= 64) {
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel() + " (collected enough wood)");
                    try {
                        com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                        if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                    } catch (Exception ignore) { }
                } else {
                    // Keep gathering wood
                    executor.gatherResource("WOOD");
                }
                break;
                
            case "GATHER_STONE":
                // Check if we have enough stone
                if (inventory.getCount(Material.STONE) >= 64) {
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel() + " (collected " + inventory.getCount(Material.STONE) + " stone)");
                    try {
                        com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                        if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                    } catch (Exception ignore) { }
                } else {
                    // Keep gathering stone
                    logger.info("[AI] Gathering stone... (" + inventory.getCount(Material.STONE) + "/64)");
                    executor.gatherResource("STONE");
                }
                break;
                
            case "MINE_DIAMONDS":
                // Check if we have enough diamonds
                if (inventory.getCount(Material.DIAMOND) >= 10) {
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel() + " (collected " + inventory.getCount(Material.DIAMOND) + " diamonds)");
                    try {
                        com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                        if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                    } catch (Exception ignore) { }
                } else {
                    // Keep mining diamonds
                    logger.info("[AI] Mining diamonds... (" + inventory.getCount(Material.DIAMOND) + "/10)");
                    executor.gatherResource("DIAMONDS");
                }
                break;
                
            case "HUNT_ANIMALS":
                // Check if we have enough food
                if (inventory.getCount(Material.COOKED_BEEF) + inventory.getCount(Material.COOKED_PORKCHOP) + 
                    inventory.getCount(Material.COOKED_CHICKEN) >= 32) {
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel() + " (collected enough food)");
                    try {
                        com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                        if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                    } catch (Exception ignore) { }
                } else {
                    // Keep hunting
                    executor.huntAnimals(30);
                }
                break;
                
            case "FARM_CROPS":
                // Check if we have enough crops
                if (inventory.getCount(Material.WHEAT) >= 32) {
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel() + " (collected " + inventory.getCount(Material.WHEAT) + " wheat)");
                    try {
                        com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                        if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                    } catch (Exception ignore) { }
                } else {
                    // Keep farming
                    executor.farmCrops(20);
                }
                break;
                
            case "EXPLORE_AREA":
                if (stepLabel.contains("explore")) {
                    long elapsed = goal.getElapsedTime();
                    if (elapsed > 60000) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    } else {
                        executor.explore(50);
                    }
                }
                break;
                
            case "BUILD_STRUCTURE":
                if (stepLabel.contains("gather") || stepLabel.contains("logs")) {
                    if (inventory.getCount(Material.OAK_LOG) >= 32) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    } else {
                        executor.gatherResource("WOOD");
                    }
                } else if (stepLabel.contains("build") || stepLabel.contains("pillar")) {
                    executor.buildPillar(5);
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                }
                break;
                
            case "FOLLOW_PLAYER":
                if (stepLabel.contains("find") || stepLabel.contains("nearest player")) {
                    // Try to detect a target player
                    Location npcLoc = npcEntity.getLocation();
                    Player target = null;
                    for (org.bukkit.entity.Entity entity : npcLoc.getWorld().getEntities()) {
                        if (entity instanceof Player && !entity.getName().equals(npcEntity.getName())) {
                            target = (Player) entity;
                            break;
                        }
                    }
                    if (target != null) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Player located: " + target.getName());
                        try {
                            com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                            if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                        } catch (Exception ignore) { }
                    } else {
                        // Expand search radius progressively
                        executor.explore(Math.min(followSearchRadius, 100));
                        followSearchRadius = Math.min(followSearchRadius + 10, 100);
                        logger.info("[AI] 🔍 Searching for player, radius=" + followSearchRadius);
                        // Keep step in progress until player is found
                    }
                } else if (stepLabel.contains("follow")) {
                    Location npcLoc = npcEntity.getLocation();
                    Player target = null;
                    for (org.bukkit.entity.Entity entity : npcLoc.getWorld().getEntities()) {
                        if (entity instanceof Player && !entity.getName().equals(npcEntity.getName())) {
                            target = (Player) entity;
                            break;
                        }
                    }
                    if (target != null) {
                        npcController.walkTo(target.getX(), target.getY(), target.getZ());
                    } else {
                        // Fallback: keep exploring and re-check next decision tick
                        executor.explore(20);
                        logger.info("[AI] 🤝 No player found, exploring while seeking...");
                    }
                }
                break;
                
            default:
                executor.explore(30);
                goal.completeCurrentStep();
                logger.info("[AI] ✓ Step complete: " + step.getLabel());
        }
    }
    
    // Backward compatibility for goals without steps
    private void executeSimpleGoal(Goal currentGoal, NPCInventory inventory) {
        String goalType = currentGoal.getType().name();
        logger.info("[AI] Executing goal: " + goalType);
        
        switch (goalType) {
            case "GATHER_WOOD":
                if (inventory.getCount(Material.OAK_LOG) >= 64) {
                    logger.info("[AI] ✅ Wood goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.gatherResource("WOOD");
                }
                break;
                
            case "GATHER_STONE":
                if (inventory.getCount(Material.STONE) >= 64) {
                    logger.info("[AI] ✅ Stone goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.gatherResource("STONE");
                }
                break;
                
            case "MINE_DIAMONDS":
                if (inventory.getCount(Material.DIAMOND) >= 10) {
                    logger.info("[AI] ✅ Diamond goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.gatherResource("DIAMONDS");
                }
                break;
                
            case "HUNT_ANIMALS":
                if (inventory.getCount(Material.COOKED_BEEF) >= 32 || 
                    inventory.getCount(Material.COOKED_PORKCHOP) >= 32) {
                    logger.info("[AI] ✅ Food goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.huntAnimals(30);
                }
                break;
                
            case "FARM_CROPS":
                if (inventory.getCount(Material.WHEAT) >= 32) {
                    logger.info("[AI] ✅ Farming goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.farmCrops(20);
                }
                break;
                
            case "EXPLORE_AREA":
                long elapsed = currentGoal.getElapsedTime();
                if (elapsed > 60000) {
                    logger.info("[AI] ✅ Exploration complete!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.explore(50);
                }
                break;
                
            case "BUILD_STRUCTURE":
                if (inventory.getCount(Material.OAK_LOG) >= 32) {
                    executor.buildPillar(5);
                    logger.info("[AI] ✅ Structure built!");
                    goalManager.completeCurrentGoal();
                } else {
                    logger.info("[AI] Gathering materials for building...");
                    executor.gatherResource("WOOD");
                }
                break;
                
            case "FOLLOW_PLAYER":
                logger.info("[AI] Following player...");
                Location npcLoc = npcEntity.getLocation();
                for (org.bukkit.entity.Entity entity : npcLoc.getWorld().getEntities()) {
                    if (entity instanceof Player && !entity.getName().equals(npcEntity.getName())) {
                        Player player = (Player) entity;
                        npcController.walkTo(player.getX(), player.getY(), player.getZ());
                        break;
                    }
                }
                break;
                
            default:
                executor.explore(30);
        }
        
        // Auto-eat if hungry
        if (npcEntity.getFoodLevel() < 10) {
            logger.info("[AI] Eating food...");
            npcController.eatFood();
        }
        
        // Auto-pickup nearby items
        npcController.pickupNearbyItems();
    }
    
    /**
     * Set a goal for the AI to pursue
     */
    public void setGoal(Goal.GoalType type, String description) {
        Goal goal = new Goal(type, description);
        goalManager.setGoal(goal);
        logger.info("[AI] New goal: " + type);
    }
    
    /**
     * Set a goal with steps
     */
    public void setGoalWithSteps(Goal.GoalType type, String description, List<GoalStep> steps) {
        Goal goal = new Goal(type, description);
        goal.setSteps(steps);
        goalManager.setGoal(goal);
        logger.info("[AI] New goal: " + type + " with " + steps.size() + " steps");
    }
    
    /**
     * Get current AI status
     */
    public String getStatus() {
        Goal current = goalManager.getCurrentGoal();
        if (current == null) {
            return "EXPLORING";
        }
        return "GOAL: " + current.getType() + " (" + current.getStatus() + ")";
    }
}

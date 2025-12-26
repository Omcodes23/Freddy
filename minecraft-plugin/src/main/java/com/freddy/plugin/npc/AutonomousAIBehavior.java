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
    
    private int tickCounter = 0;
    private int decisionInterval = 40;  // Make decisions every 2 seconds at 20 TPS
    
    public AutonomousAIBehavior(NPCController controller, AIActionExecutor executor, GoalManager goals, Player npc) {
        this.npcController = controller;
        this.executor = executor;
        this.goalManager = goals;
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
            logger.info("[AI] 🔄 Starting step " + (currentGoal.getCurrentStepIndex() + 1) + ": " + currentStep.getLabel());
            // Telemetry: step started
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + currentStep.getId() + "\",\"status\":\"IN_PROGRESS\"}");
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
                if (stepLabel.contains("navigate") || stepLabel.contains("forest")) {
                    executor.explore(30); // Navigate to forest
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    try {
                        com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                        if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                    } catch (Exception ignore) { }
                } else if (stepLabel.contains("collect") || stepLabel.contains("logs")) {
                    if (inventory.getCount(Material.OAK_LOG) >= 64) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    } else {
                        executor.gatherResource("WOOD");
                    }
                } else if (stepLabel.contains("return") || stepLabel.contains("base")) {
                    // TODO: Return to base - for now just complete
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    try {
                        com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                        if (t != null) t.send("GOAL_STEP_UPDATE:{\"id\":\"" + step.getId() + "\",\"status\":\"COMPLETED\"}");
                    } catch (Exception ignore) { }
                }
                break;
                
            case "GATHER_STONE":
                if (stepLabel.contains("find") || stepLabel.contains("stone") || stepLabel.contains("cave")) {
                    executor.explore(30);
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                } else if (stepLabel.contains("mine") || stepLabel.contains("blocks")) {
                    if (inventory.getCount(Material.STONE) >= 64) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    } else {
                        executor.gatherResource("STONE");
                    }
                }
                break;
                
            case "MINE_DIAMONDS":
                if (stepLabel.contains("locate") || stepLabel.contains("cave")) {
                    executor.explore(40);
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                } else if (stepLabel.contains("descend") || stepLabel.contains("diamond level")) {
                    executor.explore(30);
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                } else if (stepLabel.contains("mine") || stepLabel.contains("diamond")) {
                    if (inventory.getCount(Material.DIAMOND) >= 10) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    } else {
                        executor.gatherResource("DIAMONDS");
                    }
                }
                break;
                
            case "HUNT_ANIMALS":
                if (stepLabel.contains("locate") || stepLabel.contains("animals")) {
                    executor.explore(20);
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                } else if (stepLabel.contains("hunt") || stepLabel.contains("collect") || stepLabel.contains("food")) {
                    if (inventory.getCount(Material.COOKED_BEEF) >= 32 || 
                        inventory.getCount(Material.COOKED_PORKCHOP) >= 32) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    } else {
                        executor.huntAnimals(30);
                    }
                }
                break;
                
            case "FARM_CROPS":
                if (stepLabel.contains("find") || stepLabel.contains("farmable")) {
                    executor.explore(20);
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                } else if (stepLabel.contains("harvest") || stepLabel.contains("wheat") || stepLabel.contains("crops")) {
                    if (inventory.getCount(Material.WHEAT) >= 32) {
                        goal.completeCurrentStep();
                        logger.info("[AI] ✓ Step complete: " + step.getLabel());
                    } else {
                        executor.farmCrops(20);
                    }
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
                    executor.explore(20);
                    goal.completeCurrentStep();
                    logger.info("[AI] ✓ Step complete: " + step.getLabel());
                } else if (stepLabel.contains("follow")) {
                    Location npcLoc = npcEntity.getLocation();
                    for (org.bukkit.entity.Entity entity : npcLoc.getWorld().getEntities()) {
                        if (entity instanceof Player && !entity.getName().equals(npcEntity.getName())) {
                            Player player = (Player) entity;
                            npcController.walkTo(player.getX(), player.getY(), player.getZ());
                            break;
                        }
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

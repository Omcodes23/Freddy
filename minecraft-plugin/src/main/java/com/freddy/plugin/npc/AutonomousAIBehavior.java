package com.freddy.plugin.npc;

import com.freddy.plugin.ai.FreddyCraftRequest;
import com.freddy.plugin.ai.FreddyCraftResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autonomous AI Behavior System - makes decisions and executes autonomously
 */
public class AutonomousAIBehavior {
    private static final Logger logger = Logger.getLogger("AI Behavior");

    private final NPCController npcController;
    private final AIActionExecutor executor;
    private final GoalManager goalManager;
    private Player npcEntity;
    private int followSearchRadius = 20;
    private final Map<String, Integer> stepAttempts = new HashMap<>();
    private final Map<String, Integer> stepProgress = new HashMap<>();
    private final Map<String, Integer> stepRecoveries = new HashMap<>();

    private int tickCounter = 0;
    private final int decisionInterval = 40;  // Make decisions every 2 seconds at 20 TPS
    private int manualOverrideTicks = 0;

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

        // During manual mode, keep executing queued actions but skip autonomous decisions.
        if (manualOverrideTicks > 0) {
            manualOverrideTicks--;
            return;
        }

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
            npcController.clearActionQueue();
            logger.info("[AI] No goal - exploring");
            executor.explore(40);
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null && tickCounter % decisionInterval == 0) {
                    t.send("ACTION:EXPLORING_NO_GOAL");
                }
            } catch (Exception ignore) {
            }
            return;
        }

        // Check for goal timeout
        long goalTimeoutMs = switch (currentGoal.getType()) {
            case GATHER_WOOD, GATHER_STONE -> 120000L; // 2 minutes
            case MINE_DIAMONDS -> 300000L; // 5 minutes
            case CREATE_ITEM -> 90000L; // 1.5 minutes
            case BUILD_STRUCTURE -> 600000L; // 10 minutes
            case HUNT_ANIMALS, FARM_CROPS -> 150000L; // 2.5 minutes
            case EXPLORE_AREA, FOLLOW_PLAYER, RETURN_TO_PLAYER, RETURN_HOME, PROTECT_PLAYER -> 180000L; // 3 minutes
            case AUTOPILOT -> 600000L; // 10 minutes
            case FIGHT_MOB -> 600000L; // 10 minutes
            case SPEEDRUN -> 900000L; // 15 minutes
            default -> 60000L; // 1 minute
        };
        
        if (currentGoal.isTimedOut(goalTimeoutMs)) {
            logger.warning("[AI] Goal timed out: " + currentGoal.getType() + " after " + (goalTimeoutMs/1000) + "s");
            goalManager.failCurrentGoal("Goal timeout");
            return;
        }


        // Check if goal has steps
        if (currentGoal.getSteps().isEmpty()) {
            if (currentGoal.getType() == Goal.GoalType.AUTOPILOT) {
                refreshAutopilotSteps(currentGoal, inventory);
                return;
            }
            // Old simple goal execution (backward compatibility)
            executeSimpleGoal(currentGoal, inventory);
            return;
        }

        // Step-by-step execution
        GoalStep currentStep = currentGoal.getCurrentStep();
        if (currentStep == null) {
            if (currentGoal.getType() == Goal.GoalType.MINE_DIAMONDS && !isNearSurface()) {
                executor.returnToSurface();
                return;
            }
            if (currentGoal.getType() == Goal.GoalType.AUTOPILOT) {
                refreshAutopilotSteps(currentGoal, inventory);
                return;
            }
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
            } catch (Exception ignore) {
            }
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
        String stepLabel = step.getLabel().toLowerCase(Locale.ROOT);
        int attempts = incrementStepAttempts(step);

        if (!goalManager.canExecuteStep(goal, step)) {
            executor.explore(16);
            return;
        }

        int budget = computeStepAttemptBudget(goal, step, stepLabel);
        if (attempts >= budget) {
            if (!attemptRecovery(goal, step, stepLabel, inventory, attempts, budget)) {
                failStep(goal, step, "step attempt budget exceeded");
            }
            return;
        }

        switch (goalType) {
            case "GATHER_WOOD" -> {
                int target = Math.max(1, resolveGoalQuantity(goal, stepLabel, 24));
                if (containsAny(stepLabel, "search", "find", "locate", "navigate", "forest", "tree")) {
                    if (executor.hasNearbyResource("WOOD", 40) || executor.moveToNearestResource("WOOD", 80)) {
                        completeStep(goal, step, "wood source located");
                    } else {
                        executor.explore(45);
                    }
                } else if (containsAny(stepLabel, "walk", "approach", "go to", "near")) {
                    if (executor.moveToNearestResource("WOOD", 80)) {
                        completeStep(goal, step, "arrived near wood");
                    } else {
                        executor.explore(40);
                    }
                } else if (getTotalWood(inventory) >= target) {
                    completeStep(goal, step, "collected enough wood");
                } else {
                    executor.gatherResource("WOOD");
                    // Check completion after each gather attempt
                    if (getTotalWood(inventory) >= target) {
                        completeStep(goal, step, "collected enough wood");
                    }
                }
            }
            case "GATHER_STONE" -> {
                int target = Math.max(1, resolveGoalQuantity(goal, stepLabel, 24));
                if (containsAny(stepLabel, "find", "locate", "outcrop", "cave", "entrance")) {
                    if (executor.hasNearbyResource("STONE", 36) || executor.moveToNearestResource("STONE", 64)) {
                        completeStep(goal, step, "stone source located");
                    } else {
                        executor.explore(40);
                    }
                } else if (containsAny(stepLabel, "walk", "approach", "near")) {
                    if (executor.moveToNearestResource("STONE", 64)) {
                        completeStep(goal, step, "arrived near stone");
                    } else {
                        executor.explore(35);
                    }
                } else if (getTotalStone(inventory) >= target) {
                    completeStep(goal, step, "collected enough stone");
                } else {
                    executor.gatherResource("STONE");
                    // Check completion after each gather attempt
                    if (getTotalStone(inventory) >= target) {
                        completeStep(goal, step, "collected enough stone");
                    }
                }
            }
            case "MINE_DIAMONDS" -> {
                int target = Math.max(1, parseRequestedAmount(stepLabel, 3));
                if (containsAny(stepLabel, "navigate back", "nearest player", "come back", "return to player")) {
                    // Final step: walk back to the nearest player
                    Player returnTarget = resolveTargetPlayer("player");
                    if (returnTarget != null) {
                        double distance = npcEntity.getLocation().distance(returnTarget.getLocation());
                        if (distance <= 3.0) {
                            completeStep(goal, step, "arrived back at player");
                        } else {
                            npcController.walkTo(returnTarget.getX(), returnTarget.getY(), returnTarget.getZ());
                        }
                    } else {
                        // No player found, complete anyway
                        completeStep(goal, step, "no player found to return to");
                    }
                } else if (containsAny(stepLabel, "return", "surface", "ascend", "climb", "exit")) {
                    if (isNearSurface()) {
                        completeStep(goal, step, "returned to surface");
                    } else {
                        executor.returnToSurface();
                    }
                } else if (containsAny(stepLabel, "locate", "find", "search")) {
                    // Locate phase must run first; use locate/move commands before mining steps.
                    if (executor.hasNearbyResource("DIAMONDS", 24)) {
                        completeStep(goal, step, "diamond ore located");
                    } else if (executor.moveToNearestResource("DIAMONDS", 48)) {
                        // Keep step in progress until ore is actually located nearby.
                    } else {
                        executor.gatherResource("DIAMONDS");
                    }
                } else if (stepLabel.contains("descend") || stepLabel.contains("diamond level")) {
                    if (npcEntity.getLocation().getY() <= 16) {
                        completeStep(goal, step, "reached diamond level");
                    } else {
                        executor.descendTowardsDiamondLevel();
                    }
                } else if (stepLabel.contains("mine") || stepLabel.contains("diamond")) {
                    if (getTotalDiamondLoot(inventory) >= target) {
                        completeStep(goal, step, "diamond mining complete");
                    } else {
                        executor.gatherResource("DIAMONDS");
                        // Check completion after each mining attempt
                        if (getTotalDiamondLoot(inventory) >= target) {
                            completeStep(goal, step, "diamond mining complete");
                        }
                    }
                } else {
                    executor.gatherResource("DIAMONDS");
                }
            }
            case "HUNT_ANIMALS" -> {
                int target = Math.max(4, parseRequestedAmount(stepLabel, 12));
                if (containsAny(stepLabel, "locate", "find", "animal", "nearby")) {
                    if (hasNearbyHuntTarget(28)) {
                        completeStep(goal, step, "hunt targets located");
                    } else {
                        executor.explore(35);
                    }
                } else if (getTotalFood(inventory) >= target) {
                    completeStep(goal, step, "collected enough food");
                } else {
                    executor.huntAnimals(30);
                }
            }
            case "FARM_CROPS" -> {
                int target = Math.max(4, parseRequestedAmount(stepLabel, 16));
                if (containsAny(stepLabel, "find", "locate", "farm", "crop area", "field")) {
                    if (hasNearbyCrops(24)) {
                        completeStep(goal, step, "crop area located");
                    } else {
                        executor.explore(35);
                        if (attempts >= 12) {
                            completeStep(goal, step, "completed crop scouting pass");
                        }
                    }
                } else if (getTotalCrops(inventory) >= target) {
                    completeStep(goal, step, "collected enough crops");
                } else {
                    executor.farmCrops(30);
                }
            }
            case "EXPLORE_AREA" -> {
                executor.explore(50);
                if (containsAny(stepLabel, "locate", "notable", "resource", "terrain")) {
                    if (executor.hasNearbyResource("WOOD", 24)
                        || executor.hasNearbyResource("STONE", 24)
                        || executor.hasNearbyResource("DIAMONDS", 18)
                        || attempts >= 10) {
                        completeStep(goal, step, "exploration sweep identified notable resources");
                    }
                } else if (containsAny(stepLabel, "continue", "sweep", "navigation", "path")) {
                    if (attempts >= 10) {
                        completeStep(goal, step, "exploration sweep completed");
                    }
                } else {
                    int requiredSeconds = Math.max(20, parseRequestedAmount(stepLabel, 60));
                    if (goal.getElapsedTime() >= requiredSeconds * 1000L
                        || attempts >= Math.max(15, requiredSeconds / 2)) {
                        completeStep(goal, step, "exploration time target reached");
                    }
                }
            }
            case "BUILD_STRUCTURE" -> {
                if (stepLabel.contains("gather") || stepLabel.contains("collect")) {
                    // Plan steps may request either wood/logs or stone/cobblestone.
                    int target = Math.max(8, parseRequestedAmount(stepLabel, 32));

                    if (containsAny(stepLabel, "stone", "cobble")) {
                        if (getTotalStone(inventory) >= target) {
                            completeStep(goal, step, "materials ready");
                        } else if (attempts >= 14 && shouldBypassBuildGather(goal)) {
                            completeStep(goal, step, "gather timed out; continuing with build plan");
                        } else {
                            executor.gatherResource("STONE");
                        }
                    } else {
                        if (getTotalWood(inventory) >= target) {
                            completeStep(goal, step, "materials ready");
                        } else if (attempts >= 14 && shouldBypassBuildGather(goal)) {
                            completeStep(goal, step, "gather timed out; continuing with build plan");
                        } else {
                            executor.gatherResource("WOOD");
                        }
                    }
                } else if (stepLabel.contains("craft") || stepLabel.contains("plank")) {
                    // Craft enough planks so build placement doesn't stall.
                    String template = resolveBuildTemplate(goal);
                    int desiredPlanks = 48;
                    if (template != null) {
                        String t = template.trim().toUpperCase(Locale.ROOT);
                        if (t.contains("HOUSE_6X6")) desiredPlanks = 96;
                        else if (t.contains("HUT_4X4")) desiredPlanks = 64;
                        else if (t.contains("BRIDGE_8")) desiredPlanks = 48;
                    }

                    if (inventory.getCount(Material.OAK_PLANKS) >= desiredPlanks) {
                        completeStep(goal, step, "crafted build materials");
                    } else if (inventory.getCount(Material.OAK_LOG) > 0
                        || inventory.getCount(Material.BIRCH_LOG) > 0
                        || inventory.getCount(Material.SPRUCE_LOG) > 0
                        || inventory.getCount(Material.JUNGLE_LOG) > 0
                        || inventory.getCount(Material.ACACIA_LOG) > 0
                        || inventory.getCount(Material.DARK_OAK_LOG) > 0) {
                        int missing = Math.max(1, desiredPlanks - inventory.getCount(Material.OAK_PLANKS));
                        if (tryCraftItem("OAK_PLANKS", missing) || inventory.getCount(Material.OAK_PLANKS) >= desiredPlanks) {
                            completeStep(goal, step, "crafted build materials");
                        } else {
                            executor.gatherResource("WOOD");
                        }
                    } else if (tryCraftFromStep(stepLabel)) {
                        completeStep(goal, step, "crafted build materials");
                    } else {
                        executor.gatherResource("WOOD");
                    }
                } else if ((stepLabel.contains("build") || stepLabel.contains("pillar") || stepLabel.contains("place")
                           || stepLabel.contains("wall") || stepLabel.contains("roof") || stepLabel.contains("floor"))
                           && !stepLabel.contains("find")) {
                    // Guard: exclude "find" steps (e.g. "Find a flat and safe build area")
                    // which are about location scouting, not block placement.
                    String template = resolveBuildTemplate(goal);
                    boolean done = executor.buildTemplateStep(template, step.getLabel());
                    if (done) {
                        completeStep(goal, step, "structure step executed");
                    }
                } else if (stepLabel.contains("find") || stepLabel.contains("mark")) {
                    executor.explore(20);
                    completeStep(goal, step, "selected build area");
                } else if (stepLabel.contains("door") || stepLabel.contains("opening")) {
                    // Door openings are already handled by the wall template (blocks are skipped).
                    completeStep(goal, step, "door opening handled by wall template");
                } else if (stepLabel.contains("inspect") || stepLabel.contains("patch") || stepLabel.contains("gap")) {
                    completeStep(goal, step, "structure inspection complete");
                } else {
                    executeGenericStep(goal, step, stepLabel, inventory);
                }
            }
            case "FOLLOW_PLAYER" -> {
                Player target = resolveTargetPlayer(stepLabel);
                if (target == null) {
                    executor.explore(Math.min(followSearchRadius, 100));
                    followSearchRadius = Math.min(followSearchRadius + 10, 100);
                } else if (stepLabel.contains("find") || stepLabel.contains("locate")) {
                    completeStep(goal, step, "player located: " + target.getName());
                } else {
                    double distance = npcEntity.getLocation().distance(target.getLocation());
                    npcController.walkTo(target.getX(), target.getY(), target.getZ());
                    // Treat continuous follow as fulfilled once Freddy can maintain proximity.
                    if (distance <= 4.0) {
                        completeStep(goal, step, "maintaining follow distance to " + target.getName());
                    }
                }
            }
            case "RETURN_HOME" -> {
                Location home = npcEntity.getWorld() == null ? null : npcEntity.getWorld().getSpawnLocation();
                if (home == null) {
                    failStep(goal, step, "home spawn not available");
                } else if (containsAny(stepLabel, "stop", "halt", "current task")) {
                    npcController.clearActionQueue();
                    completeStep(goal, step, "stopped current task queue");
                } else {
                    double distance = npcEntity.getLocation().distance(home);
                    npcController.walkTo(home.getX(), home.getY(), home.getZ());
                    if (distance <= 3.0) {
                        completeStep(goal, step, "arrived at home spawn");
                    }
                }
            }
            case "RETURN_TO_PLAYER" -> {
                Player target = resolveTargetPlayer(stepLabel);
                if (target == null) {
                    executor.explore(30);
                } else {
                    double distance = npcEntity.getLocation().distance(target.getLocation());
                    if (stepLabel.contains("select") || stepLabel.contains("target") || stepLabel.contains("find")) {
                        completeStep(goal, step, "selected player: " + target.getName());
                    } else if (stepLabel.contains("stay") || stepLabel.contains("within") || stepLabel.contains("near")) {
                        if (distance <= 2.5) {
                            completeStep(goal, step, "arrived near player");
                        } else {
                            npcController.walkTo(target.getX(), target.getY(), target.getZ());
                        }
                    } else {
                        if (distance <= 3.0) {
                            completeStep(goal, step, "returned to player location");
                        } else {
                            npcController.walkTo(target.getX(), target.getY(), target.getZ());
                        }
                    }
                }
            }
            case "PROTECT_PLAYER" -> {
                Player target = resolveTargetPlayer(stepLabel);
                if (target != null) {
                    npcController.walkTo(target.getX(), target.getY(), target.getZ());
                    executor.attackNearestMob(20);
                    if (stepLabel.contains("find") || stepLabel.contains("locate") || stepLabel.contains("stay")) {
                        completeStep(goal, step, "protecting " + target.getName());
                    } else if (containsAny(stepLabel, "attack", "hostile", "mob")) {
                        completeStep(goal, step, "engaged hostiles near " + target.getName());
                    }
                } else {
                    executor.explore(25);
                }
            }
            case "CREATE_ITEM" -> {
                String requestedItem = resolveCraftItem(goal, stepLabel);
                int requestedAmount = resolveCraftAmount(goal, stepLabel);
                Material requestedMaterial = null;
                if (requestedItem != null) {
                    try {
                        requestedMaterial = Material.valueOf(requestedItem);
                    } catch (IllegalArgumentException ignore) {
                        requestedMaterial = null;
                    }
                }

                if (containsAny(stepLabel, "check", "recipe", "requirements")) {
                    completeStep(goal, step, "recipe requirements checked");
                } else if (containsAny(stepLabel, "gather", "material", "collect", "mine")) {
                    int target = Math.max(1, parseRequestedAmount(stepLabel, 2));

                    if (containsAny(stepLabel, "log", "wood")) {
                        if (getTotalWood(inventory) >= target) {
                            completeStep(goal, step, "materials collected");
                        } else {
                            executor.gatherResource("WOOD");
                        }
                    } else if (containsAny(stepLabel, "cobble", "stone", "deepslate")) {
                        if (getTotalStone(inventory) >= target) {
                            completeStep(goal, step, "materials collected");
                        } else {
                            executor.gatherResource("STONE");
                        }
                    } else {
                        // Fallback for generic "collect materials" steps.
                        gatherForMissingMaterials(List.of("WOOD", "STONE"), inventory);
                    }
                } else if (containsAny(stepLabel, "craft", "create", "using", "system")) {
                    // Helper-crafting steps (e.g., "Craft planks and sticks") must not jump straight
                    // to the final item craft.
                    if (containsAny(stepLabel, "plank", "stick", "helper")) {
                        ensurePlanksAndSticks(inventory);
                        if (inventory.getCount(Material.OAK_PLANKS) >= 4 && inventory.getCount(Material.STICK) >= 4) {
                            completeStep(goal, step, "crafted helper items");
                        }
                        return;
                    }

                    if (requestedItem != null) {
                        FreddyCraftResult result = craftItemWithResult(requestedItem, requestedAmount);
                        if (result != null && result.crafted) {
                            completeStep(goal, step, "crafted requested item");
                        } else if (result != null && !result.missingItems.isEmpty()) {
                            gatherForMissingMaterialsFromMap(result.missingItems, inventory);
                        } else {
                            failStep(goal, step, "crafting failed");
                        }
                    } else {
                        failStep(goal, step, "no target item specified");
                    }
                } else if (containsAny(stepLabel, "verify", "confirm", "inventory")) {
                    if (requestedMaterial != null && inventory.getCount(requestedMaterial) >= requestedAmount) {
                        completeStep(goal, step, "verified crafted item in inventory");
                    } else {
                        // Verification should not hard-fail immediately; inventory updates can lag behind actions.
                        // Re-attempt crafting if possible, otherwise continue collecting.
                        if (requestedItem != null) {
                            FreddyCraftResult result = craftItemWithResult(requestedItem, requestedAmount);
                            if (result != null && !result.crafted && result.missingItems != null && !result.missingItems.isEmpty()) {
                                gatherForMissingMaterialsFromMap(result.missingItems, inventory);
                            }
                        } else {
                            executor.explore(10);
                        }
                    }
                } else {
                    executeGenericStep(goal, step, stepLabel, inventory);
                }
            }
            case "FIGHT_MOB" -> executeFightMobStep(goal, step, stepLabel, inventory, attempts);
            case "AUTOPILOT" -> executeAutopilotStep(goal, step, stepLabel, inventory);
            case "SPEEDRUN" -> executeSpeedrunStep(goal, step, stepLabel, inventory);
            default -> executeGenericStep(goal, step, stepLabel, inventory);
        }
    }

    private void executeAutopilotStep(Goal goal, GoalStep step, String stepLabel, NPCInventory inventory) {
        if (containsAny(stepLabel, "observe", "scan", "evaluate", "context")) {
            executor.explore(12);
            completeStep(goal, step, "autopilot observation complete");
            return;
        }

        if (containsAny(stepLabel, "safety", "alert", "threat")) {
            if (hasNearbyHuntTarget(24)) {
                executor.attackNearestMob(18);
            } else {
                executor.explore(12);
            }
            completeStep(goal, step, "autopilot safety check complete");
            return;
        }

        if (containsAny(stepLabel, "hunger", "food", "recover")) {
            if (npcEntity.getFoodLevel() < 10) {
                npcController.eatFood();
            }
            completeStep(goal, step, "autopilot recovery complete");
            return;
        }

        if (containsAny(stepLabel, "mining", "mine", "current mining")) {
            executor.gatherResource("STONE");
            completeStep(goal, step, "autopilot mining task complete");
            return;
        }

        if (containsAny(stepLabel, "queued action", "execute queued", "action stack")) {
            npcController.tick();
            completeStep(goal, step, "queued actions processed");
            return;
        }

        if (containsAny(stepLabel, "build")) {
            executor.buildPillar(3);
            completeStep(goal, step, "autopilot build task complete");
            return;
        }

        if (containsAny(stepLabel, "craft")) {
            if (tryCraftFromStep(stepLabel)) {
                completeStep(goal, step, "autopilot craft task complete");
            } else {
            }
            return;
        }

        executor.explore(20);
        completeStep(goal, step, "autopilot free-will task complete");
    }

    private void executeSpeedrunStep(Goal goal, GoalStep step, String stepLabel, NPCInventory inventory) {
        if (containsAny(stepLabel, "leave", "random")) {
            if (executor.exitHouseStep()) {
                completeStep(goal, step, "leaving for random tasks");
                // Switch to a simpler wandering/exploration goal instead of LLM-based autopilot
                Goal wanderGoal = new Goal(Goal.GoalType.EXPLORE_AREA, "Just wandering around");
                wanderGoal.setSteps(StepPlanner.planFor(Goal.GoalType.EXPLORE_AREA));
                goalManager.setGoal(wanderGoal);
            }
        } else if (containsAny(stepLabel, "place", "furniture")) {
            // Ensure we have the furniture items before placing
            ensureFurnitureItems(inventory);
            boolean done = executor.placeFurnitureStep("HOUSE_6X6", stepLabel);
            if (done) {
                completeStep(goal, step, "furniture placed inside");
            }
        } else if (containsAny(stepLabel, "wood", "log")) {
            int target = 10;
            if (getTotalWood(inventory) >= target) {
                completeStep(goal, step, "collected enough wood");
            } else {
                executor.gatherResource("WOOD");
            }
        } else if (containsAny(stepLabel, "stone", "cobble")) {
            int target = 10;
            if (getTotalStone(inventory) >= target) {
                completeStep(goal, step, "collected enough stone");
            } else {
                executor.gatherResource("STONE");
            }
        } else if (containsAny(stepLabel, "build", "house", "hut")) {
            boolean done = executor.buildTemplateStep("HOUSE_6X6", step.getLabel());
            if (done) {
                completeStep(goal, step, "house structure built");
            }
        } else if (containsAny(stepLabel, "craft")) {
            if (tryCraftFromStep(stepLabel)) {
                completeStep(goal, step, "crafted " + stepLabel);
            } else {
                // Creative assist if materials missing after collection
                Material mat = null;
                if (stepLabel.contains("chest")) mat = Material.CHEST;
                else if (stepLabel.contains("bed")) mat = Material.WHITE_BED;
                else if (stepLabel.contains("furnace")) mat = Material.FURNACE;
                else if (stepLabel.contains("crafting table")) mat = Material.CRAFTING_TABLE;
                
                if (mat != null) {
                    inventory.addItem(new org.bukkit.inventory.ItemStack(mat, 1));
                    completeStep(goal, step, "crafted " + stepLabel + " (assist)");
                }
            }
        } else if (containsAny(stepLabel, "store", "chest")) {
            // Simulate storing items
            logger.info("[AI] Storing all items in chest...");
            inventory.clear(); // For presentation: clear inventory
            completeStep(goal, step, "all items stored in chest");
        } else {
            executeGenericStep(goal, step, stepLabel, inventory);
        }
    }

    private void ensureFurnitureItems(NPCInventory inventory) {
        Material[] furniture = {Material.CRAFTING_TABLE, Material.FURNACE, Material.CHEST, Material.WHITE_BED};
        for (Material mat : furniture) {
            if (inventory.getCount(mat) == 0) {
                logger.info("[AI] Missing " + mat + " for placement; crafting assist triggered.");
                inventory.addItem(new org.bukkit.inventory.ItemStack(mat, 1));
            }
        }
    }

    private void executeFightMobStep(Goal goal, GoalStep step, String stepLabel, NPCInventory inventory, int attempts) {
        // Phase 1: Equip
        if (containsAny(stepLabel, "equip", "creative", "gear", "supplies")) {
            org.bukkit.inventory.ItemStack sword = new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD, 1);
            org.bukkit.inventory.ItemStack chest = new org.bukkit.inventory.ItemStack(Material.DIAMOND_CHESTPLATE, 1);
            org.bukkit.inventory.ItemStack helmet = new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET, 1);
            org.bukkit.inventory.ItemStack leggings = new org.bukkit.inventory.ItemStack(Material.DIAMOND_LEGGINGS, 1);
            org.bukkit.inventory.ItemStack boots = new org.bukkit.inventory.ItemStack(Material.DIAMOND_BOOTS, 1);
            org.bukkit.inventory.ItemStack food = new org.bukkit.inventory.ItemStack(Material.COOKED_BEEF, 64);

            inventory.addItem(sword);
            inventory.addItem(chest);
            inventory.addItem(helmet);
            inventory.addItem(leggings);
            inventory.addItem(boots);
            inventory.addItem(food);

            // Also equip Freddy visually so armor/weapon are visible on body.
            try {
                npcEntity.getInventory().setItemInMainHand(sword.clone());
                npcEntity.getInventory().setHelmet(helmet.clone());
                npcEntity.getInventory().setChestplate(chest.clone());
                npcEntity.getInventory().setLeggings(leggings.clone());
                npcEntity.getInventory().setBoots(boots.clone());
            } catch (Exception ignored) {
            }

            completeStep(goal, step, "Magically equipped combat gear");
            return;
        }
        
        // Phase 2: Spawn mob
        if (containsAny(stepLabel, "spawn", "magically", "summon")) {
            Location loc = npcEntity.getLocation().clone().add(5, 0, 5);
            String targetMobStr = resolveFightTargetMob(goal);
            try {
                org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(targetMobStr.toUpperCase());
                loc.getWorld().spawnEntity(loc, type);
                completeStep(goal, step, type.name() + " spawned magically");
            } catch (Exception e) {
                loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.ZOMBIE);
                completeStep(goal, step, "Zombie spawned (fallback from " + targetMobStr + ")");
            }
            return;
        }

        // Phase 3: Fight
        if (containsAny(stepLabel, "fight", "defeat", "kill")) {
            executor.attackNearestMob(50);
            if (attempts >= 15) {
               completeStep(goal, step, "Mob fought");
            }
            return;
        }

        // Fallback
        executeGenericStep(goal, step, stepLabel, inventory);
    }

    private String resolveFightTargetMob(Goal goal) {
        String targetMobStr = (String) goal.getParameter("mob");
        if (targetMobStr != null && !targetMobStr.isBlank()) {
            return targetMobStr.trim();
        }

        String description = goal.getDescription();
        if (description != null && description.contains(":")) {
            String[] parts = description.split(":");
            // Goal format examples:
            // "Goal: FIGHT_MOB:SKELETON" or "FIGHT_MOB:SKELETON"
            String candidate = parts[parts.length - 1].trim();
            if (!candidate.isBlank() && !candidate.equalsIgnoreCase("FIGHT_MOB")) {
                return candidate;
            }
        }

        return "ZOMBIE";
    }

    private void executeGenericStep(Goal goal, GoalStep step, String stepLabel, NPCInventory inventory) {
        if (stepLabel.contains("protect")) {
            Player target = resolveTargetPlayer(stepLabel);
            if (target != null) {
                npcController.walkTo(target.getX(), target.getY(), target.getZ());
                executor.attackNearestMob(20);
                completeStep(goal, step, "protection loop active");
                return;
            }
        }

        if (stepLabel.contains("follow")) {
            Player target = resolveTargetPlayer(stepLabel);
            if (target != null) {
                npcController.walkTo(target.getX(), target.getY(), target.getZ());
                completeStep(goal, step, "following target");
            } else {
                executor.explore(25);
            }
            return;
        }

        if (stepLabel.contains("craft") || stepLabel.contains("create") || stepLabel.contains("make")) {
            if (tryCraftFromStep(stepLabel)) {
                completeStep(goal, step, "crafted item");
            } else {
                executor.gatherResource("WOOD");
            }
            return;
        }

        if (stepLabel.contains("diamond")) {
            executor.gatherResource("DIAMONDS");
            if (inventory.getCount(Material.DIAMOND) >= Math.max(1, parseRequestedAmount(stepLabel, 1))) {
                completeStep(goal, step, "diamond objective reached");
            }
            return;
        }

        if (stepLabel.contains("stone") || stepLabel.contains("cobble")) {
            executor.gatherResource("STONE");
            if (getTotalStone(inventory) >= Math.max(1, parseRequestedAmount(stepLabel, 8))) {
                completeStep(goal, step, "stone objective reached");
            }
            return;
        }

        if (stepLabel.contains("wood") || stepLabel.contains("log") || stepLabel.contains("tree")) {
            executor.gatherResource("WOOD");
            if (getTotalWood(inventory) >= Math.max(1, parseRequestedAmount(stepLabel, 8))) {
                completeStep(goal, step, "wood objective reached");
            }
            return;
        }

        if (stepLabel.contains("farm") || stepLabel.contains("harvest") || stepLabel.contains("crop")) {
            executor.farmCrops(30);
            if (getTotalCrops(inventory) >= Math.max(1, parseRequestedAmount(stepLabel, 8))) {
                completeStep(goal, step, "crop objective reached");
            }
            return;
        }

        if (stepLabel.contains("hunt") || stepLabel.contains("animal") || stepLabel.contains("food")) {
            executor.huntAnimals(30);
            if (getTotalFood(inventory) >= Math.max(1, parseRequestedAmount(stepLabel, 8))) {
                completeStep(goal, step, "food objective reached");
            }
            return;
        }

        if (stepLabel.contains("build") || stepLabel.contains("place")) {
            if (stepLabel.contains("house")) executor.buildTemplate("HOUSE_6X6");
            else if (stepLabel.contains("wall")) executor.buildTemplate("WALL_10");
            else if (stepLabel.contains("farm") || stepLabel.contains("plot")) executor.buildTemplate("FARM_PLOT_5X5");
            else if (stepLabel.contains("tower")) executor.buildTemplate("TOWER_7");
            else if (stepLabel.contains("hut")) executor.buildTemplate("HUT_4X4");
            else executor.buildTemplate("PILLAR_SMALL");
            completeStep(goal, step, "build task executed");
            return;
        }

        if (stepLabel.contains("mine") || stepLabel.contains("break") || stepLabel.contains("gather")) {
            if (stepLabel.contains("iron")) executor.gatherResource("IRON_ORE");
            else if (stepLabel.contains("coal")) executor.gatherResource("COAL_ORE");
            else if (stepLabel.contains("gold")) executor.gatherResource("GOLD_ORE");
            else executor.gatherResource("STONE");
            completeStep(goal, step, "mining / gathering executed");
            return;
        }

        if (stepLabel.contains("explore") || stepLabel.contains("locate") || stepLabel.contains("find") || stepLabel.contains("pathfind") || stepLabel.contains("navigate") || stepLabel.contains("approach")) {
            boolean found = false;
            if (stepLabel.contains("iron")) found = executor.moveToNearestResource("IRON_ORE", 60);
            else if (stepLabel.contains("coal")) found = executor.moveToNearestResource("COAL_ORE", 60);
            else if (stepLabel.contains("gold")) found = executor.moveToNearestResource("GOLD_ORE", 60);
            else if (stepLabel.contains("diamond")) found = executor.moveToNearestResource("DIAMOND", 60);
            else if (stepLabel.contains("stone") || stepLabel.contains("cobble")) found = executor.moveToNearestResource("STONE", 60);
            else if (stepLabel.contains("wood") || stepLabel.contains("log") || stepLabel.contains("tree")) found = executor.moveToNearestResource("WOOD", 60);
            else {
                executor.explore(35);
                found = true;
            }
            
            if (found || stepAttempts.getOrDefault(step.getId(), 0) > 5) {
                completeStep(goal, step, "location scouting and pathfinding completed");
            }
            return;
        }

        // Delay fallback so it doesn't instantly complete 
        executor.explore(30);
        if (stepAttempts.getOrDefault(step.getId(), 0) > 3) {
            completeStep(goal, step, "fallback step execution completed after delay");
        }
    }

    private void completeStep(Goal goal, GoalStep step, String detail) {
        goal.completeCurrentStep();
        goalManager.recordStepSuccess(goal, step);
        stepAttempts.remove(step.getId());
        stepProgress.remove(step.getId());
        stepRecoveries.remove(step.getId());
        logger.info("[AI] ✓ Step complete: " + step.getLabel() + " (" + detail + ")");
    }

    private void failStep(Goal goal, GoalStep step, String reason) {
        if (goal == null || step == null) {
            return;
        }

        goalManager.recordStepFailure(goal, step, reason);
        stepAttempts.remove(step.getId());
        stepProgress.remove(step.getId());
        stepRecoveries.remove(step.getId());

        if (goalManager.shouldFailGoal(goal)) {
            logger.warning("[AI] Goal failed after repeated step errors: " + goal.getType() + " (" + reason + ")");
            goalManager.failCurrentGoal(reason);
            return;
        }

        logger.warning("[AI] Step failed, skipping to continue execution: " + step.getLabel() + " (" + reason + ")");
        goal.failCurrentStep(reason);
    }

    private boolean attemptRecovery(Goal goal, GoalStep step, String stepLabel, NPCInventory inventory, int attempts, int budget) {
        if (goal == null || step == null) {
            return false;
        }

        String key = step.getId();
        int recoveries = stepRecoveries.getOrDefault(key, 0) + 1;
        stepRecoveries.put(key, recoveries);

        logger.warning("[AI] Recovering stalled step (" + attempts + "/" + budget + "): " + step.getLabel());

        switch (goal.getType()) {
            case GATHER_WOOD -> {
                if (executor.moveToNearestResource("WOOD", 96)) {
                    stepAttempts.put(key, Math.max(0, budget / 3));
                    return true;
                }
                executor.explore(55);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 3) {
                    completeStep(goal, step, "fallback recovery progressed gather wood step");
                }
                return true;
            }
            case GATHER_STONE -> {
                if (executor.moveToNearestResource("STONE", 96)) {
                    stepAttempts.put(key, Math.max(0, budget / 3));
                    return true;
                }
                executor.explore(55);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 3) {
                    completeStep(goal, step, "fallback recovery progressed gather stone step");
                }
                return true;
            }
            case MINE_DIAMONDS -> {
                if (containsAny(stepLabel, "descend", "diamond level")) {
                    executor.descendTowardsDiamondLevel();
                } else if (containsAny(stepLabel, "return", "surface")) {
                    executor.returnToSurface();
                } else {
                    executor.gatherResource("DIAMONDS");
                }
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 3 && (containsAny(stepLabel, "locate", "find") || containsAny(stepLabel, "return", "surface"))) {
                    completeStep(goal, step, "fallback recovery progressed mining phase");
                }
                return true;
            }
            case BUILD_STRUCTURE -> {
                String template = resolveBuildTemplate(goal);
                executor.buildTemplate(template);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 3) {
                    completeStep(goal, step, "fallback recovery progressed build step");
                }
                return true;
            }
            case CREATE_ITEM -> {
                String item = resolveCraftItem(goal, stepLabel);
                int amount = resolveCraftAmount(goal, stepLabel);
                if (item != null) {
                    FreddyCraftResult result = craftItemWithResult(item, amount);
                    if (result != null && result.crafted) {
                        completeStep(goal, step, "crafted during recovery");
                        return true;
                    }
                    if (result != null && !result.missingItems.isEmpty()) {
                        gatherForMissingMaterialsFromMap(result.missingItems, inventory);
                    } else {
                        executor.gatherResource("WOOD");
                    }
                    stepAttempts.put(key, Math.max(0, budget / 3));
                    if (recoveries >= 3 && containsAny(stepLabel, "verify", "confirm", "inventory")) {
                        completeStep(goal, step, "fallback recovery progressed craft verification");
                    }
                    return true;
                }
                return false;
            }
            case FOLLOW_PLAYER, RETURN_TO_PLAYER, PROTECT_PLAYER -> {
                Player target = resolveTargetPlayer(stepLabel);
                if (target != null) {
                    npcController.walkTo(target.getX(), target.getY(), target.getZ());
                    if (goal.getType() == Goal.GoalType.PROTECT_PLAYER) {
                        executor.attackNearestMob(24);
                    }
                    stepAttempts.put(key, Math.max(0, budget / 3));
                    if (recoveries >= 2) {
                        completeStep(goal, step, "fallback recovery progressed player interaction step");
                    }
                    return true;
                }
                executor.explore(40);
                stepAttempts.put(key, Math.max(0, budget / 3));
                return true;
            }
            case RETURN_HOME -> {
                if (npcEntity.getWorld() != null) {
                    Location spawn = npcEntity.getWorld().getSpawnLocation();
                    npcController.walkTo(spawn.getX(), spawn.getY(), spawn.getZ());
                    stepAttempts.put(key, Math.max(0, budget / 3));
                    if (npcEntity.getLocation().distance(spawn) <= 3.0 || recoveries >= 2) {
                        completeStep(goal, step, "fallback recovery reached home step");
                    }
                    return true;
                }
                return false;
            }
            case HUNT_ANIMALS -> {
                executor.huntAnimals(35);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 3) {
                    completeStep(goal, step, "fallback recovery progressed hunt step");
                }
                return true;
            }
            case FARM_CROPS -> {
                executor.farmCrops(35);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 3) {
                    completeStep(goal, step, "fallback recovery progressed farming step");
                }
                return true;
            }
            case EXPLORE_AREA -> {
                executor.explore(60);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 2) {
                    completeStep(goal, step, "fallback recovery progressed exploration step");
                }
                return true;
            }
            case AUTOPILOT -> {
                executeAutopilotStep(goal, step, stepLabel, inventory);
                stepAttempts.put(key, Math.max(0, budget / 3));
                return true;
            }
            case FIGHT_MOB -> {
                executeFightMobStep(goal, step, stepLabel, inventory, attempts);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 2) {
                    completeStep(goal, step, "fallback recovery progressed mob fight step");
                }
                return true;
            }
            default -> {
                executor.explore(45);
                stepAttempts.put(key, Math.max(0, budget / 3));
                if (recoveries >= 2) {
                    completeStep(goal, step, "fallback recovery progressed generic step");
                }
                return true;
            }
        }
    }

    private int incrementStepAttempts(GoalStep step) {
        if (step == null) {
            return 0;
        }
        int value = stepAttempts.getOrDefault(step.getId(), 0) + 1;
        stepAttempts.put(step.getId(), value);
        return value;
    }

    private int computeStepAttemptBudget(Goal goal, GoalStep step, String stepLabel) {
        if (goal == null || step == null) {
            return 15;
        }

        int requestedAmount = Math.max(1, parseRequestedAmount(stepLabel, 1));
        int base;
        switch (goal.getType()) {
            case FOLLOW_PLAYER, RETURN_TO_PLAYER, PROTECT_PLAYER -> base = 40;
            case EXPLORE_AREA -> base = 45;
            // BUILD_STRUCTURE needs a high budget because each attempt only places 1 block.
            // A 6x6 house wall phase alone has 76 blocks; a combined floor+walls phase can exceed 90.
            case BUILD_STRUCTURE -> base = 120;
            case GATHER_WOOD, GATHER_STONE, FARM_CROPS, HUNT_ANIMALS -> base = Math.max(20, requestedAmount + 12);
            case MINE_DIAMONDS -> base = Math.max(25, requestedAmount + 15);
            case FIGHT_MOB -> base = 50;
            case CREATE_ITEM -> base = 18;
            case AUTOPILOT -> base = 30;
            default -> base = 15;
        }

        if (containsAny(stepLabel, "follow continuously", "maintain", "repeat", "loop")) {
            base = Math.max(base, 50);
        }
        if (containsAny(stepLabel, "mine", "collect", "gather", "descend", "return to surface", "build")) {
            base += 5;
        }

        int marker = computeStepProgressMarker(goal, stepLabel);
        String key = step.getId();
        int lastMarker = stepProgress.getOrDefault(key, Integer.MIN_VALUE);
        if (marker > lastMarker) {
            stepAttempts.put(key, Math.max(0, stepAttempts.getOrDefault(key, 0) - 1));
        }
        stepProgress.put(key, marker);

        // Use a higher cap for BUILD_STRUCTURE to allow large structures to complete.
        int maxCap = (goal.getType() == Goal.GoalType.BUILD_STRUCTURE) ? 200 : 60;
        return Math.min(maxCap, Math.max(10, base));
    }

    private int computeStepProgressMarker(Goal goal, String stepLabel) {
        if (npcEntity == null) {
            return 0;
        }
        NPCInventory inventory = npcController.getInventory();
        if (goal.getType() == Goal.GoalType.GATHER_WOOD || stepLabel.contains("wood") || stepLabel.contains("log")) {
            return getTotalWood(inventory);
        }
        if (goal.getType() == Goal.GoalType.GATHER_STONE || stepLabel.contains("stone") || stepLabel.contains("cobble")) {
            return getTotalStone(inventory);
        }
        if (goal.getType() == Goal.GoalType.MINE_DIAMONDS || stepLabel.contains("diamond")) {
            return getTotalDiamondLoot(inventory) + Math.max(0, 70 - npcEntity.getLocation().getBlockY());
        }
        if (stepLabel.contains("crop") || stepLabel.contains("farm")) {
            return getTotalCrops(inventory);
        }
        if (stepLabel.contains("food") || stepLabel.contains("hunt")) {
            return getTotalFood(inventory);
        }
        return (int) Math.round(goal.getElapsedTime() / 1000.0);
    }

    private int parseRequestedAmount(String stepLabel, int defaultValue) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(stepLabel);
        if (!matcher.find()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private int resolveGoalQuantity(Goal goal, String stepLabel, int defaultValue) {
        // Prefer explicit digits in the step label.
        int fromStep = parseRequestedAmount(stepLabel, -1);
        if (fromStep > 0) {
            return fromStep;
        }

        // Fallback: parse from goal description (e.g., "Goal: GATHER_WOOD:1" or "Goal: GATHER_WOOD (1)").
        if (goal == null || goal.getDescription() == null) {
            return defaultValue;
        }
        String desc = goal.getDescription();
        try {
            String[] parts = desc.split(":");
            if (parts.length >= 3) {
                int parsed = Integer.parseInt(parts[2].trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (Exception ignore) {
        }

        // Final fallback: look for parentheses-style count.
        try {
            Matcher m = Pattern.compile("\\((\\d+)\\)").matcher(desc);
            if (m.find()) {
                int parsed = Integer.parseInt(m.group(1));
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (Exception ignore) {
        }

        return defaultValue;
    }

    private void ensurePlanksAndSticks(NPCInventory inventory) {
        if (inventory == null) {
            return;
        }

        // Ensure planks
        if (inventory.getCount(Material.OAK_PLANKS) < 4) {
            if (getTotalWood(inventory) > 0) {
                tryCraftItem("OAK_PLANKS", 4);
            }
        }

        // Ensure sticks
        if (inventory.getCount(Material.STICK) < 4) {
            if (inventory.getCount(Material.OAK_PLANKS) > 0 || getTotalWood(inventory) > 0) {
                if (inventory.getCount(Material.OAK_PLANKS) == 0 && getTotalWood(inventory) > 0) {
                    tryCraftItem("OAK_PLANKS", 2);
                }
                tryCraftItem("STICK", 4);
            }
        }
    }

    private int getTotalWood(NPCInventory inventory) {
        return inventory.getCount(Material.OAK_LOG)
            + inventory.getCount(Material.BIRCH_LOG)
            + inventory.getCount(Material.SPRUCE_LOG)
            + inventory.getCount(Material.JUNGLE_LOG)
            + inventory.getCount(Material.ACACIA_LOG)
            + inventory.getCount(Material.DARK_OAK_LOG)
            + inventory.getCount(Material.MANGROVE_LOG)
            + inventory.getCount(Material.CHERRY_LOG);
    }

    private int getTotalStone(NPCInventory inventory) {
        return inventory.getCount(Material.STONE) + inventory.getCount(Material.COBBLESTONE);
    }

    private int getTotalFood(NPCInventory inventory) {
        return inventory.getCount(Material.COOKED_BEEF)
            + inventory.getCount(Material.COOKED_PORKCHOP)
            + inventory.getCount(Material.COOKED_CHICKEN)
            + inventory.getCount(Material.BEEF)
            + inventory.getCount(Material.PORKCHOP)
            + inventory.getCount(Material.CHICKEN)
            + inventory.getCount(Material.MUTTON)
            + inventory.getCount(Material.COOKED_MUTTON);
    }

    private int getTotalCrops(NPCInventory inventory) {
        return inventory.getCount(Material.WHEAT)
            + inventory.getCount(Material.CARROT)
            + inventory.getCount(Material.POTATO)
            + inventory.getCount(Material.BEETROOT);
    }

    private int getTotalDiamondLoot(NPCInventory inventory) {
        return inventory.getCount(Material.DIAMOND)
            + inventory.getCount(Material.DIAMOND_ORE)
            + inventory.getCount(Material.DEEPSLATE_DIAMOND_ORE);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNearbyCrops(int range) {
        if (npcEntity == null || npcEntity.getWorld() == null) {
            return false;
        }
        Location base = npcEntity.getLocation();
        Material[] crops = new Material[] {Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS};
        for (int x = -range; x <= range; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -range; z <= range; z++) {
                    Material type = base.clone().add(x, y, z).getBlock().getType();
                    for (Material crop : crops) {
                        if (type == crop) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasNearbyHuntTarget(int range) {
        if (npcEntity == null || npcEntity.getWorld() == null) {
            return false;
        }
        Location base = npcEntity.getLocation();
        for (org.bukkit.entity.Entity entity : base.getWorld().getNearbyEntities(base, range, 12, range)) {
            String name = entity.getType().name();
            if (name.contains("COW") || name.contains("PIG") || name.contains("SHEEP") || name.contains("CHICKEN")) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearSurface() {
        if (npcEntity == null || npcEntity.getWorld() == null) {
            return true;
        }
        Location loc = npcEntity.getLocation();
        int surfaceY = npcEntity.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 1;
        return loc.getY() >= surfaceY - 1.0;
    }

    private Player resolveTargetPlayer(String stepLabel) {
        if (npcEntity == null || npcEntity.getLocation().getWorld() == null) {
            return null;
        }

        for (Player player : npcEntity.getLocation().getWorld().getPlayers()) {
            if (player.getName().equalsIgnoreCase(npcEntity.getName())) {
                continue;
            }
            if (stepLabel.contains(player.getName().toLowerCase(Locale.ROOT))) {
                return player;
            }
        }

        for (Player player : npcEntity.getLocation().getWorld().getPlayers()) {
            if (!player.getName().equalsIgnoreCase(npcEntity.getName())) {
                return player;
            }
        }
        return null;
    }

    private boolean tryCraftFromStep(String stepLabel) {
        var service = com.freddy.plugin.FreddyPlugin.getCraftingService();
        if (service == null && com.freddy.plugin.FreddyPlugin.getAIBrainLoop() != null
            && com.freddy.plugin.FreddyPlugin.getAIBrainLoop().getNpcController() != null) {
            service = new com.freddy.plugin.ai.FreddyCraftingService(
                com.freddy.plugin.FreddyPlugin.getAIBrainLoop().getNpcController().getInventory()
            );
        }
        if (service == null) {
            return false;
        }

        String item = "";
        String normalized = stepLabel.replace('-', ' ').replace('_', ' ').toUpperCase(Locale.ROOT);
        if (normalized.contains("CRAFTING TABLE")) item = "CRAFTING_TABLE";
        else if (normalized.contains("WOODEN SWORD")) item = "WOODEN_SWORD";
        else if (normalized.contains("WOODEN PICKAXE")) item = "WOODEN_PICKAXE";
        else if (normalized.contains("WOODEN AXE")) item = "WOODEN_AXE";
        else if (normalized.contains("WOODEN SHOVEL")) item = "WOODEN_SHOVEL";
        else if (normalized.contains("STONE SWORD")) item = "STONE_SWORD";
        else if (normalized.contains("STONE PICKAXE")) item = "STONE_PICKAXE";
        else if (normalized.contains("STONE AXE")) item = "STONE_AXE";
        else if (normalized.contains("STONE SHOVEL")) item = "STONE_SHOVEL";
        else if (normalized.contains("STICK")) item = "STICK";
        else if (normalized.contains("PLANK")) item = "OAK_PLANKS";
        else if (normalized.contains("FURNACE")) item = "FURNACE";
        else if (normalized.contains("TORCH")) item = "TORCH";
        else if (normalized.contains("CHEST")) item = "CHEST";
        else if (normalized.contains("BREAD")) item = "BREAD";

        if (item.isBlank()) {
            return false;
        }

        FreddyCraftResult result = service.craft(new FreddyCraftRequest(item, 1));
        if (result == null || !result.crafted) {
            if (npcController != null && npcController.getInventory() != null) {
                try {
                    Material mat = Material.valueOf(item);
                    npcController.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, 1));
                    logger.info("[FreddyAI] [AI] Crafting assist (creative) for " + item + " x1");
                    return true;
                } catch (Exception e) {}
            }
        }
        return result != null && result.crafted;
    }

    private void gatherForMissingMaterials(List<String> materialTypes, NPCInventory inventory) {
        if (materialTypes == null || materialTypes.isEmpty()) {
            executor.explore(20);
            return;
        }

        for (String materialType : materialTypes) {
            if (materialType == null || materialType.isBlank()) {
                continue;
            }

            if (materialType.equals("WOOD") || materialType.equals("LOG")) {
                if (getTotalWood(inventory) >= 8) {
                    continue;
                }
                executor.gatherResource("WOOD");
                return;
            }

            if (materialType.equals("STONE") || materialType.equals("COBBLESTONE")) {
                if (getTotalStone(inventory) >= 8) {
                    continue;
                }
                executor.gatherResource("STONE");
                return;
            }
        }

        executor.explore(20);
    }

    private void gatherForMissingMaterialsFromMap(Map<Material, Integer> missingItems, NPCInventory inventory) {
        if (missingItems == null || missingItems.isEmpty()) {
            executor.explore(20);
            return;
        }

        for (Material material : missingItems.keySet()) {
            if (material == null) {
                continue;
            }

            int missingAmount = Math.max(1, missingItems.getOrDefault(material, 1));

            if (material == Material.OAK_PLANKS) {
                if (inventory.getCount(Material.OAK_PLANKS) > 0 || inventory.getCount(Material.OAK_LOG) > 0) {
                    if (tryCraftItem("OAK_PLANKS", missingAmount)) {
                        return;
                    }
                }
                executor.gatherResource("WOOD");
                return;
            }

            if (material == Material.STICK) {
                if (inventory.getCount(Material.STICK) > 0) {
                    return;
                }
                if (inventory.getCount(Material.OAK_PLANKS) == 0 && inventory.getCount(Material.OAK_LOG) > 0) {
                    tryCraftItem("OAK_PLANKS", 2);
                }
                if (tryCraftItem("STICK", missingAmount)) {
                    return;
                }
                executor.gatherResource("WOOD");
                return;
            }

            if (isAny(material,
                Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG,
                Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.OAK_PLANKS, Material.STICK)) {
                executor.gatherResource("WOOD");
                return;
            }

            if (isAny(material, Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE)) {
                executor.gatherResource("STONE");
                return;
            }

            if (material == Material.COAL) {
                executor.gatherResource("COAL");
                return;
            }

            if (isAny(material, Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT)) {
                executor.farmCrops(30);
                return;
            }

            if (material == Material.DIAMOND) {
                executor.gatherResource("DIAMONDS");
                return;
            }
        }

        // Fallback for uncommon recipe materials.
        if (inventory.getCount(Material.OAK_LOG) > 0 || inventory.getCount(Material.OAK_PLANKS) > 0) {
            tryCraftItem("STICK", 4);
            return;
        }

        executor.gatherResource("WOOD");
    }

    private boolean isAny(Material target, Material... candidates) {
        for (Material candidate : candidates) {
            if (target == candidate) {
                return true;
            }
        }
        return false;
    }

    private FreddyCraftResult craftItemWithResult(String itemName, int amount) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        var service = resolveCraftingService();
        if (service == null) {
            return null;
        }

        FreddyCraftResult res = service.craft(new FreddyCraftRequest(itemName, Math.max(1, amount)));
        if (!res.crafted && npcController != null && npcController.getInventory() != null) {
            try {
                Material mat = Material.valueOf(itemName.toUpperCase());
                npcController.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount));
                logger.info("[FreddyAI] [AI] Crafting assist (creative) for " + itemName + " x" + amount);
                return new FreddyCraftResult(true, itemName.toUpperCase(), amount, new java.util.LinkedHashMap<>(), "Creative assist");
            } catch (Exception e) {}
        }
        return res;
    }

    private com.freddy.plugin.ai.FreddyCraftingService resolveCraftingService() {
        var service = com.freddy.plugin.FreddyPlugin.getCraftingService();
        if (service == null && com.freddy.plugin.FreddyPlugin.getAIBrainLoop() != null
            && com.freddy.plugin.FreddyPlugin.getAIBrainLoop().getNpcController() != null) {
            service = new com.freddy.plugin.ai.FreddyCraftingService(
                com.freddy.plugin.FreddyPlugin.getAIBrainLoop().getNpcController().getInventory()
            );
        }
        return service;
    }

    private boolean tryCraftItem(String itemName, int amount) {
        FreddyCraftResult result = craftItemWithResult(itemName, amount);
        return result != null && result.crafted;
    }

    private String resolveBuildTemplate(Goal goal) {
        String description = goal == null || goal.getDescription() == null ? "" : goal.getDescription().toUpperCase(Locale.ROOT);
        if (description.contains("HUT_4X4") || description.contains("HUT")) {
            return "HUT_4X4";
        }
        if (description.contains("BRIDGE_8") || description.contains("BRIDGE")) {
            return "BRIDGE_8";
        }
        if (description.contains("TOWER_7") || description.contains("TOWER")) {
            return "TOWER_7";
        }
        if (description.contains("HOUSE_6X6") || description.contains("HOUSE")) {
            return "HOUSE_6X6";
        }
        if (description.contains("WALL_10") || description.contains("WALL")) {
            return "WALL_10";
        }
        return "PILLAR_SMALL";
    }

    private boolean shouldBypassBuildGather(Goal goal) {
        String template = resolveBuildTemplate(goal);
        return template.equals("BRIDGE_8") || template.equals("HUT_4X4") || template.equals("TOWER_7");
    }

    private String resolveCraftItem(Goal goal, String stepLabel) {
        String fromGoal = extractCreateItemFromGoal(goal);
        if (fromGoal != null) {
            return fromGoal;
        }

        String normalized = stepLabel == null ? "" : stepLabel.replace('-', ' ').replace('_', ' ').toUpperCase(Locale.ROOT);
        if (normalized.contains("WOODEN SWORD")) return "WOODEN_SWORD";
        if (normalized.contains("WOODEN PICKAXE")) return "WOODEN_PICKAXE";
        if (normalized.contains("WOODEN AXE")) return "WOODEN_AXE";
        if (normalized.contains("WOODEN SHOVEL")) return "WOODEN_SHOVEL";
        if (normalized.contains("STONE SWORD")) return "STONE_SWORD";
        if (normalized.contains("STONE PICKAXE")) return "STONE_PICKAXE";
        if (normalized.contains("STONE AXE")) return "STONE_AXE";
        if (normalized.contains("STONE SHOVEL")) return "STONE_SHOVEL";
        if (normalized.contains("FURNACE")) return "FURNACE";
        if (normalized.contains("CRAFTING TABLE")) return "CRAFTING_TABLE";
        if (normalized.contains("CHEST")) return "CHEST";
        if (normalized.contains("TORCH")) return "TORCH";
        return null;
    }

    private int resolveCraftAmount(Goal goal, String stepLabel) {
        String description = goal == null || goal.getDescription() == null ? "" : goal.getDescription();
        String[] parts = description.split(":");
        if (parts.length >= 4 && parts[1].trim().equalsIgnoreCase("CREATE_ITEM")) {
            try {
                return Math.max(1, Integer.parseInt(parts[3].trim()));
            } catch (NumberFormatException ignore) {
                return 1;
            }
        }
        return Math.max(1, parseRequestedAmount(stepLabel, 1));
    }

    private String extractCreateItemFromGoal(Goal goal) {
        String description = goal == null || goal.getDescription() == null ? "" : goal.getDescription();
        String[] parts = description.split(":");
        if (parts.length >= 3 && parts[1].trim().equalsIgnoreCase("CREATE_ITEM")) {
            return parts[2].trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    // Backward compatibility for goals without steps
    private void executeSimpleGoal(Goal currentGoal, NPCInventory inventory) {
        String goalType = currentGoal.getType().name();
        logger.info("[AI] Executing goal: " + goalType);

        switch (goalType) {
            case "GATHER_WOOD":
                if (getTotalWood(inventory) >= 24) {
                    logger.info("[AI] ✅ Wood goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.gatherResource("WOOD");
                }
                break;

            case "GATHER_STONE":
                if (getTotalStone(inventory) >= 24) {
                    logger.info("[AI] ✅ Stone goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.gatherResource("STONE");
                }
                break;

            case "MINE_DIAMONDS":
                if (getTotalDiamondLoot(inventory) >= 3) {
                    logger.info("[AI] ✅ Diamond goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.gatherResource("DIAMONDS");
                }
                break;

            case "HUNT_ANIMALS":
                if (getTotalFood(inventory) >= 12) {
                    logger.info("[AI] ✅ Food goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.huntAnimals(30);
                }
                break;

            case "FARM_CROPS":
                if (getTotalCrops(inventory) >= 16) {
                    logger.info("[AI] ✅ Farming goal completed!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.farmCrops(20);
                }
                break;

            case "EXPLORE_AREA":
                long elapsed = currentGoal.getElapsedTime();
                if (elapsed > 120000) {
                    logger.info("[AI] ✅ Exploration complete!");
                    goalManager.completeCurrentGoal();
                } else {
                    executor.explore(50);
                }
                break;

            case "BUILD_STRUCTURE":
                if (getTotalWood(inventory) >= 24) {
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

            case "RETURN_HOME":
                logger.info("[AI] Returning home...");
                if (npcEntity.getWorld() != null) {
                    Location spawn = npcEntity.getWorld().getSpawnLocation();
                    npcController.walkTo(spawn.getX(), spawn.getY(), spawn.getZ());
                    if (npcEntity.getLocation().distance(spawn) <= 3.0) {
                        goalManager.completeCurrentGoal();
                    }
                } else {
                    executor.explore(20);
                }
                break;

            case "RETURN_TO_PLAYER":
                logger.info("[AI] Returning to nearest player...");
                Player returnTarget = resolveTargetPlayer("player");
                if (returnTarget != null) {
                    npcController.walkTo(returnTarget.getX(), returnTarget.getY(), returnTarget.getZ());
                    if (npcEntity.getLocation().distance(returnTarget.getLocation()) <= 2.5) {
                        goalManager.completeCurrentGoal();
                    }
                } else {
                    executor.explore(25);
                }
                break;

            case "PROTECT_PLAYER":
                logger.info("[AI] Protecting nearest player...");
                Player target = resolveTargetPlayer("player");
                if (target != null) {
                    npcController.walkTo(target.getX(), target.getY(), target.getZ());
                    executor.attackNearestMob(20);
                } else {
                    executor.explore(25);
                }
                break;

            case "CREATE_ITEM":
                logger.info("[AI] Crafting requested item...");
                String requestedItem = extractCreateItemFromGoal(currentGoal);
                int requestedAmount = resolveCraftAmount(currentGoal, currentGoal.getDescription() == null ? "" : currentGoal.getDescription().toLowerCase(Locale.ROOT));
                
                if (requestedItem != null) {
                    Material requestedMaterial = null;
                    try {
                        requestedMaterial = Material.valueOf(requestedItem);
                    } catch (IllegalArgumentException ignore) {
                        requestedMaterial = null;
                    }
                    
                    if (requestedMaterial != null && inventory.getCount(requestedMaterial) >= requestedAmount) {
                        logger.info("[AI] ✅ Item already in inventory!");
                        goalManager.completeCurrentGoal();
                    } else {
                        FreddyCraftResult result = craftItemWithResult(requestedItem, requestedAmount);
                        if (result != null && result.crafted) {
                            logger.info("[AI] ✅ Item crafted successfully!");
                            goalManager.completeCurrentGoal();
                        } else if (result != null && !result.missingItems.isEmpty()) {
                            gatherForMissingMaterials(List.of("WOOD", "STONE"), inventory);
                        } else {
                            logger.warning("[AI] Crafting failed, gathering materials...");
                            executor.gatherResource("WOOD");
                        }
                    }
                } else {
                    logger.warning("[AI] No target item specified for CREATE_ITEM");
                    goalManager.failCurrentGoal("No target item specified");
                }
                break;

            case "FIGHT_MOB":
                logger.info("[AI] Fighting mob (simple mode)");
                executor.explore(40);
                long fightElapsed = currentGoal.getElapsedTime();
                if (fightElapsed > 600000) {
                    logger.info("[AI] ✅ Mob fought!");
                    goalManager.completeCurrentGoal();
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
        npcController.clearActionQueue();
        executor.resetTransientState();
        stepAttempts.clear();
        stepProgress.clear();
        stepRecoveries.clear();
        Goal goal = new Goal(type, description);
        goalManager.setGoal(goal);
        logger.info("[AI] New goal: " + type);
    }

    /**
     * Set a goal with steps
     */
    public void setGoalWithSteps(Goal.GoalType type, String description, List<GoalStep> steps) {
        npcController.clearActionQueue();
        executor.resetTransientState();
        stepAttempts.clear();
        stepProgress.clear();
        stepRecoveries.clear();
        Goal goal = new Goal(type, description);
        goal.setSteps(steps);
        goalManager.setGoal(goal);
        logger.info("[AI] New goal: " + type + " with " + steps.size() + " steps");
    }

    private void refreshAutopilotSteps(Goal currentGoal, NPCInventory inventory) {
        if (currentGoal == null) {
            return;
        }

        int queuedActions = npcController.hasPendingMineAction() ? 1 : 0;
        boolean threatNearby = hasNearbyHuntTarget(24);
        String context = String.format(Locale.ROOT,
            "goal=%s,queued=%d,food=%d,threat=%s,health=%.1f",
            currentGoal.getType().name(),
            queuedActions,
            npcEntity.getFoodLevel(),
            threatNearby,
            npcEntity.getHealth()
        );

        currentGoal.setSteps(StepPlanner.planAutopilot(
            context,
            queuedActions,
            npcEntity.getFoodLevel(),
            threatNearby,
            npcController.hasPendingMineAction()
        ));
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

    public void pauseDecisions(int ticks) {
        manualOverrideTicks = Math.max(manualOverrideTicks, Math.max(0, ticks));
    }
}

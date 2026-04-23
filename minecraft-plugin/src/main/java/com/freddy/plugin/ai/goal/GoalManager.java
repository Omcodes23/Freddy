package com.freddy.plugin.ai.goal;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyWorldState;
import com.freddy.plugin.ai.action.Action;
import com.freddy.plugin.ai.action.ActionResult;
import com.freddy.plugin.ai.planning.DeterministicPlanner;
import com.freddy.plugin.ai.planning.PlanNode;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * GoalManager - Core autonomous agent loop
 * 
 * This is the brain of the autonomous system.
 * 
 * Flow:
 * 1. Maintain goal queue
 * 2. For active goal:
 * - Sense world state
 * - Plan actions deterministically
 * - Execute actions
 * - Verify progress
 * - Replan on failure
 * 3. Mark goals as COMPLETED or BLOCKED
 * 
 * Golden Rule: Never repeat the same failed action without state change
 */
public class GoalManager {

    private final FreddyPlugin plugin;
    private final DeterministicPlanner planner;
    private final com.freddy.plugin.ai.WorldStateBuilder worldStateBuilder;
    private final Queue<Goal> goals;

    // Blacklist for preventing infinite loops
    private final Set<String> actionBlacklist;

    // Current execution state
    private boolean running;
    private Goal currentGoal;
    private PlanNode currentPlan;

    public GoalManager(FreddyPlugin plugin, DeterministicPlanner planner) {
        this.plugin = plugin;
        this.planner = planner;
        this.worldStateBuilder = new com.freddy.plugin.ai.WorldStateBuilder(plugin);
        this.goals = new LinkedList<>();
        this.actionBlacklist = new HashSet<>();
        this.running = false;
    }

    /**
     * Add a goal to the queue
     */
    public void addGoal(Goal goal) {
        goals.add(goal);
        plugin.getLogger().info("Goal added: " + goal);

        // Start processing if not already running
        if (!running) {
            startProcessing();
        }
    }

    /**
     * Get current goal being worked on
     */
    public Goal getCurrentGoal() {
        return currentGoal;
    }

    /**
     * Check if manager is actively working on goals
     */
    public boolean isActive() {
        return running && currentGoal != null;
    }

    /**
     * Get all pending goals
     */
    public List<Goal> getPendingGoals() {
        return new ArrayList<>(goals);
    }

    /**
     * Start the autonomous agent loop
     */
    public void startProcessing() {
        if (running) {
            return;
        }

        running = true;
        plugin.getLogger().info("GoalManager: Starting autonomous processing");

        // Run the agent loop asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                processGoals();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }

    /**
     * Stop processing goals
     */
    public void stopProcessing() {
        running = false;
        plugin.getLogger().info("GoalManager: Stopped processing");
    }

    /**
     * Core agent loop - processes goals continuously
     */
    private void processGoals() {
        if (!running) {
            return;
        }

        // [1] Get next goal if not currently working on one
        if (currentGoal == null) {
            currentGoal = goals.poll();

            if (currentGoal == null) {
                // No goals to work on
                running = false;
                return;
            }

            plugin.getLogger().info("Starting work on goal: " + currentGoal);
            actionBlacklist.clear(); // Reset blacklist for new goal
        }

        // [2] Check if goal is still active
        if (currentGoal.status != GoalStatus.ACTIVE) {
            plugin.getLogger().info("Goal completed or blocked: " + currentGoal);
            currentGoal = null;
            currentPlan = null;
            return;
        }

        // [3] Check if goal has exceeded attempts
        if (currentGoal.hasExceededAttempts()) {
            plugin.getLogger().warning("Goal exceeded max attempts: " + currentGoal);
            currentGoal.block(FailureReason.TIMEOUT);
            currentGoal = null;
            currentPlan = null;
            return;
        }

        // [4] Record attempt
        currentGoal.recordAttempt();

        // [5] Sense world state
        FreddyWorldState state = senseWorld();

        // [6] Plan if needed
        if (currentPlan == null) {
            plugin.getLogger().info("Creating plan for: " + currentGoal.target.name());
            currentPlan = planner.plan(currentGoal.target, currentGoal.quantity);
        }

        // [7] Execute plan
        executeCurrentPlan(state);
    }

    /**
     * Execute the current plan asynchronously
     */
    private void executeCurrentPlan(FreddyWorldState state) {
        // Convert plan to actions
        List<Action> actions = planner.planToActions(currentPlan);

        plugin.getLogger().info(String.format("Executing plan with %d actions", actions.size()));

        // Execute actions recursively (async)
        executeActionsRecursive(state, actions, 0);
    }

    /**
     * Execute actions recursively in sequence (async)
     * 
     * @param state   World state
     * @param actions List of actions to execute
     * @param index   Current action index
     */
    private void executeActionsRecursive(FreddyWorldState state, List<Action> actions, int index) {
        // Base case: all actions executed
        if (index >= actions.size()) {
            // All actions succeeded - verify goal completion
            if (verifyGoalCompletion()) {
                plugin.getLogger().info("✓ Goal completed: " + currentGoal);
                currentGoal.complete();
                currentGoal = null;
                currentPlan = null;
            } else {
                plugin.getLogger().warning("Actions completed but goal not achieved, replanning");
                replan(FailureReason.EXECUTION);
            }
            return;
        }

        // Get current action
        Action action = actions.get(index);

        // Check if action is blacklisted
        String actionKey = getActionKey(action);
        if (actionBlacklist.contains(actionKey)) {
            plugin.getLogger().warning("Action is blacklisted, replanning: " + action.describe());
            replan(FailureReason.EXECUTION);
            return;
        }

        plugin.getLogger().info(String.format("(%d/%d) Executing: %s",
                index + 1, actions.size(), action.describe()));

        // Execute action asynchronously
        action.executeAsync(state, result -> {
            // Handle result
            switch (result) {
                case SUCCESS:
                    plugin.getLogger().info("✓ " + action.describe());
                    // Continue to next action
                    executeActionsRecursive(state, actions, index + 1);
                    break;

                case FAILED_TEMPORARY:
                    plugin.getLogger().warning("Temporary failure: " + action.describe());
                    // Don't blacklist, might work next time
                    replan(FailureReason.EXECUTION);
                    break;

                case FAILED_PERMANENT:
                    plugin.getLogger().severe("Permanent failure: " + action.describe());
                    blacklistAction(action);
                    replan(FailureReason.EXECUTION);
                    break;
            }
        });
    }

    /**
     * Replan current goal
     */
    private void replan(FailureReason reason) {
        plugin.getLogger().info("Replanning due to: " + reason);
        currentPlan = null; // Force replanning on next iteration

        // If we've tried too many times, block the goal
        if (currentGoal.attemptCount >= 5) {
            plugin.getLogger().warning("Too many replan attempts, blocking goal");
            currentGoal.block(reason);
        }
    }

    /**
     * Blacklist an action to prevent infinite loops
     */
    private void blacklistAction(Action action) {
        String key = getActionKey(action);
        actionBlacklist.add(key);
        plugin.getLogger().warning("Blacklisted action: " + action.describe());
    }

    /**
     * Get unique key for an action
     */
    private String getActionKey(Action action) {
        return action.getClass().getSimpleName() + ":" + action.describe();
    }

    /**
     * Verify that the current goal has been achieved
     */
    private boolean verifyGoalCompletion() {
        if (currentGoal == null) {
            return false;
        }

        // Check if inventory now contains the target item
        int count = plugin.getFreddyInventory().count(currentGoal.target);
        return count >= currentGoal.quantity;
    }

    /**
     * Sense the current world state
     */
    private FreddyWorldState senseWorld() {
        return worldStateBuilder.build();
    }
}

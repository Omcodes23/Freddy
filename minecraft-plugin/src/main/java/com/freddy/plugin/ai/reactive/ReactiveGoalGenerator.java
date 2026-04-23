package com.freddy.plugin.ai.reactive;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyWorldState;
import com.freddy.plugin.ai.goal.Goal;
import com.freddy.plugin.ai.goal.GoalManager;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * ReactiveGoalGenerator - Automatically creates goals based on world state
 * 
 * This enables true autonomous behavior where Freddy creates goals
 * without player commands.
 * 
 * Examples:
 * - Night falls → Create shelter goal
 * - Low health → Create food goal
 * - Tool broken → Create replacement goal
 * - No goals active → Create exploration goal
 */
public class ReactiveGoalGenerator {

    private final FreddyPlugin plugin;
    private final GoalManager goalManager;

    private boolean enabled;
    private long lastCheckTime;

    // Cooldowns to prevent spam
    private long lastShelterGoalTime;
    private long lastFoodGoalTime;

    private static final long SHELTER_COOLDOWN = 60000; // 1 minute
    private static final long FOOD_COOLDOWN = 30000; // 30 seconds

    public ReactiveGoalGenerator(FreddyPlugin plugin, GoalManager goalManager) {
        this.plugin = plugin;
        this.goalManager = goalManager;
        this.enabled = false;
        this.lastCheckTime = 0;
        this.lastShelterGoalTime = 0;
        this.lastFoodGoalTime = 0;
    }

    /**
     * Start the reactive goal generation loop
     */
    public void start() {
        if (enabled) {
            return;
        }

        enabled = true;
        plugin.getLogger().info("ReactiveGoalGenerator: Started");

        // Run checks every 10 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) {
                    cancel();
                    return;
                }
                checkAndGenerateGoals();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds
    }

    /**
     * Stop the reactive goal generation
     */
    public void stop() {
        enabled = false;
        plugin.getLogger().info("ReactiveGoalGenerator: Stopped");
    }

    /**
     * Check world state and generate appropriate goals
     */
    private void checkAndGenerateGoals() {
        lastCheckTime = System.currentTimeMillis();

        // Don't generate if already working on a goal
        if (goalManager.isActive()) {
            return;
        }

        // Build current world state
        FreddyWorldState state = new com.freddy.plugin.ai.WorldStateBuilder(plugin).build();

        // [1] Night detection → Shelter goal
        if (shouldCreateShelterGoal(state)) {
            createShelterGoal();
            return; // Only create one goal at a time
        }

        // [2] Low health / No food → Food goal
        if (shouldCreateFoodGoal(state)) {
            createFoodGoal();
            return;
        }

        // [3] No active goals → Default exploration/gathering goal
        if (shouldCreateDefaultGoal(state)) {
            createDefaultGoal();
            return;
        }
    }

    /**
     * Check if shelter goal should be created
     */
    private boolean shouldCreateShelterGoal(FreddyWorldState state) {
        long now = System.currentTimeMillis();

        // Cooldown check
        if (now - lastShelterGoalTime < SHELTER_COOLDOWN) {
            return false;
        }

        // Check if it's night (time > 13000 and < 23000 in MC ticks)
        // For now, we'll use a simple check based on player context
        // A real implementation would check the actual world time

        // Check if unsafe (no solid block below or lava nearby)
        if (!state.standingOnSolidBlock || state.lavaBelow || state.lavaNearby) {
            return true;
        }

        return false;
    }

    /**
     * Check if food goal should be created
     */
    private boolean shouldCreateFoodGoal(FreddyWorldState state) {
        long now = System.currentTimeMillis();

        // Cooldown check
        if (now - lastFoodGoalTime < FOOD_COOLDOWN) {
            return false;
        }

        // Check if player nearby has low health
        if (state.playerNearby && state.playerHealth < 10.0) {
            return true;
        }

        return false;
    }

    /**
     * Check if default goal should be created
     */
    private boolean shouldCreateDefaultGoal(FreddyWorldState state) {
        // Only if no goals in queue and not currently active
        return goalManager.getPendingGoals().isEmpty() && !goalManager.isActive();
    }

    /**
     * Create shelter goal
     */
    private void createShelterGoal() {
        plugin.getLogger().info("ReactiveGoalGenerator: Creating shelter goal");

        // Simple shelter = house with door
        Goal goal = new Goal(Material.OAK_DOOR, 1);
        goalManager.addGoal(goal);

        lastShelterGoalTime = System.currentTimeMillis();
    }

    /**
     * Create food goal
     */
    private void createFoodGoal() {
        plugin.getLogger().info("ReactiveGoalGenerator: Creating food goal");

        // Gather bread as basic food
        Goal goal = new Goal(Material.BREAD, 5);
        goalManager.addGoal(goal);

        lastFoodGoalTime = System.currentTimeMillis();
    }

    /**
     * Create default exploration/gathering goal
     */
    private void createDefaultGoal() {
        plugin.getLogger().info("ReactiveGoalGenerator: Creating default gathering goal");

        // Default: Gather basic materials (cobblestone)
        Goal goal = new Goal(Material.COBBLESTONE, 64);
        goalManager.addGoal(goal);
    }

    /**
     * Check if generator is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}

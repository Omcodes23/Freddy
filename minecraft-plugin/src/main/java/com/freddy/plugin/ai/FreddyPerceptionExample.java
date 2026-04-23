package com.freddy.plugin.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.citizensnpcs.api.npc.NPC;

/**
 * FreddyPerceptionExample - Usage Examples
 * 
 * This class demonstrates how to use the FreddyPerception system.
 * It can be integrated into your existing Freddy AI logic.
 */
public class FreddyPerceptionExample {

    private final FreddyPerception perception;
    private final Gson gson;

    public FreddyPerceptionExample(NPC freddy) {
        this.perception = new FreddyPerception(freddy);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // =========================
    // BASIC USAGE
    // =========================

    /**
     * Example 1: Basic world observation
     * 
     * This returns a complete snapshot of Freddy's surroundings
     * without any specific queries.
     */
    public FreddyWorldState observeWorld() {
        return perception.observe();
    }

    /**
     * Example 2: Get JSON representation (for LLM)
     * 
     * This converts the world state to JSON format,
     * ready to be sent to an LLM for reasoning.
     */
    public String getWorldStateAsJson() {
        FreddyWorldState state = perception.observe();
        return gson.toJson(state);
    }

    // =========================
    // QUERY USAGE
    // =========================

    /**
     * Example 3: Find nearest lava
     * 
     * Useful for obsidian creation or danger avoidance
     */
    public FreddyWorldState findNearestLava() {
        FreddyQuery query = new FreddyQuery();
        query.blockType = "LAVA";
        query.radius = 12;
        query.nearestOnly = true;

        return perception.observe(query);
    }

    /**
     * Example 4: Find nearest diamond ore
     * 
     * For mining tasks
     */
    public FreddyWorldState findNearestDiamondOre() {
        FreddyQuery query = new FreddyQuery();
        query.blockType = "DIAMOND_ORE";
        query.radius = 10;
        query.nearestOnly = true;

        return perception.observe(query);
    }

    /**
     * Example 5: Find nearest zombie
     * 
     * For combat/protection mode
     */
    public FreddyWorldState findNearestZombie() {
        FreddyQuery query = new FreddyQuery();
        query.mobType = "ZOMBIE";
        query.radius = 8;
        query.nearestOnly = true;

        return perception.observe(query);
    }

    /**
     * Example 6: Find obsidian for portal building
     */
    public FreddyWorldState findNearestObsidian() {
        FreddyQuery query = new FreddyQuery();
        query.blockType = "OBSIDIAN";
        query.radius = 15;
        query.nearestOnly = true;

        return perception.observe(query);
    }

    // =========================
    // DECISION-MAKING HELPERS
    // =========================

    /**
     * Example 7: Check if a query was successful
     */
    public boolean didFindTarget(FreddyWorldState state) {
        return state.querySatisfied;
    }

    /**
     * Example 8: Get distance to queried target
     */
    public double getTargetDistance(FreddyWorldState state) {
        return state.querySatisfied ? state.queryDistance : -1;
    }

    /**
     * Example 9: Check if Freddy is in danger
     */
    public boolean isInDanger(FreddyWorldState state) {
        return state.threatNearby ||
                state.lavaBelow ||
                !state.standingOnSolidBlock;
    }

    /**
     * Example 10: Check if ready for portal building
     */
    public boolean isReadyForPortalBuilding(FreddyWorldState state) {
        return state.obsidianCount >= 10 &&
                state.hasDiamondPickaxe;
    }

    // =========================
    // INTEGRATION EXAMPLE
    // =========================

    /**
     * Example 11: Complete decision flow
     * 
     * This demonstrates how you might integrate perception
     * with decision-making logic.
     */
    public String makeDecision() {
        // 1. Observe base world
        FreddyWorldState state = perception.observe();

        // 2. Check for immediate dangers
        if (isInDanger(state)) {
            return "RETREAT";
        }

        // 3. Check for nearby player
        if (state.playerNearby && state.playerDistance < 3) {
            return "GREET_PLAYER";
        }

        // 4. Check for threats
        if (state.threatNearby) {
            return "PROTECT_MODE";
        }

        // 5. Check for resources
        if (state.nearbyOres != null && !state.nearbyOres.isEmpty()) {
            return "MINE_ORES";
        }

        // 6. Check if ready for portal
        if (isReadyForPortalBuilding(state)) {
            return "BUILD_PORTAL";
        }

        // 7. Default: idle or gather resources
        return "IDLE";
    }

    /**
     * Example 12: LLM integration pattern (future use)
     * 
     * This shows how you would send world state to an LLM
     * and get a decision back.
     */
    public void llmIntegrationExample() {
        // 1. Get current world state
        FreddyWorldState state = perception.observe();

        // 2. Convert to JSON
        String json = gson.toJson(state);

        // 3. Send to LLM (pseudo-code)
        // String decision = LLMConnector.ask(json);

        // 4. Execute decision
        // FreddyActions.execute(decision);

        System.out.println("World State JSON:");
        System.out.println(json);
    }
}

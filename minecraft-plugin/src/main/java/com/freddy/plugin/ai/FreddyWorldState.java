package com.freddy.plugin.ai;

import java.util.List;
import java.util.Map;

/**
 * FreddyWorldState - LLM-Friendly World State Representation
 * 
 * This class contains a flat, JSON-serializable representation of Freddy's
 * perception of the Minecraft world. No Bukkit objects - only primitives
 * and simple collections.
 * 
 * This state can be directly serialized to JSON and sent to an LLM for
 * reasoning and decision-making.
 */
public class FreddyWorldState {

    // =========================
    // POSITION
    // =========================
    public double x;
    public double y;
    public double z;
    public String biome;

    // =========================
    // PLAYER CONTEXT
    // =========================
    public boolean playerNearby;
    public String nearestPlayer;
    public double playerDistance;
    public double playerHealth;

    // =========================
    // THREATS
    // =========================
    public boolean threatNearby;
    public List<String> nearbyHostileMobs;

    // =========================
    // ENVIRONMENT
    // =========================
    public boolean lavaNearby;
    public boolean waterNearby;
    public boolean obsidianNearby;

    // =========================
    // RESOURCES
    // =========================
    public List<String> nearbyOres;

    // =========================
    // CRAFTING & SMELTING
    // =========================
    public boolean furnaceAvailable;
    public boolean hasFuel;
    public Map<String, Integer> nearbyBlocks;

    // =========================
    // INVENTORY
    // =========================
    public int obsidianCount;
    public boolean hasDiamondPickaxe;
    public boolean hasWaterBucket;

    // =========================
    // SAFETY
    // =========================
    public boolean standingOnSolidBlock;
    public boolean lavaBelow;

    // =========================
    // QUERY RESULTS
    // =========================
    // These fields are populated when a FreddyQuery is used
    public boolean querySatisfied;
    public String queryResultType;
    public double queryDistance;
    public double queryX;
    public double queryY;
    public double queryZ;

    // =========================
    // META
    // =========================
    public long tickTime;
}

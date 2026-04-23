package com.freddy.plugin.ai;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * FreddyPerception - Observation-Only Module
 * 
 * This module is responsible for observing the Minecraft world and converting
 * it into a clean, LLM-friendly FreddyWorldState object.
 * 
 * Key principles:
 * - OBSERVE ONLY (no actions)
 * - No Bukkit objects in output
 * - Supports optional queries for targeted perception
 */
public class FreddyPerception {

    private final NPC freddy;

    public FreddyPerception(NPC freddy) {
        this.freddy = freddy;
    }

    // =========================
    // PUBLIC API
    // =========================

    /**
     * Observe the world without any specific query
     */
    public FreddyWorldState observe() {
        return observe(null);
    }

    /**
     * Observe the world with an optional query for targeted information
     * 
     * @param query Optional query for specific blocks or mobs
     * @return FreddyWorldState with base observations and query results (if
     *         provided)
     */
    public FreddyWorldState observe(FreddyQuery query) {
        FreddyWorldState s = observeBase();

        if (query == null || query.isEmpty()) {
            return s;
        }

        // Process block query
        if (query.blockType != null) {
            findBlockQuery(s, query);
        }

        // Process mob query
        if (query.mobType != null) {
            findMobQuery(s, query);
        }

        return s;
    }

    // =========================
    // BASE OBSERVATION
    // =========================

    /**
     * Perform base world observation (no queries)
     */
    private FreddyWorldState observeBase() {
        FreddyWorldState s = new FreddyWorldState();

        if (!(freddy.getEntity() instanceof Player npc)) {
            return s;
        }

        Location loc = npc.getLocation();
        World world = loc.getWorld();

        // Position
        observePosition(s, loc);

        // Player context
        observePlayerContext(s, npc, loc, world);

        // Threats
        observeThreats(s, loc, world);

        // Environment scan
        observeEnvironment(s, loc, world);

        // Inventory
        observeInventory(s, npc);

        // Safety checks
        observeSafety(s, loc);

        // Meta
        s.tickTime = System.currentTimeMillis();

        return s;
    }

    // =========================
    // OBSERVATION HELPERS
    // =========================

    private void observePosition(FreddyWorldState s, Location loc) {
        s.x = loc.getX();
        s.y = loc.getY();
        s.z = loc.getZ();
        s.biome = loc.getBlock().getBiome().toString();
    }

    private void observePlayerContext(FreddyWorldState s, Player npc, Location loc, World world) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player p : world.getPlayers()) {
            if (p.equals(npc))
                continue;
            double d = p.getLocation().distance(loc);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = p;
            }
        }

        if (nearest != null && nearestDist <= 10) {
            s.playerNearby = true;
            s.nearestPlayer = nearest.getName();
            s.playerDistance = nearestDist;
            s.playerHealth = nearest.getHealth();
        }
    }

    private void observeThreats(FreddyWorldState s, Location loc, World world) {
        s.nearbyHostileMobs = new ArrayList<>();
        s.threatNearby = false;

        for (Entity e : world.getNearbyEntities(loc, 8, 8, 8)) {
            if (e instanceof Monster m) {
                s.threatNearby = true;
                s.nearbyHostileMobs.add(m.getType().name());
            }
        }
    }

    private void observeEnvironment(FreddyWorldState s, Location loc, World world) {
        int scanRadius = 6;
        s.nearbyOres = new ArrayList<>();

        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -scanRadius; y <= scanRadius; y++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    Block b = world.getBlockAt(
                            loc.getBlockX() + x,
                            loc.getBlockY() + y,
                            loc.getBlockZ() + z);

                    Material type = b.getType();

                    if (type == Material.LAVA)
                        s.lavaNearby = true;
                    if (type == Material.WATER)
                        s.waterNearby = true;
                    if (type == Material.OBSIDIAN)
                        s.obsidianNearby = true;

                    if (type.name().endsWith("_ORE")) {
                        s.nearbyOres.add(type.name());
                    }
                }
            }
        }
    }

    private void observeInventory(FreddyWorldState s, Player npc) {
        s.obsidianCount = countItem(npc, Material.OBSIDIAN);
        s.hasDiamondPickaxe = hasItem(npc, Material.DIAMOND_PICKAXE);
        s.hasWaterBucket = hasItem(npc, Material.WATER_BUCKET);
    }

    private void observeSafety(FreddyWorldState s, Location loc) {
        Block below = loc.clone().add(0, -1, 0).getBlock();
        s.standingOnSolidBlock = below.getType().isSolid();
        s.lavaBelow = below.getType() == Material.LAVA;
    }

    // =========================
    // QUERY HANDLERS
    // =========================

    /**
     * Find the nearest block matching the query
     */
    private void findBlockQuery(FreddyWorldState s, FreddyQuery q) {
        Location loc = freddy.getEntity().getLocation();
        World world = loc.getWorld();

        Material target;
        try {
            target = Material.valueOf(q.blockType.toUpperCase());
        } catch (Exception e) {
            return; // Invalid material name
        }

        double bestDist = Double.MAX_VALUE;
        Location bestLoc = null;

        for (int x = -q.radius; x <= q.radius; x++) {
            for (int y = -q.radius; y <= q.radius; y++) {
                for (int z = -q.radius; z <= q.radius; z++) {
                    Block b = world.getBlockAt(
                            loc.getBlockX() + x,
                            loc.getBlockY() + y,
                            loc.getBlockZ() + z);

                    if (b.getType() != target)
                        continue;

                    double d = b.getLocation().distance(loc);

                    if (d < bestDist) {
                        bestDist = d;
                        bestLoc = b.getLocation();
                    }

                    // If not looking for nearest, break early
                    if (!q.nearestOnly)
                        break;
                }
            }
        }

        if (bestLoc != null) {
            s.querySatisfied = true;
            s.queryResultType = target.name();
            s.queryDistance = bestDist;
            s.queryX = bestLoc.getX();
            s.queryY = bestLoc.getY();
            s.queryZ = bestLoc.getZ();
        }
    }

    /**
     * Find the nearest mob matching the query
     */
    private void findMobQuery(FreddyWorldState s, FreddyQuery q) {
        Location loc = freddy.getEntity().getLocation();
        World world = loc.getWorld();

        EntityType target;
        try {
            target = EntityType.valueOf(q.mobType.toUpperCase());
        } catch (Exception e) {
            return; // Invalid entity type
        }

        double bestDist = Double.MAX_VALUE;
        LivingEntity best = null;

        for (Entity e : world.getNearbyEntities(loc, q.radius, q.radius, q.radius)) {
            if (!(e instanceof LivingEntity le))
                continue;
            if (le.getType() != target)
                continue;

            double d = le.getLocation().distance(loc);

            if (d < bestDist) {
                bestDist = d;
                best = le;
            }
        }

        if (best != null) {
            s.querySatisfied = true;
            s.queryResultType = target.name();
            s.queryDistance = bestDist;
            s.queryX = best.getLocation().getX();
            s.queryY = best.getLocation().getY();
            s.queryZ = best.getLocation().getZ();
        }
    }

    // =========================
    // INVENTORY HELPERS
    // =========================

    private int countItem(Player p, Material mat) {
        int count = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && i.getType() == mat) {
                count += i.getAmount();
            }
        }
        return count;
    }

    private boolean hasItem(Player p, Material mat) {
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && i.getType() == mat)
                return true;
        }
        return false;
    }
}

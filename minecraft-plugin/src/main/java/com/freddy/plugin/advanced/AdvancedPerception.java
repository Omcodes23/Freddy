package com.freddy.plugin.advanced;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

/**
 * Observe-only world scanner.
 */
public class AdvancedPerception {
    private Player npc;

    public AdvancedPerception(Player npc) {
        this.npc = npc;
    }

    public void setNpc(Player npc) {
        this.npc = npc;
    }

    public AdvancedWorldState observe() {
        return observe(null);
    }

    public AdvancedWorldState observe(AdvancedQuery query) {
        AdvancedWorldState state = new AdvancedWorldState();
        if (npc == null || npc.getWorld() == null) {
            state.tickTime = System.currentTimeMillis();
            return state;
        }

        Location loc = npc.getLocation();
        World world = npc.getWorld();

        state.x = loc.getX();
        state.y = loc.getY();
        state.z = loc.getZ();
        state.biome = world.getBiome(loc).toString();

        scanPlayers(state, loc, world);
        scanThreats(state, loc, world);
        scanBlocks(state, loc, world, 8);

        Block below = loc.clone().add(0, -1, 0).getBlock();
        state.standingOnSolidBlock = below.getType().isSolid();
        state.tickTime = System.currentTimeMillis();

        if (query != null && !query.isEmpty()) {
            applyQuery(state, loc, world, query);
        }

        return state;
    }

    private void scanPlayers(AdvancedWorldState state, Location loc, World world) {
        double best = Double.MAX_VALUE;
        Player bestPlayer = null;
        for (Player p : world.getPlayers()) {
            if (p.getUniqueId().equals(npc.getUniqueId())) {
                continue;
            }
            double d = p.getLocation().distance(loc);
            if (d < best) {
                best = d;
                bestPlayer = p;
            }
        }
        if (bestPlayer != null) {
            state.playerNearby = true;
            state.nearestPlayer = bestPlayer.getName();
            state.playerDistance = best;
        }
    }

    private void scanThreats(AdvancedWorldState state, Location loc, World world) {
        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            if (e instanceof Monster m) {
                state.threatNearby = true;
                state.nearbyHostileMobs.add(m.getType().name());
            }
        }
    }

    private void scanBlocks(AdvancedWorldState state, Location loc, World world, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = world.getBlockAt(loc.getBlockX() + dx, loc.getBlockY() + dy, loc.getBlockZ() + dz);
                    Material type = b.getType();

                    if (type == Material.LAVA) {
                        state.lavaNearby = true;
                    }
                    if (type == Material.WATER) {
                        state.waterNearby = true;
                    }
                    if (isUsefulBlock(type)) {
                        state.nearbyBlocks.merge(type.name(), 1, Integer::sum);
                    }
                    if (state.nearbyBlocks.size() > 100) {
                        return;
                    }
                }
            }
        }
    }

    private void applyQuery(AdvancedWorldState state, Location loc, World world, AdvancedQuery query) {
        if (query.blockType != null && !query.blockType.isBlank()) {
            tryFindBlock(state, loc, world, query);
            if (state.querySatisfied) {
                return;
            }
        }

        if (query.mobType != null && !query.mobType.isBlank()) {
            tryFindMob(state, loc, world, query);
        }
    }

    private void tryFindBlock(AdvancedWorldState state, Location loc, World world, AdvancedQuery query) {
        Material target;
        try {
            target = Material.valueOf(query.blockType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        double bestDist = Double.MAX_VALUE;
        Location bestLoc = null;
        int r = Math.max(1, query.radius);

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block b = world.getBlockAt(loc.getBlockX() + dx, loc.getBlockY() + dy, loc.getBlockZ() + dz);
                    if (b.getType() != target) {
                        continue;
                    }
                    double d = b.getLocation().distance(loc);
                    if (d < bestDist) {
                        bestDist = d;
                        bestLoc = b.getLocation();
                    }
                    if (!query.nearestOnly) {
                        break;
                    }
                }
            }
        }

        if (bestLoc != null) {
            state.querySatisfied = true;
            state.queryResultType = target.name();
            state.queryDistance = bestDist;
            state.queryX = bestLoc.getX();
            state.queryY = bestLoc.getY();
            state.queryZ = bestLoc.getZ();
        }
    }

    private void tryFindMob(AdvancedWorldState state, Location loc, World world, AdvancedQuery query) {
        EntityType target;
        try {
            target = EntityType.valueOf(query.mobType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        double bestDist = Double.MAX_VALUE;
        Entity best = null;
        int r = Math.max(1, query.radius);
        for (Entity e : world.getNearbyEntities(loc, r, r, r)) {
            if (e.getType() != target) {
                continue;
            }
            double d = e.getLocation().distance(loc);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }

        if (best != null) {
            state.querySatisfied = true;
            state.queryResultType = target.name();
            state.queryDistance = bestDist;
            state.queryX = best.getLocation().getX();
            state.queryY = best.getLocation().getY();
            state.queryZ = best.getLocation().getZ();
        }
    }

    private boolean isUsefulBlock(Material type) {
        return type.name().endsWith("_ORE")
            || type.name().endsWith("_LOG")
            || type == Material.COBBLESTONE
            || type == Material.STONE
            || type == Material.DIRT
            || type == Material.GRAVEL
            || type == Material.SAND;
    }
}

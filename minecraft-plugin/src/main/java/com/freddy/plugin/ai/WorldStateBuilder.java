package com.freddy.plugin.ai;

import com.freddy.plugin.FreddyPlugin;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * WorldStateBuilder - Populates FreddyWorldState from actual world
 * 
 * Converts Bukkit world data into LLM-friendly state representation.
 * This is the "sense" step of the autonomous agent loop.
 */
public class WorldStateBuilder {

    private final FreddyPlugin plugin;

    public WorldStateBuilder(FreddyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Build complete world state snapshot
     * 
     * @return Populated FreddyWorldState
     */
    public FreddyWorldState build() {
        FreddyWorldState state = new FreddyWorldState();

        NPC freddy = FreddyPlugin.getFreddy();
        if (freddy == null || freddy.getEntity() == null) {
            // Freddy not spawned, return minimal state
            state.tickTime = System.currentTimeMillis();
            return state;
        }

        Entity entity = freddy.getEntity();
        Location loc = entity.getLocation();
        World world = loc.getWorld();

        // [1] Position
        state.x = loc.getX();
        state.y = loc.getY();
        state.z = loc.getZ();
        state.biome = world.getBiome(loc).toString();

        // [2] Player context
        Player nearestPlayer = findNearestPlayer(loc);
        if (nearestPlayer != null) {
            state.playerNearby = true;
            state.nearestPlayer = nearestPlayer.getName();
            state.playerDistance = nearestPlayer.getLocation().distance(loc);
            state.playerHealth = nearestPlayer.getHealth();
        }

        // [3] Furnace availability (scan nearby)
        state.furnaceAvailable = scanForFurnace(loc, 10);

        // [4] Fuel availability (check inventory)
        state.hasFuel = checkHasFuel();

        // [5] Nearby blocks (for planning)
        state.nearbyBlocks = scanNearbyBlocks(loc, 20);

        // [6] Safety checks
        Block blockBelow = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        state.standingOnSolidBlock = blockBelow.getType().isSolid();
        state.lavaBelow = blockBelow.getType() == Material.LAVA;

        state.tickTime = System.currentTimeMillis();

        return state;
    }

    /**
     * Find nearest player to location
     */
    private Player findNearestPlayer(Location loc) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;

        for (Player player : loc.getWorld().getPlayers()) {
            double dist = player.getLocation().distance(loc);
            if (dist < minDist) {
                minDist = dist;
                nearest = player;
            }
        }

        return nearest;
    }

    /**
     * Scan for furnace within radius
     */
    private boolean scanForFurnace(Location center, int radius) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.FURNACE ||
                            block.getType() == Material.BLAST_FURNACE) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if Freddy has fuel in inventory
     */
    private boolean checkHasFuel() {
        FreddyInventory inv = plugin.getFreddyInventory();

        // Common fuel materials
        return inv.has(Material.COAL, 1) ||
                inv.has(Material.CHARCOAL, 1) ||
                inv.has(Material.COAL_BLOCK, 1) ||
                inv.has(Material.OAK_PLANKS, 1) ||
                inv.has(Material.OAK_LOG, 1);
    }

    /**
     * Scan nearby blocks for planning
     * 
     * @param center Center location
     * @param radius Scan radius
     * @return Map of material names to counts
     */
    private Map<String, Integer> scanNearbyBlocks(Location center, int radius) {
        Map<String, Integer> blocks = new HashMap<>();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Limit scan to avoid performance issues
        int scanned = 0;
        int maxScan = 1000;

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), cy - radius); y <= Math.min(world.getMaxHeight(),
                    cy + radius); y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (scanned++ > maxScan) {
                        return blocks; // Stop if too many blocks
                    }

                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();

                    // Only track useful blocks (ores, logs, etc.)
                    if (isUsefulBlock(type)) {
                        blocks.merge(type.name(), 1, Integer::sum);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Check if block type is useful for crafting/gathering
     */
    private boolean isUsefulBlock(Material type) {
        return type.name().endsWith("_ORE") ||
                type.name().endsWith("_LOG") ||
                type == Material.COBBLESTONE ||
                type == Material.STONE ||
                type == Material.DIRT ||
                type == Material.SAND ||
                type == Material.GRAVEL ||
                type == Material.CLAY;
    }
}

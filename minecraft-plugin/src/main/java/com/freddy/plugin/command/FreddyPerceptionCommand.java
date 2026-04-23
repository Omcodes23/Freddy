package com.freddy.plugin.command;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyPerception;
import com.freddy.plugin.ai.FreddyQuery;
import com.freddy.plugin.ai.FreddyWorldState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * FreddyPerceptionCommand - Test command for FreddyPerception
 * 
 * Usage:
 * /freddy-observe - Get basic world state
 * /freddy-observe lava - Query for nearest lava
 * /freddy-observe zombie - Query for nearest zombie
 * /freddy-observe obsidian - Query for nearest obsidian
 */
public class FreddyPerceptionCommand implements CommandExecutor {

    private final FreddyPlugin plugin;
    private final Gson gson;

    public FreddyPerceptionCommand(FreddyPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        FreddyPerception perception = plugin.getFreddyPerception();
        if (perception == null) {
            player.sendMessage("§cFreddyPerception is not initialized!");
            return true;
        }

        FreddyWorldState state;

        if (args.length == 0) {
            // Basic observation
            player.sendMessage("§6[Freddy] §eObserving world...");
            state = perception.observe();
        } else {
            // Query observation - dynamically support all entity types and materials
            String queryInput = args[0].toUpperCase().replace("-", "_");
            FreddyQuery query = new FreddyQuery();
            query.radius = args.length > 1 ? parseRadius(args[1]) : 50;
            query.nearestOnly = true;

            // Try as EntityType first (for mobs/animals/etc)
            try {
                org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(queryInput);
                query.mobType = entityType.name();
                player.sendMessage(String.format("§6[Freddy] §eSearching for %s (radius: %d)...",
                        formatName(entityType.name()), query.radius));
                state = perception.observe(query);
            } catch (IllegalArgumentException e1) {
                // Not an entity, try as Material (for blocks)
                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(queryInput);
                    query.blockType = material.name();
                    player.sendMessage(String.format("§6[Freddy] §eSearching for %s (radius: %d)...",
                            formatName(material.name()), query.radius));
                    state = perception.observe(query);
                } catch (IllegalArgumentException e2) {
                    // Neither entity nor material
                    player.sendMessage("§c✗ Unknown type: §f" + args[0]);
                    player.sendMessage("§eExamples:");
                    player.sendMessage("  §7Mobs: §fhorse, cow, pig, zombie, skeleton, creeper");
                    player.sendMessage("  §7Blocks: §flava, obsidian, diamond_ore, iron_ore");
                    player.sendMessage("§eUsage: §f/freddy-observe <type> [radius]");
                    return true;
                }
            }
        }

        // Display results
        displayWorldState(player, state, args.length > 0);

        return true;
    }

    private void displayWorldState(Player player, FreddyWorldState state, boolean isQuery) {
        player.sendMessage("§6========== Freddy World State ==========");

        // Position
        player.sendMessage(String.format("§ePosition: §f%.1f, %.1f, %.1f", state.x, state.y, state.z));
        player.sendMessage("§eBiome: §f" + state.biome);

        // Player context
        if (state.playerNearby) {
            player.sendMessage(String.format("§eNearest Player: §f%s (§e%.1f blocks§f, §c%.1f HP§f)",
                    state.nearestPlayer, state.playerDistance, state.playerHealth));
        } else {
            player.sendMessage("§eNearest Player: §7None nearby");
        }

        // Threats
        if (state.threatNearby) {
            player.sendMessage("§c⚠ Threats: §f" + String.join(", ", state.nearbyHostileMobs));
        } else {
            player.sendMessage("§aThreats: §7None");
        }

        // Environment
        player.sendMessage(String.format("§eEnvironment: %s %s %s",
                state.lavaNearby ? "§c[Lava]" : "",
                state.waterNearby ? "§b[Water]" : "",
                state.obsidianNearby ? "§5[Obsidian]" : "").trim());

        // Resources
        if (state.nearbyOres != null && !state.nearbyOres.isEmpty()) {
            player.sendMessage("§eOres Nearby: §f" + String.join(", ", state.nearbyOres));
        }

        // Inventory
        player.sendMessage(String.format("§eInventory: §f%d obsidian, %s diamond pickaxe, %s water bucket",
                state.obsidianCount,
                state.hasDiamondPickaxe ? "§a✓" : "§c✗",
                state.hasWaterBucket ? "§a✓" : "§c✗"));

        // Safety
        String safetyStatus = state.standingOnSolidBlock ? "§a✓" : "§c✗";
        if (state.lavaBelow) {
            safetyStatus = "§c⚠ LAVA BELOW!";
        }
        player.sendMessage("§eSafety: " + safetyStatus);

        // Query results
        if (isQuery) {
            player.sendMessage("§6========== Query Results ==========");
            if (state.querySatisfied) {
                player.sendMessage(String.format("§a✓ Found: §f%s", state.queryResultType));
                player.sendMessage(String.format("§eDistance: §f%.1f blocks", state.queryDistance));
                player.sendMessage(String.format("§eLocation: §f%.1f, %.1f, %.1f",
                        state.queryX, state.queryY, state.queryZ));
            } else {
                player.sendMessage("§c✗ Not found within search radius");
            }
        }

        player.sendMessage("§6=====================================");

        // Send JSON to console for debugging
        String json = gson.toJson(state);
        player.sendMessage("§7(Full JSON logged to console)");
        plugin.getLogger().info("\n" + json);
    }

    /**
     * Parse radius from string, with validation
     */
    private int parseRadius(String radiusStr) {
        try {
            int radius = Integer.parseInt(radiusStr);
            return Math.max(1, radius); // Minimum 1, no upper limit
        } catch (NumberFormatException e) {
            return 50; // Default
        }
    }

    /**
     * Format enum name for display (DIAMOND_ORE -> Diamond Ore)
     */
    private String formatName(String enumName) {
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0)
                result.append(" ");
            result.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }
        return result.toString();
    }
}

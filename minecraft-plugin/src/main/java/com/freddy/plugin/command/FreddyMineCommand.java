package com.freddy.plugin.command;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.npc.FreddyMovement;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * FreddyMineCommand - Command Freddy to mine blocks
 * 
 * Usage:
 * /freddy-mine <material> [quantity]
 * 
 * Examples:
 * /freddy-mine oak_log
 * /freddy-mine iron_ore 5
 * /freddy-mine diamond_ore 3
 */

public class FreddyMineCommand implements CommandExecutor {

    private final FreddyPlugin plugin;

    public FreddyMineCommand(FreddyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (args.length == 0) {
            showUsage(player);
            return true;
        }

        FreddyMovement movement = plugin.getFreddyMovement();
        if (movement == null) {
            player.sendMessage("§c[Freddy] Movement system not initialized!");
            return true;
        }

        // Parse material
        String materialName = args[0].toUpperCase().replace("-", "_");
        Material material;

        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            // Try fuzzy match
            material = Material.matchMaterial(materialName);
            if (material == null) {
                player.sendMessage("§c[Freddy] Unknown material: " + args[0]);
                return true;
            }
        }

        // Validate it's a block
        if (!material.isBlock()) {
            player.sendMessage("§c[Freddy] " + material.name() + " is not a block!");
            return true;
        }

        // Parse quantity (default 1)
        int quantity = 1;
        if (args.length > 1) {
            try {
                quantity = Integer.parseInt(args[1]);
                if (quantity < 1 || quantity > 64) {
                    player.sendMessage("§c[Freddy] Quantity must be between 1 and 64!");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c[Freddy] Invalid quantity: " + args[1]);
                return true;
            }
        }

        // Execute mining
        player.sendMessage(String.format("§a[Freddy] Mining %dx %s...",
                quantity, formatName(material.name())));

        // Mine the specified quantity
        for (int i = 0; i < quantity; i++) {
            movement.mine(material);

            // Add small delay between mining operations
            if (i < quantity - 1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return true;
    }

    /**
     * Show command usage
     */
    private void showUsage(Player player) {
        player.sendMessage("§6========== Freddy Mine ==========");
        player.sendMessage("§eUsage: §f/freddy-mine <material> [quantity]");
        player.sendMessage("");
        player.sendMessage("§eExamples:");
        player.sendMessage("  §f/freddy-mine oak_log");
        player.sendMessage("  §f/freddy-mine iron_ore 5");
        player.sendMessage("  §f/freddy-mine diamond_ore 3");
        player.sendMessage("§6=================================");
    }

    /**
     * Format material name for display
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

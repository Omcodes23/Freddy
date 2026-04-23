package com.freddy.plugin.command;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyInventory;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * FreddyInventoryCommand - Manage Freddy's Virtual Inventory
 * 
 * Usage:
 * /freddy-inv add <material> <amount> - Add items to Freddy's inventory
 * /freddy-inv remove <material> <amount> - Remove items from inventory
 * /freddy-inv list - Display all items in inventory
 * /freddy-inv clear - Clear all items from inventory
 * 
 * Examples:
 * /freddy-inv add oak_planks 64
 * /freddy-inv remove iron_ingot 10
 * /freddy-inv list
 */
public class FreddyInventoryCommand implements CommandExecutor {

    private final FreddyPlugin plugin;

    public FreddyInventoryCommand(FreddyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        FreddyInventory inventory = plugin.getFreddyInventory();
        if (inventory == null) {
            player.sendMessage("§cFreddyInventory is not initialized!");
            return true;
        }

        if (args.length == 0) {
            showUsage(player);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add" -> handleAdd(player, inventory, args);
            case "remove" -> handleRemove(player, inventory, args);
            case "list" -> handleList(player, inventory);
            case "clear" -> handleClear(player, inventory);
            default -> showUsage(player);
        }

        return true;
    }

    private void handleAdd(Player player, FreddyInventory inventory, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /freddy-inv add <material> <amount>");
            return;
        }

        Material material = parseMaterial(args[1]);
        if (material == null) {
            player.sendMessage("§cUnknown material: " + args[1]);
            return;
        }

        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            player.sendMessage("§cAmount must be a positive number!");
            return;
        }

        inventory.add(material, amount);
        player.sendMessage(String.format("§a✓ Added %dx %s to Freddy's inventory",
                amount, formatName(material.name())));
    }

    private void handleRemove(Player player, FreddyInventory inventory, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /freddy-inv remove <material> <amount>");
            return;
        }

        Material material = parseMaterial(args[1]);
        if (material == null) {
            player.sendMessage("§cUnknown material: " + args[1]);
            return;
        }

        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            player.sendMessage("§cAmount must be a positive number!");
            return;
        }

        boolean success = inventory.remove(material, amount);
        if (success) {
            player.sendMessage(String.format("§a✓ Removed %dx %s from Freddy's inventory",
                    amount, formatName(material.name())));
        } else {
            player.sendMessage(String.format("§c✗ Insufficient items (has %d, needs %d)",
                    inventory.getCount(material), amount));
        }
    }

    private void handleList(Player player, FreddyInventory inventory) {
        Map<Material, Integer> items = inventory.getAll();

        player.sendMessage("§6========== Freddy's Inventory ==========");

        if (items.isEmpty()) {
            player.sendMessage("§7(Empty)");
        } else {
            player.sendMessage(String.format("§e%d unique item types:", items.size()));
            player.sendMessage("");

            items.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // Sort by amount descending
                    .forEach(entry -> {
                        player.sendMessage(String.format("  §f%s §7x%d",
                                formatName(entry.getKey().name()),
                                entry.getValue()));
                    });
        }

        player.sendMessage("§6========================================");
    }

    private void handleClear(Player player, FreddyInventory inventory) {
        int count = inventory.getUniqueItemCount();
        inventory.clear();
        player.sendMessage(String.format("§a✓ Cleared %d item types from Freddy's inventory", count));
    }

    private void showUsage(Player player) {
        player.sendMessage("§6========== Freddy Inventory ==========");
        player.sendMessage("§eUsage:");
        player.sendMessage("  §f/freddy-inv add <material> <amount>");
        player.sendMessage("  §f/freddy-inv remove <material> <amount>");
        player.sendMessage("  §f/freddy-inv list");
        player.sendMessage("  §f/freddy-inv clear");
        player.sendMessage("");
        player.sendMessage("§eExamples:");
        player.sendMessage("  §f/freddy-inv add oak_planks 64");
        player.sendMessage("  §f/freddy-inv remove iron_ingot 10");
        player.sendMessage("  §f/freddy-inv list");
        player.sendMessage("§6======================================");
    }

    private Material parseMaterial(String materialStr) {
        try {
            return Material.valueOf(materialStr.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int parseAmount(String amountStr) {
        try {
            return Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

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

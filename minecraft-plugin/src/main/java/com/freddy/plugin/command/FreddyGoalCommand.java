package com.freddy.plugin.command;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.goal.Goal;
import com.freddy.plugin.ai.goal.GoalManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * FreddyGoalCommand - Create autonomous goals for Freddy
 * 
 * This demonstrates the new goal-driven architecture.
 * Instead of immediate execution, goals are queued and Freddy works on them
 * autonomously.
 * 
 * Usage:
 * /freddy-goal \u003citem\u003e [amount]
 * 
 * Examples:
 * /freddy-goal iron_sword
 * /freddy-goal diamond_pickaxe 2
 * /freddy-goal crafting_table
 */
public class FreddyGoalCommand implements CommandExecutor {

    private final FreddyPlugin plugin;

    public FreddyGoalCommand(FreddyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        GoalManager goalManager = plugin.getGoalManager();
        if (goalManager == null) {
            player.sendMessage("§cGoal system not initialized!");
            return true;
        }

        // Handle subcommands
        if (args.length == 0) {
            showUsage(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "add":
            case "create":
                return handleAddGoal(player, args);

            case "list":
                return handleListGoals(player);

            case "status":
                return handleStatus(player);

            case "help":
            default:
                showUsage(player);
                return true;
        }
    }

    /**
     * Handle adding a new goal
     */
    private boolean handleAddGoal(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /freddy-goal add \u003citem\u003e [amount]");
            return true;
        }

        String item = args[1].toUpperCase().replace("-", "_");
        int amount = args.length > 2 ? parseAmount(args[2]) : 1;

        // Validate material
        Material target;
        try {
            target = Material.valueOf(item);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c✗ Unknown item: " + formatName(item));
            return true;
        }

        // Create goal
        Goal goal = new Goal(target, amount);
        plugin.getGoalManager().addGoal(goal);

        player.sendMessage("§6========== Freddy Goal =========");
        player.sendMessage(String.format("§a✓ Goal created: %s x%d", formatName(item), amount));
        player.sendMessage("§7Freddy will work on this autonomously");
        player.sendMessage("§7Use §f/freddy-goal status§7 to monitor progress");
        player.sendMessage("§6================================");

        return true;
    }

    /**
     * Handle listing pending goals
     */
    private boolean handleListGoals(Player player) {
        GoalManager goalManager = plugin.getGoalManager();
        var goals = goalManager.getPendingGoals();

        player.sendMessage("§6====== Pending Goals ======");

        if (goals.isEmpty()) {
            player.sendMessage("§7No pending goals");
        } else {
            for (int i = 0; i < goals.size(); i++) {
                Goal goal = goals.get(i);
                player.sendMessage(String.format("§7%d. §f%s §7x%d §8[%s]",
                        i + 1,
                        formatName(goal.target.name()),
                        goal.quantity,
                        goal.status));
            }
        }

        player.sendMessage("§6===========================");
        return true;
    }

    /**
     * Handle showing current status
     */
    private boolean handleStatus(Player player) {
        GoalManager goalManager = plugin.getGoalManager();
        Goal current = goalManager.getCurrentGoal();

        player.sendMessage("§6====== Freddy Status ======");

        if (current == null) {
            player.sendMessage("§7Not working on any goal");
            player.sendMessage("§7Use §f/freddy-goal add \u003citem\u003e§7 to create a goal");
        } else {
            player.sendMessage(String.format("§eCurrent Goal: §f%s §7x%d",
                    formatName(current.target.name()),
                    current.quantity));
            player.sendMessage(String.format("§7Status: §f%s", current.status));
            player.sendMessage(String.format("§7Attempts: §f%d", current.attemptCount));
            player.sendMessage(String.format("§7Age: §f%d seconds", current.getAgeSeconds()));

            if (current.lastFailure != null) {
                player.sendMessage(String.format("§cLast Failure: §f%s", current.lastFailure));
            }
        }

        player.sendMessage("§6===========================");
        return true;
    }

    /**
     * Show command usage
     */
    private void showUsage(Player player) {
        player.sendMessage("§6====== Freddy Goal System ======");
        player.sendMessage("§eUsage:");
        player.sendMessage("  §f/freddy-goal add \u003citem\u003e [amount] §7- Create goal");
        player.sendMessage("  §f/freddy-goal list §7- Show pending goals");
        player.sendMessage("  §f/freddy-goal status §7- Show current status");
        player.sendMessage("");
        player.sendMessage("§eExamples:");
        player.sendMessage("  §f/freddy-goal add iron_sword");
        player.sendMessage("  §f/freddy-goal add diamond_pickaxe 2");
        player.sendMessage("  §f/freddy-goal status");
        player.sendMessage("§6================================");
    }

    /**
     * Parse amount from string
     */
    private int parseAmount(String amountStr) {
        try {
            int amount = Integer.parseInt(amountStr);
            return Math.max(1, Math.min(64, amount)); // Clamp 1-64
        } catch (NumberFormatException e) {
            return 1;
        }
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

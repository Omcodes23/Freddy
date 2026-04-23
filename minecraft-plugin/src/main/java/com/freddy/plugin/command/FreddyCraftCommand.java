package com.freddy.plugin.command;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.*;
import com.freddy.plugin.ai.crafting.*;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * FreddyCraftCommand - Autonomous crafting with automatic material gathering
 * 
 * Usage:
 * /freddy-craft <item> [amount]
 * 
 * Examples:
 * /freddy-craft iron_pickaxe
 * /freddy-craft diamond_sword 2
 * /freddy-craft stick 16
 * 
 * If materials are missing, Freddy will autonomously gather them.
 */
public class FreddyCraftCommand implements CommandExecutor {

    private final FreddyPlugin plugin;

    public FreddyCraftCommand(FreddyPlugin plugin) {
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

        // Get services
        PrimitiveResolver resolver = plugin.getPrimitiveResolver();
        GatheringPlanner gatheringPlanner = plugin.getGatheringPlanner();
        FreddyWorkflowExecutor executor = plugin.getFreddyWorkflowExecutor();
        FreddyInventory inventory = plugin.getFreddyInventory();
        AutoCrafter autoCrafter = plugin.getAutoCrafter();

        if (resolver == null || gatheringPlanner == null || executor == null ||
                inventory == null || autoCrafter == null) {
            player.sendMessage("§cCrafting system not initialized!");
            return true;
        }

        // Check if already executing
        if (executor.isExecuting()) {
            player.sendMessage("§c✗ Already executing a workflow!");
            player.sendMessage("§7Current: " + executor.getCurrentWorkflow().goal);
            return true;
        }

        // Parse request
        String item = args[0].toUpperCase().replace("-", "_");
        int amount = args.length > 1 ? parseAmount(args[1]) : 1;

        player.sendMessage("§6========== Freddy Craft ==========");
        player.sendMessage(String.format("§eTarget: §f%s §7x%d", formatName(item), amount));
        player.sendMessage("");

        // Validate material
        Material target;
        try {
            target = Material.valueOf(item);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c✗ Unknown item: " + formatName(item));
            player.sendMessage("§6==================================");
            return true;
        }

        // ==================== NEW DETERMINISTIC FLOW ====================

        // [1] Deterministic Expansion (resolve to primitives)
        player.sendMessage("§7[1/4] Resolving recipe tree...");
        Map<Material, Integer> required = resolver.resolve(target, amount);

        player.sendMessage("§ePrimitive requirements:");
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            player.sendMessage(String.format("  §7- §f%s §7x%d",
                    formatName(entry.getKey().name()), entry.getValue()));
        }
        player.sendMessage("");

        // [2] Calculate missing materials
        player.sendMessage("§7[2/4] Checking inventory...");
        Map<Material, Integer> missing = InventoryDiff.missing(required, inventory);

        if (missing.isEmpty()) {
            player.sendMessage("§a✓ All materials available!");
            player.sendMessage("");
            player.sendMessage("§7[3/4] No gathering needed");
            player.sendMessage("§7[4/4] Auto-crafting...");

            // Skip gathering, go straight to crafting
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                boolean success = autoCrafter.craftWithPlan(target, amount);

                player.sendMessage("");
                if (success) {
                    player.sendMessage("§a✓ Crafted successfully!");
                    player.sendMessage(String.format("§eCrafted: §f%dx %s", amount, formatName(item)));
                } else {
                    player.sendMessage("§c✗ Crafting failed!");
                }
                player.sendMessage("§6==================================");
            });

            return true;
        }

        // [3] LLM Gathering Planner (ASYNC)
        player.sendMessage("§eMissing materials:");
        for (Map.Entry<Material, Integer> entry : missing.entrySet()) {
            player.sendMessage(String.format("  §7- §f%s §7x%d",
                    formatName(entry.getKey().name()), entry.getValue()));
        }
        player.sendMessage("");
        player.sendMessage("§7[3/4] Planning gathering workflow...");

        // Plan gathering asynchronously
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            // [3] Generate gathering workflow
            FreddyWorkflow gatherWorkflow;

            // Special case: For weapons/tools, use deterministic gathering (no LLM)
            if (com.freddy.plugin.ai.crafting.ToolRecipeGraph.isWeaponOrTool(target)) {
                player.sendMessage("§7  → Using deterministic gathering (no LLM)");
                gatherWorkflow = createDeterministicGatheringWorkflow(missing); // Use 'missing' instead of
                                                                                // 'missingPrimitives'
            } else {
                // Use LLM for other items
                gatherWorkflow = gatheringPlanner.plan(target, missing);
            }

            if (gatherWorkflow == null) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage("§c✗ Failed to generate gathering plan!");
                    player.sendMessage("§6==================================");
                });
                return;
            }

            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(String.format("§7  → Generated %d gathering steps", gatherWorkflow.getStepCount()));

                // Display workflow
                player.sendMessage("");
                player.sendMessage("§e📋 Gathering Plan:");
                for (int i = 0; i < gatherWorkflow.steps.size(); i++) {
                    var step = gatherWorkflow.steps.get(i);
                    String icon = getCommandIcon(step.command);
                    player.sendMessage(String.format(
                            "§7  %d. %s §f%s §7x%d",
                            i + 1, icon, formatName(step.target), step.quantity));
                }
                player.sendMessage("");
                player.sendMessage("§eExecuting gathering...");

                // Execute with callback
                executor.executeWorkflowAsync(gatherWorkflow, gatherSuccess -> {
                    if (gatherSuccess) {
                        player.sendMessage("");
                        player.sendMessage("§a✓ Gathering completed!");
                        player.sendMessage("");
                        player.sendMessage("§7[4/4] Auto-crafting...");

                        // [5] Deterministic Crafting (SYNC)
                        boolean craftSuccess = autoCrafter.craftWithPlan(target, amount);

                        player.sendMessage("");
                        if (craftSuccess) {
                            player.sendMessage("§a✓ Craft completed successfully!");
                            player.sendMessage(String.format("§eCrafted: §f%dx %s", amount, formatName(item)));
                        } else {
                            player.sendMessage("§c✗ Auto-crafting failed!");
                            player.sendMessage("§7Check inventory for missing materials");
                        }
                    } else {
                        player.sendMessage("");
                        player.sendMessage("§c✗ Gathering failed!");
                    }

                    player.sendMessage("§6==================================");
                });
            });
        });

        return true;
    }

    /**
     * Get icon for command type
     */
    private String getCommandIcon(String command) {
        return switch (command.toUpperCase()) {
            case "FREDDY_MINE" -> "⛏";
            case "FREDDY_KILL" -> "⚔";
            case "FREDDY_CRAFT" -> "🔨";
            default -> "•";
        };
    }

    /**
     * Show usage
     */
    private void showUsage(Player player) {
        player.sendMessage("§6========== Freddy Craft ==========");
        player.sendMessage("§eUsage: §f/freddy-craft <item> [amount]");
        player.sendMessage("");
        player.sendMessage("§eExamples:");
        player.sendMessage("  §f/freddy-craft iron_pickaxe");
        player.sendMessage("  §f/freddy-craft diamond_sword 2");
        player.sendMessage("  §f/freddy-craft stick 16");
        player.sendMessage("");
        player.sendMessage("§7Freddy will automatically gather missing materials!");
        player.sendMessage("§6==================================");
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
     * Format material name for display (IRON_PICKAXE -> Iron Pickaxe)
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

    /**
     * Create deterministic gathering workflow (no LLM)
     * 
     * For weapons/tools, we know exactly what to gather - just create MINE
     * commands.
     */
    private FreddyWorkflow createDeterministicGatheringWorkflow(Map<Material, Integer> missing) {
        FreddyWorkflow workflow = new FreddyWorkflow();
        workflow.goal = "GATHER";
        workflow.steps = new java.util.ArrayList<>();

        // Create simple MINE command for each missing material
        for (var entry : missing.entrySet()) {
            FreddyStep step = new FreddyStep();
            step.command = "FREDDY_MINE";
            step.target = entry.getKey().name();
            step.quantity = entry.getValue();
            workflow.steps.add(step);
        }

        return workflow;
    }
}

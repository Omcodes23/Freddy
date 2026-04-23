package com.freddy.plugin.ai;

import com.freddy.plugin.FreddyPlugin;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Consumer;

/**
 * FreddyWorkflowExecutor - Executes workflow steps asynchronously
 * 
 * Uses tick-based execution to avoid blocking the server thread
 * All workflow execution happens via callbacks
 */
public class FreddyWorkflowExecutor {

    private final FreddyPlugin plugin;
    private boolean executing = false;
    private FreddyWorkflow currentWorkflow;

    public FreddyWorkflowExecutor(FreddyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isExecuting() {
        return executing;
    }

    public FreddyWorkflow getCurrentWorkflow() {
        return currentWorkflow;
    }

    /**
     * Execute a complete workflow ASYNCHRONOUSLY with callback
     * This will NEVER block the server thread
     */
    public void executeWorkflowAsync(FreddyWorkflow workflow, Consumer<Boolean> callback) {
        if (executing) {
            plugin.getLogger().warning("Workflow already executing!");
            callback.accept(false);
            return;
        }

        this.executing = true;
        this.currentWorkflow = workflow;

        plugin.getLogger().info(String.format("Starting workflow: %s", workflow));

        // Execute steps sequentially using tick-based approach
        executeStepRecursive(workflow, 0, success -> {
            this.executing = false;
            this.currentWorkflow = null;
            callback.accept(success);
        });
    }

    /**
     * Execute steps recursively without blocking
     */
    private void executeStepRecursive(FreddyWorkflow workflow, int stepIndex, Consumer<Boolean> callback) {
        // Base case: all steps complete
        if (stepIndex >= workflow.steps.size()) {
            plugin.getLogger().info("Workflow completed successfully!");
            callback.accept(true);
            return;
        }

        FreddyStep step = workflow.steps.get(stepIndex);

        plugin.getLogger().info(String.format("Executing step %d/%d: %s %s x%d",
                stepIndex + 1, workflow.steps.size(),
                step.command, step.target, step.quantity));

        long startTime = System.currentTimeMillis();

        // Execute step asynchronously
        executeStepAsync(step, success -> {
            long elapsed = System.currentTimeMillis() - startTime;
            plugin.getLogger().info(String.format("Step completed in %dms", elapsed));

            if (!success) {
                plugin.getLogger().warning("Step failed!");
                callback.accept(false);
                return;
            }

            // Verify progress
            if (!verifyProgress(step)) {
                plugin.getLogger().warning("Verification failed!");
                callback.accept(false);
                return;
            }

            // Execute next step
            executeStepRecursive(workflow, stepIndex + 1, callback);
        });
    }

    /**
     * Execute a single step asynchronously with callback
     */
    private void executeStepAsync(FreddyStep step, Consumer<Boolean> callback) {
        FreddyWorkflowAction command = step.getCommandEnum();

        if (command == null) {
            plugin.getLogger().warning("Invalid command: " + step.command);
            callback.accept(false);
            return;
        }

        switch (command) {
            case FREDDY_MINE:
                mineBlockAsync(step.target, step.quantity, callback);
                break;

            case FREDDY_KILL:
                huntMobAsync(step.target, step.quantity, callback);
                break;

            case FREDDY_CRAFT:
                craftItemAsync(step.target, step.quantity, callback);
                break;

            default:
                plugin.getLogger().warning("Unknown command: " + command);
                callback.accept(false);
        }
    }

    /**
     * Mine blocks asynchronously using BLOCK-COUNT semantics
     * 
     * CRITICAL: Mine exactly N blocks, accept whatever drops occur.
     * DO NOT monitor drop counts (Fortune causes infinite loops).
     */
    public void mineBlockAsync(String target, int quantity, Consumer<Boolean> callback) {
        plugin.getLogger().info(String.format("Mining %s x%d blocks", target, quantity));

        // Validate material
        Material material;
        try {
            material = Material.valueOf(target.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + target);
            callback.accept(false);
            return;
        }

        // Get movement system
        var movement = plugin.getFreddyMovement();
        if (movement == null) {
            plugin.getLogger().warning("FreddyMovement not initialized");
            callback.accept(false);
            return;
        }

        // Track blocks mined (NOT drops collected)
        final int[] blocksMined = { 0 };

        // Callback when a block is successfully mined
        Runnable onBlockMined = () -> {
            blocksMined[0]++;
            plugin.getLogger().info(String.format("Block mined: %d/%d %s",
                    blocksMined[0], quantity, material));

            if (blocksMined[0] >= quantity) {
                // Stop mining - we've mined enough BLOCKS
                movement.stop();
                plugin.getLogger().info(String.format("Mining complete: mined %d blocks", blocksMined[0]));
                callback.accept(true);
            }
        };

        // Start mining with block-count limit
        movement.mineWithLimit(material, quantity, onBlockMined);

        // Timeout safety (30 seconds)
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 600;

            @Override
            public void run() {
                if (blocksMined[0] >= quantity) {
                    // Already completed
                    cancel();
                    return;
                }

                ticks++;
                if (ticks >= maxTicks) {
                    // Timeout
                    movement.stop();
                    plugin.getLogger().warning(String.format("Mining timeout: only mined %d/%d blocks",
                            blocksMined[0], quantity));
                    cancel();
                    callback.accept(false);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Hunt mobs asynchronously
     */
    private void huntMobAsync(String target, int quantity, Consumer<Boolean> callback) {
        plugin.getLogger().info(String.format("Hunting %s x%d", target, quantity));

        // TODO: Implement actual hunting with tick-based approach
        // For now, simulate
        FreddyInventory inventory = plugin.getFreddyInventory();

        // Simulate mob drops
        Material dropMaterial = getMobDropMaterial(target);
        if (dropMaterial != null) {
            inventory.add(dropMaterial, quantity);
            plugin.getLogger().info(String.format("Hunted %d %s", quantity, target));
            callback.accept(true);
        } else {
            callback.accept(false);
        }
    }

    /**
     * Craft items (this is fast, can be synchronous)
     */
    private void craftItemAsync(String target, int quantity, Consumer<Boolean> callback) {
        plugin.getLogger().info(String.format("Crafting %s x%d", target, quantity));

        Material material;
        try {
            material = Material.valueOf(target.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + target);
            callback.accept(false);
            return;
        }

        FreddyCraftingService craftingService = plugin.getFreddyCraftingService();
        FreddyInventory inventory = plugin.getFreddyInventory();

        FreddyCraftRequest request = new FreddyCraftRequest();
        request.item = material.name();
        request.amount = quantity;

        FreddyCraftResult result = craftingService.craft(request);

        if (result.crafted && result.craftedAmount > 0) {
            plugin.getLogger().info(String.format("Craft successful: %s", result.message));
            callback.accept(true);
        } else {
            plugin.getLogger().warning(String.format("Craft failed: %s", result.message));
            callback.accept(false);
        }
    }

    /**
     * Verify step actually accomplished its goal
     */
    private boolean verifyProgress(FreddyStep step) {
        FreddyWorkflowAction command = step.getCommandEnum();

        // For MINE and CRAFT commands, verify inventory has the target item
        if (command == FreddyWorkflowAction.FREDDY_MINE || command == FreddyWorkflowAction.FREDDY_CRAFT) {
            try {
                Material stepMaterial = Material.valueOf(step.target.toUpperCase());
                FreddyInventory inventory = plugin.getFreddyInventory();

                // For ores, verify the DROP (RAW_IRON, DIAMOND), not the block
                Material verifyMaterial;
                if (com.freddy.plugin.ai.crafting.ConversionRegistry.isOre(stepMaterial)) {
                    verifyMaterial = com.freddy.plugin.ai.crafting.ConversionRegistry.getOreDrop(stepMaterial);
                    plugin.getLogger().info(String.format("  → Verifying %s drops (from %s blocks)",
                            verifyMaterial.name(), stepMaterial.name()));
                } else {
                    verifyMaterial = stepMaterial;
                }

                int actualCount = inventory.getCount(verifyMaterial);

                if (actualCount < step.quantity) {
                    plugin.getLogger().warning(String.format(
                            "Verification failed: expected %d %s, but inventory only has %d",
                            step.quantity, verifyMaterial.name(), actualCount));
                    return false;
                }

                plugin.getLogger().info(String.format("✓ Verified: Inventory has %d %s",
                        actualCount, verifyMaterial.name()));
                return true;

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material for verification: " + step.target);
                return false;
            }
        }

        // For KILL, verification is harder (mob drops are random)
        // For now, assume success if the step completed
        return true;
    }

    /**
     * Get drop material for mob type
     */
    private Material getMobDropMaterial(String mobType) {
        return switch (mobType.toUpperCase()) {
            case "ZOMBIE" -> Material.ROTTEN_FLESH;
            case "SKELETON" -> Material.BONE;
            case "SPIDER" -> Material.STRING;
            case "CREEPER" -> Material.GUNPOWDER;
            case "COW" -> Material.LEATHER;
            case "PIG" -> Material.PORKCHOP;
            case "SHEEP" -> Material.MUTTON;
            default -> null;
        };
    }
}

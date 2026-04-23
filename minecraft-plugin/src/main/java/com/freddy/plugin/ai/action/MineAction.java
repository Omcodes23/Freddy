package com.freddy.plugin.ai.action;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyInventory;
import com.freddy.plugin.ai.FreddyWorkflowExecutor;
import com.freddy.plugin.ai.FreddyWorldState;
import org.bukkit.Material;

import java.util.function.Consumer;

/**
 * MineAction - Mines blocks and collects drops
 * 
 * Delegates to FreddyWorkflowExecutor.mineBlockAsync()
 * Verifies drops properly (RAW_IRON vs IRON_ORE)
 * 
 * UPDATED: Now fully asynchronous with callback pattern
 */
public class MineAction implements Action {

    private final FreddyPlugin plugin;
    private final Material block;
    private final int quantity;

    public MineAction(FreddyPlugin plugin, Material block, int quantity) {
        this.plugin = plugin;
        this.block = block;
        this.quantity = quantity;
    }

    @Override
    public void executeAsync(FreddyWorldState state, Consumer<ActionResult> callback) {
        plugin.getLogger().info("MineAction: " + block.name() + " x" + quantity);

        FreddyWorkflowExecutor executor = plugin.getFreddyWorkflowExecutor();
        if (executor == null) {
            plugin.getLogger().warning("MineAction: FreddyWorkflowExecutor not initialized");
            callback.accept(ActionResult.FAILED_PERMANENT);
            return;
        }

        // Execute mining asynchronously - callback handles completion
        executor.mineBlockAsync(block.name(), quantity, success -> {
            if (!success) {
                plugin.getLogger().warning("MineAction: Mining failed");
                callback.accept(ActionResult.FAILED_TEMPORARY);
                return;
            }

            // Verify drops were collected
            FreddyInventory inventory = plugin.getFreddyInventory();

            // For ores, check the drop material (RAW_IRON, DIAMOND, etc.)
            Material verifyMaterial = block;
            if (com.freddy.plugin.ai.crafting.ConversionRegistry.isOre(block)) {
                verifyMaterial = com.freddy.plugin.ai.crafting.ConversionRegistry.getOreDrop(block);
            }

            // We can't verify exact quantity due to Fortune, but should have at least
            // something
            if (inventory.count(verifyMaterial) <= 0) {
                plugin.getLogger().warning("MineAction: No drops collected");
                callback.accept(ActionResult.FAILED_TEMPORARY);
                return;
            }

            plugin.getLogger().info("✓ Mined " + quantity + " " + block.name());
            callback.accept(ActionResult.SUCCESS);
        });
    }

    @Override
    public String describe() {
        return "Mine " + quantity + " " + block.name();
    }

    @Override
    public boolean canRetry() {
        return true;
    }

    @Override
    public int getEstimatedDuration() {
        // Mining can take a while (30 seconds default timeout in workflow executor)
        return Math.max(30000, quantity * 5000); // At least 30s, or 5s per block
    }
}

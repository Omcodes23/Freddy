package com.freddy.plugin.ai.action;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyCraftingService;
import com.freddy.plugin.ai.FreddyWorldState;
import org.bukkit.Material;

import java.util.function.Consumer;

/**
 * CraftAction - Executes crafting operations
 * 
 * Wraps the existing AutoCrafter logic.
 * Simple and fast execution.
 * 
 * UPDATED: Now fully asynchronous with callback pattern
 */
public class CraftAction implements Action {

    private final FreddyPlugin plugin;
    private final Material item;
    private final int quantity;

    public CraftAction(FreddyPlugin plugin, Material item, int quantity) {
        this.plugin = plugin;
        this.item = item;
        this.quantity = quantity;
    }

    @Override
    public void executeAsync(FreddyWorldState state, Consumer<ActionResult> callback) {
        plugin.getLogger().info("CraftAction: " + item.name() + " x" + quantity);

        FreddyCraftingService craftingService = plugin.getFreddyCraftingService();
        if (craftingService == null) {
            plugin.getLogger().warning("CraftAction: FreddyCraftingService not initialized");
            callback.accept(ActionResult.FAILED_PERMANENT);
            return;
        }

        // Crafting is fast, execute directly
        // (Could be made async in future if needed)
        try {
            com.freddy.plugin.ai.FreddyCraftRequest request = new com.freddy.plugin.ai.FreddyCraftRequest();
            request.item = item.name();
            request.amount = quantity;

            com.freddy.plugin.ai.FreddyCraftResult result = craftingService.craft(request);

            if (result.crafted && result.craftedAmount > 0) {
                plugin.getLogger().info("✓ Crafted " + quantity + " " + item.name());
                callback.accept(ActionResult.SUCCESS);
            } else {
                plugin.getLogger().warning("CraftAction: Crafting failed - " + result.message);
                callback.accept(ActionResult.FAILED_TEMPORARY);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("CraftAction: Exception - " + e.getMessage());
            callback.accept(ActionResult.FAILED_TEMPORARY);
        }
    }

    @Override
    public String describe() {
        return "Craft " + quantity + " " + item.name();
    }

    @Override
    public boolean canRetry() {
        return true;
    }

    @Override
    public int getEstimatedDuration() {
        // Crafting is fast
        return 1000; // 1 second
    }
}

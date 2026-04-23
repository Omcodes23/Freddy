package com.freddy.plugin.ai.action;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyInventory;
import com.freddy.plugin.ai.FreddyWorldState;
import org.bukkit.Material;

import java.util.function.Consumer;

/**
 * SmeltAction - THE MISSING LAYER
 * 
 * This action fixes the "IRON_INGOT primitive" bug permanently.
 * Smelting is now a first-class action that checks preconditions.
 * 
 * UPDATED: Now fully asynchronous with callback pattern
 */
public class SmeltAction implements Action {

    private final FreddyPlugin plugin;
    private final Material input;
    private final Material output;
    private final int quantity;

    public SmeltAction(FreddyPlugin plugin, Material input, Material output, int quantity) {
        this.plugin = plugin;
        this.input = input;
        this.output = output;
        this.quantity = quantity;
    }

    @Override
    public void executeAsync(FreddyWorldState state, Consumer<ActionResult> callback) {
        plugin.getLogger().info("SmeltAction: " + input.name() + " → " + output.name() + " x" + quantity);

        // [1] Check preconditions - furnace availability
        if (!state.furnaceAvailable) {
            plugin.getLogger().warning("SmeltAction: No furnace available");
            callback.accept(ActionResult.FAILED_TEMPORARY); // Can retry when furnace available
            return;
        }

        // [2] Check preconditions - fuel availability
        if (!state.hasFuel) {
            plugin.getLogger().warning("SmeltAction: No fuel available");
            callback.accept(ActionResult.FAILED_TEMPORARY); // Can retry when fuel available
            return;
        }

        // [3] Verify input materials
        FreddyInventory inventory = plugin.getFreddyInventory();
        if (!inventory.has(input, quantity)) {
            plugin.getLogger().warning("SmeltAction: Missing input materials");
            callback.accept(ActionResult.FAILED_PERMANENT); // Can't smelt without input
            return;
        }

        // [4] Execute smelting (fast operation, can be synchronous)
        boolean success = inventory.smelt(input, output, quantity);

        if (success) {
            plugin.getLogger().info("✓ Smelted " + quantity + " " + input.name() + " → " + output.name());
            callback.accept(ActionResult.SUCCESS);
        } else {
            plugin.getLogger().warning("SmeltAction: Smelting operation failed");
            callback.accept(ActionResult.FAILED_TEMPORARY);
        }
    }

    @Override
    public String describe() {
        return "Smelt " + quantity + " " + input.name() + " → " + output.name();
    }

    @Override
    public boolean canRetry() {
        return true;
    }

    @Override
    public int getEstimatedDuration() {
        // Smelting is fast (just inventory manipulation)
        return 1000; // 1 second
    }
}

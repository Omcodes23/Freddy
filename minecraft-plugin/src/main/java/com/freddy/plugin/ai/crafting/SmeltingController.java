package com.freddy.plugin.ai.crafting;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyInventory;
import org.bukkit.Material;

/**
 * SmeltingController - Handles ore smelting with animation
 * 
 * Converts RAW materials to INGOTS using furnace.
 * Includes realistic smelting delays.
 */
public class SmeltingController {

    private final FreddyPlugin plugin;
    private final FreddyInventory inventory;
    private final FurnaceManager furnaceManager;

    public SmeltingController(FreddyPlugin plugin, FreddyInventory inventory, FurnaceManager furnaceManager) {
        this.plugin = plugin;
        this.inventory = inventory;
        this.furnaceManager = furnaceManager;
    }

    /**
     * Smelt all raw materials that need smelting
     */
    public void smeltAll() {
        // Ensure furnace exists
        furnaceManager.ensureFurnace();

        // Smelt all ore types
        smelt(Material.RAW_IRON, Material.IRON_INGOT);
        smelt(Material.RAW_GOLD, Material.GOLD_INGOT);
        smelt(Material.RAW_COPPER, Material.COPPER_INGOT);
    }

    /**
     * Smelt a specific material
     */
    private void smelt(Material input, Material output) {
        int count = inventory.getCount(input);
        if (count == 0) {
            return; // Nothing to smelt
        }

        plugin.getLogger().info(String.format("  🔥 Smelting %dx %s → %s",
                count, input.name(), output.name()));

        for (int i = 0; i < count; i++) {
            playSmeltAnimation(input, i + 1, count);
            inventory.remove(input, 1);
            inventory.add(output, 1);
        }

        plugin.getLogger().info(String.format("  ✓ Smelted %dx %s", count, output.name()));
    }

    /**
     * Play smelting animation (realistic delay)
     */
    private void playSmeltAnimation(Material input, int current, int total) {
        try {
            Thread.sleep(800); // Realistic furnace tick (reduce from 1200ms for testing)
            plugin.getLogger().info(String.format("    [%d/%d] %s smelting...",
                    current, total, input.name()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

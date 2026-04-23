package com.freddy.plugin.ai.crafting;

import com.freddy.plugin.ai.FreddyInventory;
import org.bukkit.Material;

/**
 * FurnaceManager - Ensures furnace availability
 * 
 * Manages virtual furnace state for smelting operations.
 * Future: Can be extended to place actual furnace blocks.
 */
public class FurnaceManager {

    private final FreddyInventory inventory;
    private boolean furnaceBuilt = false;

    public FurnaceManager(FreddyInventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Ensure furnace exists (craft if needed)
     */
    public void ensureFurnace() {
        if (furnaceBuilt) {
            return; // Already have furnace
        }

        // Check if we have a furnace in inventory
        if (inventory.has(Material.FURNACE, 1)) {
            furnaceBuilt = true;
            return;
        }

        // Craft furnace from cobblestone
        if (inventory.has(Material.COBBLESTONE, 8)) {
            inventory.remove(Material.COBBLESTONE, 8);
            inventory.add(Material.FURNACE, 1);
            furnaceBuilt = true;
        } else {
            throw new IllegalStateException("Cannot build furnace: need 8 cobblestone");
        }
    }

    public boolean hasFurnace() {
        return furnaceBuilt || inventory.has(Material.FURNACE, 1);
    }
}

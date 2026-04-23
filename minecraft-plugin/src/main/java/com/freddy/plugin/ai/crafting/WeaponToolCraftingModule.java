package com.freddy.plugin.ai.crafting;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyInventory;
import org.bukkit.Material;

/**
 * WeaponToolCraftingModule - Specialized crafting for weapons and tools
 * 
 * Handles the complete crafting flow for weapons/tools including:
 * - Furnace creation
 * - Smelting (RAW_IRON → IRON_INGOT)
 * - Dependency crafting (sticks, planks)
 * - Final item crafting
 * 
 * This module is called by AutoCrafter when it detects a weapon/tool.
 */
public class WeaponToolCraftingModule {

    private final FreddyPlugin plugin;
    private final FreddyInventory inventory;
    private final FurnaceManager furnaceManager;
    private final SmeltingController smelter;
    private final AutoCrafter autoCrafter; // For crafting dependencies

    public WeaponToolCraftingModule(FreddyPlugin plugin, FreddyInventory inventory, AutoCrafter autoCrafter) {
        this.plugin = plugin;
        this.inventory = inventory;
        this.furnaceManager = new FurnaceManager(inventory);
        this.smelter = new SmeltingController(plugin, inventory, furnaceManager);
        this.autoCrafter = autoCrafter;
    }

    /**
     * Craft a weapon or tool
     * 
     * @param target Weapon/tool to craft
     * @return true if successful
     */
    public boolean craft(Material target) {
        ToolRecipeGraph.ToolRecipe recipe = ToolRecipeGraph.get(target);
        if (recipe == null) {
            plugin.getLogger().warning("Not a weapon/tool: " + target.name());
            return false;
        }

        plugin.getLogger().info("🔨 Weapon/Tool Crafting: " + target.name());

        // 1️⃣ Smelt if needed (RAW_IRON → IRON_INGOT)
        if (recipe.requiresSmelting()) {
            plugin.getLogger().info("  → Smelting required");
            smelter.smeltAll();
        }

        // 2️⃣ Craft dependencies (sticks, planks, etc.)
        plugin.getLogger().info("  → Crafting dependencies");
        for (Material dep : recipe.dependencies()) {
            if (RecipeRegistry.isCraftable(dep)) {
                // Calculate how many we need
                int needed = recipe.inputs().get(dep);
                int have = inventory.getCount(dep);

                if (have < needed) {
                    plugin.getLogger().info(String.format("    → Need %dx %s (have %d)",
                            needed - have, dep.name(), have));

                    // Recursively craft dependency using AutoCrafter
                    boolean success = autoCrafter.craftRecursively(dep, needed - have);
                    if (!success) {
                        plugin.getLogger().warning("    ✗ Failed to craft dependency: " + dep.name());
                        return false;
                    }
                }
            }
        }

        // 3️⃣ Verify we have all materials
        for (var entry : recipe.inputs().entrySet()) {
            Material material = entry.getKey();
            int required = entry.getValue();
            int have = inventory.getCount(material);

            if (have < required) {
                plugin.getLogger().warning(String.format(
                        "  ✗ Missing materials: need %dx %s, have %d",
                        required, material.name(), have));
                return false;
            }
        }

        // 4️⃣ Final craft
        plugin.getLogger().info("  → Crafting final item");
        playCraftAnimation(target);

        // Consume materials
        for (var entry : recipe.inputs().entrySet()) {
            inventory.remove(entry.getKey(), entry.getValue());
        }

        // Add crafted item
        inventory.add(target, 1);

        plugin.getLogger().info("  ✓ Crafted 1x " + target.name());
        return true;
    }

    /**
     * Play crafting animation (realistic delay)
     */
    private void playCraftAnimation(Material item) {
        try {
            Thread.sleep(600); // Crafting delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

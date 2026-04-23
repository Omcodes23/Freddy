package com.freddy.plugin.ai.crafting;

import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyCraftingService;
import com.freddy.plugin.ai.FreddyInventory;
import org.bukkit.Material;

/**
 * AutoCrafter - Deterministic recursive crafting
 * 
 * Crafts items in dependency order WITHOUT any LLM involvement.
 * Delegates weapon/tool crafting to WeaponToolCraftingModule.
 * 
 * Algorithm:
 * 1. If weapon/tool, delegate to WeaponToolCraftingModule
 * 2. If primitive (no recipe), skip
 * 3. Get recipe from RecipeRegistry
 * 4. Recursively craft all dependencies first
 * 5. Craft the item itself
 */
public class AutoCrafter {

    private final FreddyPlugin plugin;
    private final FreddyCraftingService craftingService;
    private WeaponToolCraftingModule weaponToolModule;

    public AutoCrafter(FreddyPlugin plugin, FreddyCraftingService craftingService) {
        this.plugin = plugin;
        this.craftingService = craftingService;
    }

    /**
     * Initialize weapon/tool module (can't do in constructor due to circular
     * dependency)
     */
    public void setWeaponToolModule(WeaponToolCraftingModule module) {
        this.weaponToolModule = module;
    }

    /**
     * Recursively craft an item and all its dependencies
     * 
     * @param item   Item to craft
     * @param amount Quantity to craft
     * @return true if crafting succeeded
     */
    public boolean craftRecursively(Material item, int amount) {
        plugin.getLogger().info("AutoCraft: " + item.name() + " x" + amount);

        // Special case: Delegate weapons/tools to specialized module
        if (ToolRecipeGraph.isWeaponOrTool(item)) {
            plugin.getLogger().info("  → Delegating to WeaponToolCraftingModule");

            if (weaponToolModule == null) {
                plugin.getLogger().warning("  ✗ WeaponToolCraftingModule not initialized!");
                return false;
            }

            // Craft one at a time (tools don't stack in recipes)
            for (int i = 0; i < amount; i++) {
                boolean success = weaponToolModule.craft(item);
                if (!success) {
                    return false;
                }
            }
            return true;
        }

        // Base case: If no recipe, this is a primitive
        if (!RecipeRegistry.isCraftable(item)) {
            plugin.getLogger().info("  → Primitive, skipping");
            return true;
        }

        // Get recipe
        RecipeOption recipe = RecipeRegistry.get(item).get(0);
        plugin.getLogger().info("  → Recipe: " + recipe);

        // Calculate craft multiplier (how many times to craft)
        // For simplicity, we assume recipe yields 1 item
        // TODO: Handle recipes with yields > 1 (e.g., 1 log → 4 planks)
        int craftTimes = amount;

        // Recursive case: Craft all dependencies first
        for (var entry : recipe.inputs.entrySet()) {
            Material dependency = entry.getKey();
            int dependencyQty = entry.getValue() * craftTimes;

            boolean success = craftRecursively(dependency, dependencyQty);
            if (!success) {
                plugin.getLogger().warning("  ✗ Failed to craft dependency: " + dependency.name());
                return false;
            }
        }

        // Now craft this item using FreddyCraftingService
        plugin.getLogger().info("  → Crafting " + item.name() + " x" + amount);

        // Use existing crafting service
        com.freddy.plugin.ai.FreddyCraftRequest request = new com.freddy.plugin.ai.FreddyCraftRequest();
        request.item = item.name();
        request.amount = amount;

        com.freddy.plugin.ai.FreddyCraftResult result = craftingService.craft(request);

        if (result.crafted) {
            plugin.getLogger().info("  ✓ Crafted " + result.craftedAmount + "x " + result.craftedItem);
            return true;
        } else {
            plugin.getLogger().warning("  ✗ Craft failed: " + result.message);
            return false;
        }
    }

    /**
     * Craft with better logging
     */
    public boolean craftWithPlan(Material item, int amount) {
        plugin.getLogger().info("========== AUTO CRAFTING ==========");
        plugin.getLogger().info("Target: " + item.name() + " x" + amount);
        plugin.getLogger().info("");

        boolean success = craftRecursively(item, amount);

        plugin.getLogger().info("");
        if (success) {
            plugin.getLogger().info("✓ Auto-crafting completed successfully");
        } else {
            plugin.getLogger().info("✗ Auto-crafting failed");
        }
        plugin.getLogger().info("===================================");

        return success;
    }
}

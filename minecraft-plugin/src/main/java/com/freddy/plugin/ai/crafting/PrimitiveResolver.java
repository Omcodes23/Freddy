package com.freddy.plugin.ai.crafting;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * PrimitiveResolver - Recursive recipe expansion
 * 
 * Resolves craftable items to their primitive requirements.
 * This is DETERMINISTIC - no LLM involvement.
 * 
 * Example:
 * DIAMOND_SWORD → { DIAMOND: 2, STICK: 1 }
 * → { DIAMOND: 2, OAK_PLANKS: 2 }
 * → { DIAMOND: 2, OAK_LOG: 1 }
 * 
 * ✅ STICK and PLANKS are eliminated
 * ✅ Only gatherable primitives remain
 */
public class PrimitiveResolver {

    /**
     * Resolve an item to its primitive requirements
     * 
     * @param target Item to craft
     * @param amount Quantity needed
     * @return Map of primitive materials to quantities
     */
    public Map<Material, Integer> resolve(Material target, int amount) {
        Map<Material, Integer> result = new HashMap<>();
        expand(target, amount, result);
        return result;
    }

    /**
     * Recursively expand item requirements
     * 
     * EXPANSION ORDER:
     * 1. Smelting (INGOT → ORE)
     * 2. Crafting (STICK → PLANKS → LOG)
     * 3. Primitive (gatherable from world)
     * 
     * @param item   Current item to expand
     * @param qty    Quantity needed
     * @param output Accumulator for primitive requirements
     */
    private void expand(Material item, int qty, Map<Material, Integer> output) {

        // [1] Check if this is a direct drop (DIAMOND → DIAMOND_ORE, etc.)
        if (ConversionRegistry.isDirectDrop(item)) {
            Material ore = ConversionRegistry.getOreForDrop(item);
            output.merge(ore, qty, Integer::sum);
            return;
        }

        // [2] Check if this requires smelting (INGOT → ORE)
        if (ConversionRegistry.requiresSmelting(item)) {
            Material ore = ConversionRegistry.getRequiredOre(item);

            // ORE is primitive (gatherable from world)
            output.merge(ore, qty, Integer::sum);
            return;
        }

        // [3] Check if craftable
        if (!RecipeRegistry.isCraftable(item)) {
            // Primitive (gatherable from world)
            output.merge(item, qty, Integer::sum);
            return;
        }

        // [4] Expand crafting recipe
        RecipeOption recipe = RecipeRegistry.get(item).get(0);

        // Recursively expand each ingredient
        for (Map.Entry<Material, Integer> entry : recipe.inputs.entrySet()) {
            Material ingredient = entry.getKey();
            int ingredientQty = entry.getValue();

            // Recursively expand this ingredient
            expand(ingredient, ingredientQty * qty, output);
        }
    }

    /**
     * Check if an item requires any primitives
     * 
     * @param target Item to check
     * @param amount Quantity
     * @return true if item needs primitive gathering
     */
    public boolean needsGathering(Material target, int amount) {
        Map<Material, Integer> primitives = resolve(target, amount);
        return !primitives.isEmpty();
    }

    /**
     * Get human-readable breakdown of requirements
     * 
     * @param target Item to analyze
     * @param amount Quantity
     * @return String description
     */
    public String getBreakdown(Material target, int amount) {
        Map<Material, Integer> primitives = resolve(target, amount);

        if (primitives.isEmpty()) {
            return "No primitives required (already primitive)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Primitive requirements for ").append(target.name()).append(" x").append(amount).append(":\n");

        for (Map.Entry<Material, Integer> entry : primitives.entrySet()) {
            sb.append("  - ").append(entry.getKey().name())
                    .append(" x").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }
}

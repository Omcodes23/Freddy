package com.freddy.plugin.ai.crafting;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * RecipeOption - Single crafting recipe option
 * 
 * Represents one way to craft an item.
 * Multiple RecipeOptions may exist for the same item (alternative recipes).
 * 
 * Example:
 * STICK recipe: { OAK_PLANKS: 2 }
 * DIAMOND_SWORD recipe: { DIAMOND: 2, STICK: 1 }
 */
public class RecipeOption {

    /**
     * Input materials and their quantities
     * Key: Material type
     * Value: Required quantity
     */
    public Map<Material, Integer> inputs = new HashMap<>();

    /**
     * How many items this recipe produces
     * Default: 1
     */
    public int yield = 1;

    /**
     * Default constructor
     */
    public RecipeOption() {
    }

    /**
     * Add an input material to the recipe
     * 
     * @param material Material type
     * @param quantity Required quantity
     */
    public void addInput(Material material, int quantity) {
        inputs.put(material, quantity);
    }

    /**
     * Check if recipe is valid (has at least one input)
     */
    public boolean isValid() {
        return !inputs.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Recipe[");
        boolean first = true;
        for (Map.Entry<Material, Integer> entry : inputs.entrySet()) {
            if (!first)
                sb.append(", ");
            sb.append(entry.getKey().name()).append(" x").append(entry.getValue());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}

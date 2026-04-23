package com.freddy.plugin.ai.crafting;

import org.bukkit.Material;

import java.util.Map;
import java.util.Set;

/**
 * ToolRecipeGraph - Deterministic recipes for weapons and tools
 * 
 * Defines which items are weapons/tools and their specific requirements.
 */
public final class ToolRecipeGraph {

    /**
     * Tool/Weapon recipes
     */
    private static final Map<Material, ToolRecipe> RECIPES = Map.ofEntries(
            // Swords
            Map.entry(Material.WOODEN_SWORD, new ToolRecipe(Map.of(Material.OAK_PLANKS, 2, Material.STICK, 1), false)),
            Map.entry(Material.STONE_SWORD, new ToolRecipe(Map.of(Material.COBBLESTONE, 2, Material.STICK, 1), false)),
            Map.entry(Material.IRON_SWORD, new ToolRecipe(Map.of(Material.IRON_INGOT, 2, Material.STICK, 1), true)),
            Map.entry(Material.GOLDEN_SWORD, new ToolRecipe(Map.of(Material.GOLD_INGOT, 2, Material.STICK, 1), true)),
            Map.entry(Material.DIAMOND_SWORD, new ToolRecipe(Map.of(Material.DIAMOND, 2, Material.STICK, 1), false)),

            // Pickaxes
            Map.entry(Material.WOODEN_PICKAXE,
                    new ToolRecipe(Map.of(Material.OAK_PLANKS, 3, Material.STICK, 2), false)),
            Map.entry(Material.STONE_PICKAXE,
                    new ToolRecipe(Map.of(Material.COBBLESTONE, 3, Material.STICK, 2), false)),
            Map.entry(Material.IRON_PICKAXE, new ToolRecipe(Map.of(Material.IRON_INGOT, 3, Material.STICK, 2), true)),
            Map.entry(Material.GOLDEN_PICKAXE, new ToolRecipe(Map.of(Material.GOLD_INGOT, 3, Material.STICK, 2), true)),
            Map.entry(Material.DIAMOND_PICKAXE, new ToolRecipe(Map.of(Material.DIAMOND, 3, Material.STICK, 2), false)),

            // Axes
            Map.entry(Material.WOODEN_AXE, new ToolRecipe(Map.of(Material.OAK_PLANKS, 3, Material.STICK, 2), false)),
            Map.entry(Material.STONE_AXE, new ToolRecipe(Map.of(Material.COBBLESTONE, 3, Material.STICK, 2), false)),
            Map.entry(Material.IRON_AXE, new ToolRecipe(Map.of(Material.IRON_INGOT, 3, Material.STICK, 2), true)),
            Map.entry(Material.GOLDEN_AXE, new ToolRecipe(Map.of(Material.GOLD_INGOT, 3, Material.STICK, 2), true)),
            Map.entry(Material.DIAMOND_AXE, new ToolRecipe(Map.of(Material.DIAMOND, 3, Material.STICK, 2), false)),

            // Shovels
            Map.entry(Material.WOODEN_SHOVEL, new ToolRecipe(Map.of(Material.OAK_PLANKS, 1, Material.STICK, 2), false)),
            Map.entry(Material.STONE_SHOVEL, new ToolRecipe(Map.of(Material.COBBLESTONE, 1, Material.STICK, 2), false)),
            Map.entry(Material.IRON_SHOVEL, new ToolRecipe(Map.of(Material.IRON_INGOT, 1, Material.STICK, 2), true)),
            Map.entry(Material.GOLDEN_SHOVEL, new ToolRecipe(Map.of(Material.GOLD_INGOT, 1, Material.STICK, 2), true)),
            Map.entry(Material.DIAMOND_SHOVEL, new ToolRecipe(Map.of(Material.DIAMOND, 1, Material.STICK, 2), false)),

            // Hoes
            Map.entry(Material.WOODEN_HOE, new ToolRecipe(Map.of(Material.OAK_PLANKS, 2, Material.STICK, 2), false)),
            Map.entry(Material.STONE_HOE, new ToolRecipe(Map.of(Material.COBBLESTONE, 2, Material.STICK, 2), false)),
            Map.entry(Material.IRON_HOE, new ToolRecipe(Map.of(Material.IRON_INGOT, 2, Material.STICK, 2), true)),
            Map.entry(Material.GOLDEN_HOE, new ToolRecipe(Map.of(Material.GOLD_INGOT, 2, Material.STICK, 2), true)),
            Map.entry(Material.DIAMOND_HOE, new ToolRecipe(Map.of(Material.DIAMOND, 2, Material.STICK, 2), false)));

    public static ToolRecipe get(Material material) {
        return RECIPES.get(material);
    }

    public static boolean isWeaponOrTool(Material material) {
        return RECIPES.containsKey(material);
    }

    /**
     * Tool recipe definition
     */
    public record ToolRecipe(
            Map<Material, Integer> inputs,
            boolean requiresSmelting) {
        public Set<Material> dependencies() {
            return inputs.keySet();
        }
    }
}

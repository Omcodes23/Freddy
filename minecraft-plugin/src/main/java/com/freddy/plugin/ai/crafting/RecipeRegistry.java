package com.freddy.plugin.ai.crafting;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RecipeRegistry - Deterministic recipe knowledge base
 * 
 * This is the SINGLE SOURCE OF TRUTH for crafting recipes.
 * NO LLM should ever decide recipes - only this registry.
 * 
 * Rules:
 * - Recipes are immutable once registered
 * - Only primitive items (gatherable) have no recipes
 * - Primitives: ORE blocks, LOGs, MOB_DROPS, etc.
 */
public class RecipeRegistry {

    private static final Map<Material, List<RecipeOption>> RECIPES = new HashMap<>();

    static {
        registerVanillaRecipes();
    }

    /**
     * Register all vanilla Minecraft recipes
     */
    private static void registerVanillaRecipes() {

        // ==================== WOOD PROCESSING ====================

        // PLANKS (all wood types yield 4 planks)
        registerPlanks(Material.OAK_LOG, Material.OAK_PLANKS);
        registerPlanks(Material.SPRUCE_LOG, Material.SPRUCE_PLANKS);
        registerPlanks(Material.BIRCH_LOG, Material.BIRCH_PLANKS);
        registerPlanks(Material.JUNGLE_LOG, Material.JUNGLE_PLANKS);
        registerPlanks(Material.ACACIA_LOG, Material.ACACIA_PLANKS);
        registerPlanks(Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS);
        registerPlanks(Material.MANGROVE_LOG, Material.MANGROVE_PLANKS);
        registerPlanks(Material.CHERRY_LOG, Material.CHERRY_PLANKS);

        // STICKS (2 planks → 4 sticks)
        RecipeOption stick = new RecipeOption();
        stick.addInput(Material.OAK_PLANKS, 2);
        register(Material.STICK, stick);

        // ==================== CRAFTING TABLE ====================

        RecipeOption craftingTable = new RecipeOption();
        craftingTable.addInput(Material.OAK_PLANKS, 4);
        register(Material.CRAFTING_TABLE, craftingTable);

        // ==================== WOODEN TOOLS ====================

        RecipeOption woodenPickaxe = new RecipeOption();
        woodenPickaxe.addInput(Material.OAK_PLANKS, 3);
        woodenPickaxe.addInput(Material.STICK, 2);
        register(Material.WOODEN_PICKAXE, woodenPickaxe);

        RecipeOption woodenAxe = new RecipeOption();
        woodenAxe.addInput(Material.OAK_PLANKS, 3);
        woodenAxe.addInput(Material.STICK, 2);
        register(Material.WOODEN_AXE, woodenAxe);

        RecipeOption woodenSword = new RecipeOption();
        woodenSword.addInput(Material.OAK_PLANKS, 2);
        woodenSword.addInput(Material.STICK, 1);
        register(Material.WOODEN_SWORD, woodenSword);

        RecipeOption woodenShovel = new RecipeOption();
        woodenShovel.addInput(Material.OAK_PLANKS, 1);
        woodenShovel.addInput(Material.STICK, 2);
        register(Material.WOODEN_SHOVEL, woodenShovel);

        RecipeOption woodenHoe = new RecipeOption();
        woodenHoe.addInput(Material.OAK_PLANKS, 2);
        woodenHoe.addInput(Material.STICK, 2);
        register(Material.WOODEN_HOE, woodenHoe);

        // ==================== STONE TOOLS ====================

        RecipeOption stonePickaxe = new RecipeOption();
        stonePickaxe.addInput(Material.COBBLESTONE, 3);
        stonePickaxe.addInput(Material.STICK, 2);
        register(Material.STONE_PICKAXE, stonePickaxe);

        RecipeOption stoneAxe = new RecipeOption();
        stoneAxe.addInput(Material.COBBLESTONE, 3);
        stoneAxe.addInput(Material.STICK, 2);
        register(Material.STONE_AXE, stoneAxe);

        RecipeOption stoneSword = new RecipeOption();
        stoneSword.addInput(Material.COBBLESTONE, 2);
        stoneSword.addInput(Material.STICK, 1);
        register(Material.STONE_SWORD, stoneSword);

        RecipeOption stoneShovel = new RecipeOption();
        stoneShovel.addInput(Material.COBBLESTONE, 1);
        stoneShovel.addInput(Material.STICK, 2);
        register(Material.STONE_SHOVEL, stoneShovel);

        RecipeOption stoneHoe = new RecipeOption();
        stoneHoe.addInput(Material.COBBLESTONE, 2);
        stoneHoe.addInput(Material.STICK, 2);
        register(Material.STONE_HOE, stoneHoe);

        // ==================== IRON TOOLS ====================

        RecipeOption ironPickaxe = new RecipeOption();
        ironPickaxe.addInput(Material.IRON_INGOT, 3);
        ironPickaxe.addInput(Material.STICK, 2);
        register(Material.IRON_PICKAXE, ironPickaxe);

        RecipeOption ironAxe = new RecipeOption();
        ironAxe.addInput(Material.IRON_INGOT, 3);
        ironAxe.addInput(Material.STICK, 2);
        register(Material.IRON_AXE, ironAxe);

        RecipeOption ironSword = new RecipeOption();
        ironSword.addInput(Material.IRON_INGOT, 2);
        ironSword.addInput(Material.STICK, 1);
        register(Material.IRON_SWORD, ironSword);

        RecipeOption ironShovel = new RecipeOption();
        ironShovel.addInput(Material.IRON_INGOT, 1);
        ironShovel.addInput(Material.STICK, 2);
        register(Material.IRON_SHOVEL, ironShovel);

        RecipeOption ironHoe = new RecipeOption();
        ironHoe.addInput(Material.IRON_INGOT, 2);
        ironHoe.addInput(Material.STICK, 2);
        register(Material.IRON_HOE, ironHoe);

        // ==================== DIAMOND TOOLS ====================

        RecipeOption diamondPickaxe = new RecipeOption();
        diamondPickaxe.addInput(Material.DIAMOND, 3);
        diamondPickaxe.addInput(Material.STICK, 2);
        register(Material.DIAMOND_PICKAXE, diamondPickaxe);

        RecipeOption diamondAxe = new RecipeOption();
        diamondAxe.addInput(Material.DIAMOND, 3);
        diamondAxe.addInput(Material.STICK, 2);
        register(Material.DIAMOND_AXE, diamondAxe);

        RecipeOption diamondSword = new RecipeOption();
        diamondSword.addInput(Material.DIAMOND, 2);
        diamondSword.addInput(Material.STICK, 1);
        register(Material.DIAMOND_SWORD, diamondSword);

        RecipeOption diamondShovel = new RecipeOption();
        diamondShovel.addInput(Material.DIAMOND, 1);
        diamondShovel.addInput(Material.STICK, 2);
        register(Material.DIAMOND_SHOVEL, diamondShovel);

        RecipeOption diamondHoe = new RecipeOption();
        diamondHoe.addInput(Material.DIAMOND, 2);
        diamondHoe.addInput(Material.STICK, 2);
        register(Material.DIAMOND_HOE, diamondHoe);

        // ==================== FURNACE ====================

        RecipeOption furnace = new RecipeOption();
        furnace.addInput(Material.COBBLESTONE, 8);
        register(Material.FURNACE, furnace);

        // ==================== TORCHES ====================

        RecipeOption torch = new RecipeOption();
        torch.addInput(Material.COAL, 1);
        torch.addInput(Material.STICK, 1);
        register(Material.TORCH, torch);
    }

    /**
     * Helper: Register plank recipes (1 log → 4 planks)
     */
    private static void registerPlanks(Material log, Material planks) {
        RecipeOption recipe = new RecipeOption();
        recipe.addInput(log, 1);
        register(planks, recipe);
    }

    /**
     * Register a recipe for a material
     */
    private static void register(Material output, RecipeOption recipe) {
        RECIPES.put(output, List.of(recipe));
    }

    // ==================== PUBLIC API ====================

    /**
     * Get all recipe options for a material
     * 
     * @param material Material to craft
     * @return List of recipe options (empty if not craftable)
     */
    public static List<RecipeOption> get(Material material) {
        return RECIPES.getOrDefault(material, List.of());
    }

    /**
     * Check if a material is craftable
     * 
     * @param material Material to check
     * @return true if material has at least one recipe
     */
    public static boolean isCraftable(Material material) {
        return RECIPES.containsKey(material);
    }

    /**
     * Check if a material is primitive (cannot be crafted, must be gathered)
     * 
     * @param material Material to check
     * @return true if material is primitive
     */
    public static boolean isPrimitive(Material material) {
        return !isCraftable(material);
    }

    /**
     * Get total number of registered recipes
     */
    public static int getRecipeCount() {
        return RECIPES.size();
    }
}

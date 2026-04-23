package com.freddy.plugin.ai.planning;

import com.freddy.plugin.ai.action.*;
import com.freddy.plugin.ai.crafting.ConversionRegistry;
import com.freddy.plugin.ai.crafting.RecipeOption;
import com.freddy.plugin.ai.crafting.RecipeRegistry;
import com.freddy.plugin.FreddyPlugin;
import org.bukkit.Material;

import java.util.*;

/**
 * DeterministicPlanner - Build dependency graphs WITHOUT LLM
 * 
 * This planner NEVER:
 * - Calls LLM for recipes
 * - Invents materials
 * - Skips smelting
 * - Chooses alternatives
 * 
 * It ONLY uses RecipeRegistry and ConversionRegistry.
 * 
 * Example output for IRON_SWORD:
 * IRON_SWORD x1
 * ├─ IRON_INGOT x2 [SMELT]
 * │ ├─ RAW_IRON x2 [PRIMITIVE]
 * │ │ └─ IRON_ORE x2 [PRIMITIVE]
 * │ └─ FURNACE x1 [CRAFT]
 * └─ STICK x1 [CRAFT]
 * └─ OAK_PLANKS x2 [CRAFT]
 * └─ OAK_LOG x1 [PRIMITIVE]
 */
public class DeterministicPlanner {

    private final FreddyPlugin plugin;

    public DeterministicPlanner(FreddyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Plan how to obtain a target item
     * 
     * @param target   Material to obtain
     * @param quantity How many needed
     * @return Root plan node with full dependency tree
     */
    public PlanNode plan(Material target, int quantity) {
        plugin.getLogger().info(String.format("Planning for %s x%d", target.name(), quantity));

        PlanNode root = expand(target, quantity);

        plugin.getLogger().info("Plan created:\n" + root.visualize());

        return root;
    }

    /**
     * Recursively expand a material into its dependencies
     */
    private PlanNode expand(Material item, int qty) {

        // [1] Check if this requires smelting (INGOT → ORE)
        if (ConversionRegistry.requiresSmelting(item)) {
            PlanNode node = new PlanNode(item, qty, PlanNode.NodeType.SMELT);

            // Get the ore required for smelting
            Material ore = ConversionRegistry.getRequiredOre(item);

            // Recursively expand the ore (which might need mining)
            PlanNode oreDep = expand(ore, qty);
            node.addDependency(oreDep);

            // Also need furnace (if we don't check inventory, we assume we might need it)
            // For now, we'll assume furnace is available or can be crafted

            return node;
        }

        // [2] Check if this is a direct drop (DIAMOND → DIAMOND_ORE)
        if (ConversionRegistry.isDirectDrop(item)) {
            Material ore = ConversionRegistry.getOreForDrop(item);
            return new PlanNode(ore, qty, PlanNode.NodeType.PRIMITIVE);
        }

        // [3] Check if craftable
        if (!RecipeRegistry.isCraftable(item)) {
            // Primitive (gatherable from world)
            return new PlanNode(item, qty, PlanNode.NodeType.PRIMITIVE);
        }

        // [4] Expand crafting recipe
        PlanNode node = new PlanNode(item, qty, PlanNode.NodeType.CRAFT);

        List<RecipeOption> recipes = RecipeRegistry.get(item);
        if (recipes.isEmpty()) {
            plugin.getLogger().warning("No recipe found for craftable item: " + item.name());
            return new PlanNode(item, qty, PlanNode.NodeType.PRIMITIVE);
        }

        // Use first recipe (deterministic choice)
        RecipeOption recipe = recipes.get(0);

        // Calculate how many times to craft based on yield
        int craftCount = (int) Math.ceil((double) qty / recipe.yield);

        // Recursively expand each ingredient
        for (Map.Entry<Material, Integer> entry : recipe.inputs.entrySet()) {
            Material ingredient = entry.getKey();
            int ingredientPerCraft = entry.getValue();
            int totalNeeded = ingredientPerCraft * craftCount;

            PlanNode ingredientNode = expand(ingredient, totalNeeded);
            node.addDependency(ingredientNode);
        }

        return node;
    }

    /**
     * Convert plan tree into ordered list of actions
     * 
     * Actions are ordered bottom-up (dependencies first).
     */
    public List<Action> planToActions(PlanNode plan) {
        List<Action> actions = new ArrayList<>();
        collectActions(plan, actions, new HashSet<>());
        return actions;
    }

    /**
     * Recursively collect actions from plan tree
     */
    private void collectActions(PlanNode node, List<Action> actions, Set<String> processed) {
        // Create unique key for this node
        String key = node.item.name() + ":" + node.quantity;

        // Skip if already processed (avoid duplicates)
        if (processed.contains(key)) {
            return;
        }
        processed.add(key);

        // Process dependencies first (bottom-up)
        for (PlanNode dep : node.dependencies) {
            collectActions(dep, actions, processed);
        }

        // Create action for this node
        Action action = createAction(node);
        if (action != null) {
            actions.add(action);
        }
    }

    /**
     * Create appropriate action for a plan node
     */
    private Action createAction(PlanNode node) {
        switch (node.type) {
            case PRIMITIVE:
                // Mining action
                return new MineAction(plugin, node.item, node.quantity);

            case SMELT:
                // Smelting action
                Material input = ConversionRegistry.getRequiredOre(node.item);
                return new SmeltAction(plugin, input, node.item, node.quantity);

            case CRAFT:
                // Crafting action
                return new CraftAction(plugin, node.item, node.quantity);

            default:
                plugin.getLogger().warning("Unknown node type: " + node.type);
                return null;
        }
    }

    /**
     * Get list of all primitives needed (for gathering plan)
     */
    public Map<Material, Integer> getPrimitives(PlanNode plan) {
        Map<Material, Integer> primitives = new HashMap<>();

        for (PlanNode node : plan.getPrimitives()) {
            primitives.merge(node.item, node.quantity, Integer::sum);
        }

        return primitives;
    }
}

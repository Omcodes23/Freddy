package com.freddy.plugin.ai;

import com.freddy.plugin.npc.NPCInventory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FreddyCraftingService {

    private final NPCInventory inventory;
    private final FreddyInventory virtualInventory;

    public FreddyCraftingService(NPCInventory inventory) {
        this.inventory = inventory;
        this.virtualInventory = null;
    }

    public FreddyCraftingService(FreddyInventory inventory) {
        this.inventory = null;
        this.virtualInventory = inventory;
    }

    public FreddyCraftResult craft(FreddyCraftRequest request) {
        if (request == null || request.item == null || request.item.trim().isEmpty()) {
            return new FreddyCraftResult(false, null, 0, Map.of(), "Missing craft item.");
        }

        Material output = resolveMaterial(request.item);
        if (output == null) {
            return new FreddyCraftResult(false, request.item, 0, Map.of(), "Unknown item: " + request.item);
        }

        int amount = Math.max(1, request.amount);
        Map<Material, Integer> required = recipeFor(output, amount);
        if (required == null) {
            return new FreddyCraftResult(false, output.name(), 0, Map.of(), "No recipe available for " + output.name());
        }

        Map<Material, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int have = countInInventory(entry.getKey());
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }

        if (!missing.isEmpty()) {
            return new FreddyCraftResult(false, output.name(), 0, missing, "Missing required materials.");
        }

        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            removeFromInventory(entry.getKey(), entry.getValue());
        }

        addToInventory(output, amount);
        return new FreddyCraftResult(true, output.name(), amount, Map.of(), "Crafted successfully.");
    }

    public Map<String, Integer> getMissingItems(FreddyCraftRequest request) {
        if (request == null || request.item == null || request.item.trim().isEmpty()) {
            return Map.of();
        }

        Material output = resolveMaterial(request.item);
        if (output == null) {
            return Map.of();
        }

        int amount = Math.max(1, request.amount);
        Map<Material, Integer> required = recipeFor(output, amount);
        if (required == null) {
            return Map.of();
        }

        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int have = countInInventory(entry.getKey());
            if (have < entry.getValue()) {
                missing.put(entry.getKey().name(), entry.getValue() - have);
            }
        }
        return missing;
    }

    private int countInInventory(Material material) {
        if (inventory != null) {
            return inventory.getCount(material);
        }
        if (virtualInventory != null) {
            return virtualInventory.getCount(material);
        }
        return 0;
    }

    private void removeFromInventory(Material material, int amount) {
        if (amount <= 0) {
            return;
        }

        if (inventory != null) {
            for (int i = 0; i < amount; i++) {
                inventory.removeItem(material);
            }
            return;
        }

        if (virtualInventory != null) {
            virtualInventory.remove(material, amount);
        }
    }

    private void addToInventory(Material material, int amount) {
        if (amount <= 0) {
            return;
        }

        if (inventory != null) {
            inventory.addItem(new ItemStack(material, amount));
            return;
        }

        if (virtualInventory != null) {
            virtualInventory.add(material, amount);
        }
    }

    private Material resolveMaterial(String itemName) {
        try {
            return Material.valueOf(itemName.trim().toUpperCase());
        } catch (Exception ignore) {
            return null;
        }
    }

    private Map<Material, Integer> recipeFor(Material output, int amount) {
        Map<Material, Integer> dynamic = recipeFromServer(output, amount);
        if (dynamic != null) {
            return dynamic;
        }

        Map<Material, Integer> recipe = new LinkedHashMap<>();

        // Some recipes yield 4 items per craft operation. When using the fallback table below,
        // approximate required inputs by number of craft operations needed.
        int ops4 = (int) Math.ceil(Math.max(1, amount) / 4.0);

        switch (output) {
            case OAK_PLANKS -> recipe.put(Material.OAK_LOG, ops4);
            case STICK -> recipe.put(Material.OAK_PLANKS, ops4 * 2);
            case CRAFTING_TABLE -> recipe.put(Material.OAK_PLANKS, 4 * amount);
            case FURNACE -> recipe.put(Material.COBBLESTONE, 8 * amount);
            case BREAD -> recipe.put(Material.WHEAT, 3 * amount);
            case STONE_PICKAXE -> {
                recipe.put(Material.COBBLESTONE, 3 * amount);
                recipe.put(Material.STICK, 2 * amount);
            }
            case STONE_AXE -> {
                recipe.put(Material.COBBLESTONE, 3 * amount);
                recipe.put(Material.STICK, 2 * amount);
            }
            case STONE_SHOVEL -> {
                recipe.put(Material.COBBLESTONE, 1 * amount);
                recipe.put(Material.STICK, 2 * amount);
            }
            case STONE_SWORD -> {
                recipe.put(Material.COBBLESTONE, 2 * amount);
                recipe.put(Material.STICK, 1 * amount);
            }
            case TORCH -> {
                recipe.put(Material.COAL, ops4);
                recipe.put(Material.STICK, ops4);
            }
            case WOODEN_PICKAXE -> {
                recipe.put(Material.OAK_PLANKS, 3 * amount);
                recipe.put(Material.STICK, 2 * amount);
            }
            case WOODEN_AXE -> {
                recipe.put(Material.OAK_PLANKS, 3 * amount);
                recipe.put(Material.STICK, 2 * amount);
            }
            case WOODEN_SHOVEL -> {
                recipe.put(Material.OAK_PLANKS, 1 * amount);
                recipe.put(Material.STICK, 2 * amount);
            }
            case WOODEN_SWORD -> {
                recipe.put(Material.OAK_PLANKS, 2 * amount);
                recipe.put(Material.STICK, 1 * amount);
            }
            case CHEST -> recipe.put(Material.OAK_PLANKS, 8 * amount);
            default -> {
                return null;
            }
        }

        return recipe;
    }

    private Map<Material, Integer> recipeFromServer(Material output, int amount) {
        try {
            Iterator<Recipe> iterator = Bukkit.recipeIterator();
            while (iterator.hasNext()) {
                Recipe recipe = iterator.next();
                ItemStack result = recipe == null ? null : recipe.getResult();
                if (result == null || result.getType() != output) {
                    continue;
                }

                Map<Material, Integer> single = extractIngredients(recipe);
                if (single == null || single.isEmpty()) {
                    continue;
                }

                int recipeYield = Math.max(1, result.getAmount());
                int operations = (int) Math.ceil((double) Math.max(1, amount) / recipeYield);

                Map<Material, Integer> scaled = new LinkedHashMap<>();
                for (Map.Entry<Material, Integer> entry : single.entrySet()) {
                    scaled.put(entry.getKey(), entry.getValue() * operations);
                }
                return scaled;
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private Map<Material, Integer> extractIngredients(Recipe recipe) {
        Map<Material, Integer> ingredients = new LinkedHashMap<>();

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            try {
                for (RecipeChoice choice : shapedRecipe.getChoiceMap().values()) {
                    Material material = firstMaterialFromChoice(choice);
                    if (material != null && material != Material.AIR) {
                        ingredients.merge(material, 1, Integer::sum);
                    }
                }
                if (!ingredients.isEmpty()) {
                    return ingredients;
                }
            } catch (Exception ignore) { }

            try {
                for (ItemStack itemStack : shapedRecipe.getIngredientMap().values()) {
                    if (itemStack != null && itemStack.getType() != Material.AIR) {
                        ingredients.merge(itemStack.getType(), 1, Integer::sum);
                    }
                }
                if (!ingredients.isEmpty()) {
                    return ingredients;
                }
            } catch (Exception ignore) { }
        }

        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            try {
                for (RecipeChoice choice : shapelessRecipe.getChoiceList()) {
                    Material material = firstMaterialFromChoice(choice);
                    if (material != null && material != Material.AIR) {
                        ingredients.merge(material, 1, Integer::sum);
                    }
                }
                if (!ingredients.isEmpty()) {
                    return ingredients;
                }
            } catch (Exception ignore) { }

            try {
                for (ItemStack itemStack : shapelessRecipe.getIngredientList()) {
                    if (itemStack != null && itemStack.getType() != Material.AIR) {
                        ingredients.merge(itemStack.getType(), 1, Integer::sum);
                    }
                }
                if (!ingredients.isEmpty()) {
                    return ingredients;
                }
            } catch (Exception ignore) { }
        }

        return ingredients;
    }

    private Material firstMaterialFromChoice(RecipeChoice choice) {
        if (choice == null) {
            return null;
        }

        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            List<Material> choices = materialChoice.getChoices();
            if (choices != null && !choices.isEmpty() && choices.get(0) != null) {
                return choices.get(0);
            }
        }

        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            List<ItemStack> choices = exactChoice.getChoices();
            if (choices != null && !choices.isEmpty() && choices.get(0) != null) {
                return choices.get(0).getType();
            }
        }

        return null;
    }
}
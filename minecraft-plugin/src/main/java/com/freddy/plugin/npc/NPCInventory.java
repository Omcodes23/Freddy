package com.freddy.plugin.npc;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/**
 * Manages NPC inventory
 */
public class NPCInventory {
    private Map<Material, Integer> items = new HashMap<>();
    private static final int MAX_SLOTS = 36;
    
    /**
     * Add item to inventory
     */
    public boolean addItem(ItemStack stack) {
        Material type = stack.getType();
        int amount = stack.getAmount();
        
        int current = items.getOrDefault(type, 0);
        items.put(type, current + amount);
        return true;
    }
    
    /**
     * Remove item from inventory
     */
    public boolean removeItem(Material material) {
        if (items.containsKey(material) && items.get(material) > 0) {
            int count = items.get(material);
            items.put(material, count - 1);
            if (count - 1 <= 0) {
                items.remove(material);
            }
            return true;
        }
        return false;
    }
    
    /**
     * Check if NPC has item
     */
    public boolean hasItem(Material material) {
        return items.getOrDefault(material, 0) > 0;
    }
    
    /**
     * Get item count
     */
    public int getCount(Material material) {
        return items.getOrDefault(material, 0);
    }
    
    /**
     * Find any food item
     */
    public ItemStack findFood() {
        Material[] foods = {
            Material.APPLE, Material.BREAD, Material.COOKED_BEEF, 
            Material.COOKED_CHICKEN, Material.COOKED_PORKCHOP
        };
        
        for (Material food : foods) {
            if (hasItem(food)) {
                return new ItemStack(food);
            }
        }
        return null;
    }
    
    /**
     * Get all items in inventory
     */
    public Map<Material, Integer> getItems() {
        return new HashMap<>(items);
    }
    
    /**
     * Clear inventory
     */
    public void clear() {
        items.clear();
    }
}

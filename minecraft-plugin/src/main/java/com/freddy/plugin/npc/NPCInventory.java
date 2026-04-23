package com.freddy.plugin.npc;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/**
 * Manages NPC inventory with capacity limits
 */
public class NPCInventory {
    private Map<Material, Integer> items = new HashMap<>();
    private static final int MAX_STACK_SIZE = 64;
    private static final int MAX_UNIQUE_ITEMS = 36;
    
    /**
     * Add item to inventory (respects capacity)
     */
    public boolean addItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        Material type = stack.getType();
        int amount = stack.getAmount();
        
        int current = items.getOrDefault(type, 0);
        
        // Enforce capacity: max 36 unique item types, max stack size per type
        if (!items.containsKey(type) && items.size() >= MAX_UNIQUE_ITEMS) {
            return false;
        }
        
        int newAmount = Math.min(current + amount, MAX_STACK_SIZE * 9); // Allow up to 9 stacks per type
        items.put(type, newAmount);
        return true;
    }
    
    /**
     * Remove specific amount from inventory
     */
    public boolean removeItem(Material material, int amount) {
        if (material == null || amount <= 0) return false;
        int current = items.getOrDefault(material, 0);
        if (current <= 0) return false;
        
        int toRemove = Math.min(amount, current);
        int remaining = current - toRemove;
        if (remaining <= 0) {
            items.remove(material);
        } else {
            items.put(material, remaining);
        }
        return true;
    }
    
    /**
     * Remove single item from inventory
     */
    public boolean removeItem(Material material) {
        return removeItem(material, 1);
    }
    
    /**
     * Check if NPC has item
     */
    public boolean hasItem(Material material) {
        return items.getOrDefault(material, 0) > 0;
    }
    
    /**
     * Check if NPC has at least a specific amount
     */
    public boolean hasItem(Material material, int amount) {
        return items.getOrDefault(material, 0) >= amount;
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
            Material.COOKED_BEEF, Material.COOKED_PORKCHOP,
            Material.COOKED_CHICKEN, Material.COOKED_MUTTON,
            Material.BREAD, Material.APPLE,
            Material.BEEF, Material.PORKCHOP,
            Material.CHICKEN, Material.MUTTON
        };
        
        for (Material food : foods) {
            if (hasItem(food)) {
                return new ItemStack(food);
            }
        }
        return null;
    }
    
    /**
     * Get total item count across all types
     */
    public int getTotalItemCount() {
        int total = 0;
        for (int count : items.values()) {
            total += count;
        }
        return total;
    }
    
    /**
     * Get all items in inventory
     */
    public Map<Material, Integer> getItems() {
        return new HashMap<>(items);
    }

    /**
     * Get a summary of notable items for LLM context
     */
    public List<String> getSummary() {
        List<String> summary = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                summary.add(entry.getKey().name() + "x" + entry.getValue());
            }
        }
        return summary;
    }
    
    /**
     * Clear inventory
     */
    public void clear() {
        items.clear();
    }
}

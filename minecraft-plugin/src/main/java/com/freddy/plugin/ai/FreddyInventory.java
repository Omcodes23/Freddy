package com.freddy.plugin.ai;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * FreddyInventory - Virtual Inventory System
 * 
 * Manages Freddy's inventory independently of Bukkit's player inventory.
 * This allows inventory tracking without requiring an NPC entity.
 * 
 * Thread-safe for concurrent access from AI logic and commands.
 */
public class FreddyInventory {

    private final Map<Material, Integer> items = new HashMap<>();

    /**
     * Check if inventory has enough of a material
     * 
     * @param material Material to check
     * @param amount   Required amount
     * @return true if inventory contains at least the specified amount
     */
    public boolean has(Material material, int amount) {
        return items.getOrDefault(material, 0) >= amount;
    }

    /**
     * Add items to inventory
     * 
     * @param material Material to add
     * @param amount   Amount to add
     */
    public void add(Material material, int amount) {
        if (amount <= 0)
            return;
        items.merge(material, amount, Integer::sum);
    }

    /**
     * Remove items from inventory
     * 
     * @param material Material to remove
     * @param amount   Amount to remove
     * @return true if removal was successful, false if insufficient items
     */
    public boolean remove(Material material, int amount) {
        if (amount <= 0)
            return true;

        int current = items.getOrDefault(material, 0);
        if (current < amount) {
            return false; // Not enough items
        }

        int remaining = current - amount;
        if (remaining == 0) {
            items.remove(material);
        } else {
            items.put(material, remaining);
        }

        return true;
    }

    /**
     * Get all inventory contents
     * 
     * @return Map of materials to amounts (defensive copy)
     */
    public Map<Material, Integer> getAll() {
        return new HashMap<>(items);
    }

    /**
     * Get count of a specific material
     * 
     * @param material Material to count
     * @return Amount in inventory (0 if not present)
     */
    public int getCount(Material material) {
        return items.getOrDefault(material, 0);
    }

    /**
     * Set inventory contents (for testing/initialization)
     * 
     * @param newItems Map of materials to amounts
     */
    public void setItems(Map<Material, Integer> newItems) {
        items.clear();
        items.putAll(newItems);
    }

    /**
     * Clear all items from inventory
     */
    public void clear() {
        items.clear();
    }

    /**
     * Check if inventory is empty
     * 
     * @return true if no items in inventory
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Get total number of unique item types
     * 
     * @return Number of different materials in inventory
     */
    public int getUniqueItemCount() {
        return items.size();
    }

    /**
     * Alias for getCount() - used by action system
     * 
     * @param material Material to count
     * @return Amount in inventory (0 if not present)
     */
    public int count(Material material) {
        return getCount(material);
    }

    /**
     * Smelt materials (input → output conversion)
     * 
     * @param input    Input material to smelt
     * @param output   Output material produced
     * @param quantity How many to smelt
     * @return true if smelting succeeded
     */
    public boolean smelt(Material input, Material output, int quantity) {
        // Check if we have enough input
        if (!has(input, quantity)) {
            return false;
        }

        // Remove input
        if (!remove(input, quantity)) {
            return false;
        }

        // Add output
        add(output, quantity);

        return true;
    }
}

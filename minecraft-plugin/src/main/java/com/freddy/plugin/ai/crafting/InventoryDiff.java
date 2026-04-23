package com.freddy.plugin.ai.crafting;

import com.freddy.plugin.ai.FreddyInventory;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * InventoryDiff - Calculate missing materials
 * 
 * Compares required primitive materials against current inventory
 * to determine what needs to be gathered.
 * 
 * This is DETERMINISTIC - simple subtraction.
 */
public class InventoryDiff {

    /**
     * Calculate missing materials
     * 
     * @param required  Required materials (from PrimitiveResolver)
     * @param inventory Current inventory state
     * @return Map of missing materials (empty if all available)
     */
    public static Map<Material, Integer> missing(
            Map<Material, Integer> required,
            FreddyInventory inventory) {

        Map<Material, Integer> missing = new HashMap<>();

        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            Material material = entry.getKey();
            int needed = entry.getValue();
            int have = inventory.getCount(material);

            if (have < needed) {
                missing.put(material, needed - have);
            }
        }

        return missing;
    }

    /**
     * Check if all required materials are available
     * 
     * @param required  Required materials
     * @param inventory Current inventory
     * @return true if all materials available
     */
    public static boolean hasAll(
            Map<Material, Integer> required,
            FreddyInventory inventory) {

        return missing(required, inventory).isEmpty();
    }

    /**
     * Get human-readable missing items report
     * 
     * @param required  Required materials
     * @param inventory Current inventory
     * @return String description
     */
    public static String getReport(
            Map<Material, Integer> required,
            FreddyInventory inventory) {

        Map<Material, Integer> missing = missing(required, inventory);

        if (missing.isEmpty()) {
            return "All materials available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Missing materials:\n");

        for (Map.Entry<Material, Integer> entry : missing.entrySet()) {
            int have = inventory.getCount(entry.getKey());
            int need = required.get(entry.getKey());

            sb.append("  - ").append(entry.getKey().name())
                    .append(": need ").append(need)
                    .append(", have ").append(have)
                    .append(", missing ").append(entry.getValue())
                    .append("\n");
        }

        return sb.toString();
    }
}

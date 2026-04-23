package com.freddy.plugin.ai.crafting;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ConversionRegistry - Smelting and conversion mappings
 * 
 * Handles deterministic conversions that happen outside crafting table:
 * - Ore → Ingot (smelting)
 * - Ore → Drop (mining in 1.20+)
 * - Future: Log → Charcoal, etc.
 */
public class ConversionRegistry {

    /**
     * Ore → Ingot smelting mappings
     */
    public static final Map<Material, Material> ORE_TO_INGOT = Map.ofEntries(
            // Iron variants
            Map.entry(Material.IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT),

            // Gold variants
            Map.entry(Material.GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),

            // Copper variants
            Map.entry(Material.COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT));

    /**
     * Ore → Drop mappings (for verification after mining)
     * 
     * In Minecraft 1.20+, mining ORE blocks yields RAW items (not ORE blocks).
     * This mapping is critical for inventory verification.
     */
    public static final Map<Material, Material> ORE_TO_DROP = Map.ofEntries(
            // Iron ores → RAW_IRON
            Map.entry(Material.IRON_ORE, Material.RAW_IRON),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON),

            // Gold ores → RAW_GOLD (overworld/deepslate) or GOLD_NUGGET (nether)
            Map.entry(Material.GOLD_ORE, Material.RAW_GOLD),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD),
            Map.entry(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET),

            // Copper ores → RAW_COPPER
            Map.entry(Material.COPPER_ORE, Material.RAW_COPPER),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER),

            // Diamond ores → DIAMOND (direct)
            Map.entry(Material.DIAMOND_ORE, Material.DIAMOND),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND),

            // Coal ores → COAL
            Map.entry(Material.COAL_ORE, Material.COAL),
            Map.entry(Material.DEEPSLATE_COAL_ORE, Material.COAL),

            // Emerald ores → EMERALD
            Map.entry(Material.EMERALD_ORE, Material.EMERALD),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD),

            // Lapis ores → LAPIS_LAZULI
            Map.entry(Material.LAPIS_ORE, Material.LAPIS_LAZULI),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI),

            // Redstone ores → REDSTONE
            Map.entry(Material.REDSTONE_ORE, Material.REDSTONE),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE));

    /**
     * Reverse mapping: Ingot → Ore (for primitive resolution)
     * 
     * Note: Uses the "standard" overworld ore variant
     */
    public static final Map<Material, Material> INGOT_TO_ORE;
    static {
        Map<Material, Material> reverse = new HashMap<>();

        // Use standard overworld ores (not deepslate/nether variants)
        reverse.put(Material.IRON_INGOT, Material.IRON_ORE);
        reverse.put(Material.GOLD_INGOT, Material.GOLD_ORE);
        reverse.put(Material.COPPER_INGOT, Material.COPPER_ORE);

        INGOT_TO_ORE = Collections.unmodifiableMap(reverse);
    }

    /**
     * Reverse mapping: Direct drop → Ore (for primitive resolution)
     * 
     * For items that drop directly from ores without smelting.
     * Example: DIAMOND → DIAMOND_ORE
     */
    public static final Map<Material, Material> DROP_TO_ORE;
    static {
        Map<Material, Material> reverse = new HashMap<>();

        // Use standard overworld ores (not deepslate variants)
        reverse.put(Material.DIAMOND, Material.DIAMOND_ORE);
        reverse.put(Material.COAL, Material.COAL_ORE);
        reverse.put(Material.EMERALD, Material.EMERALD_ORE);
        reverse.put(Material.LAPIS_LAZULI, Material.LAPIS_ORE);
        reverse.put(Material.REDSTONE, Material.REDSTONE_ORE);

        DROP_TO_ORE = Collections.unmodifiableMap(reverse);
    }

    /**
     * Check if a material is smeltable (ore → ingot)
     */
    public static boolean isSmeltable(Material material) {
        return ORE_TO_INGOT.containsKey(material);
    }

    /**
     * Check if a material requires smelting (ingot that comes from ore)
     */
    public static boolean requiresSmelting(Material material) {
        return INGOT_TO_ORE.containsKey(material);
    }

    /**
     * Get the ore required to produce this ingot
     */
    public static Material getRequiredOre(Material ingot) {
        return INGOT_TO_ORE.get(ingot);
    }

    /**
     * Check if item drops directly from an ore (no smelting)
     */
    public static boolean isDirectDrop(Material material) {
        return DROP_TO_ORE.containsKey(material);
    }

    /**
     * Get the ore block for an item that drops directly
     */
    public static Material getOreForDrop(Material drop) {
        return DROP_TO_ORE.get(drop);
    }

    /**
     * Check if a material is an ore block (drops something when mined)
     */
    public static boolean isOre(Material material) {
        return ORE_TO_DROP.containsKey(material);
    }

    /**
     * Get the drop from mining this ore block
     */
    public static Material getOreDrop(Material ore) {
        return ORE_TO_DROP.get(ore);
    }
}

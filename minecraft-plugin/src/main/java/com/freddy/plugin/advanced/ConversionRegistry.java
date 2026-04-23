package com.freddy.plugin.advanced;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic conversion mappings for mining and smelting checks.
 */
public final class ConversionRegistry {
    private ConversionRegistry() {
    }

    private static final Map<Material, Material> ORE_TO_DROP = Map.ofEntries(
        Map.entry(Material.IRON_ORE, Material.RAW_IRON),
        Map.entry(Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON),
        Map.entry(Material.GOLD_ORE, Material.RAW_GOLD),
        Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD),
        Map.entry(Material.COPPER_ORE, Material.RAW_COPPER),
        Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER),
        Map.entry(Material.COAL_ORE, Material.COAL),
        Map.entry(Material.DEEPSLATE_COAL_ORE, Material.COAL),
        Map.entry(Material.DIAMOND_ORE, Material.DIAMOND),
        Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND),
        Map.entry(Material.EMERALD_ORE, Material.EMERALD),
        Map.entry(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD),
        Map.entry(Material.REDSTONE_ORE, Material.REDSTONE),
        Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE),
        Map.entry(Material.LAPIS_ORE, Material.LAPIS_LAZULI),
        Map.entry(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI)
    );

    private static final Map<Material, Material> INGOT_TO_ORE;

    static {
        Map<Material, Material> reverse = new HashMap<>();
        reverse.put(Material.IRON_INGOT, Material.IRON_ORE);
        reverse.put(Material.GOLD_INGOT, Material.GOLD_ORE);
        reverse.put(Material.COPPER_INGOT, Material.COPPER_ORE);
        INGOT_TO_ORE = Collections.unmodifiableMap(reverse);
    }

    public static boolean isOre(Material material) {
        return ORE_TO_DROP.containsKey(material);
    }

    public static Material dropForOre(Material ore) {
        return ORE_TO_DROP.getOrDefault(ore, ore);
    }

    public static boolean requiresSmelting(Material material) {
        return INGOT_TO_ORE.containsKey(material);
    }

    public static Material oreForIngot(Material ingot) {
        return INGOT_TO_ORE.get(ingot);
    }
}

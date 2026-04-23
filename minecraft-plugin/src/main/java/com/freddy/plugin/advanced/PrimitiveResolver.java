package com.freddy.plugin.advanced;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves requested outputs into primitive world-gather targets.
 */
public class PrimitiveResolver {

    public Map<Material, Integer> resolve(Material target, int amount) {
        Map<Material, Integer> output = new HashMap<>();
        expand(target, Math.max(1, amount), output);
        return output;
    }

    private void expand(Material material, int quantity, Map<Material, Integer> output) {
        if (ConversionRegistry.requiresSmelting(material)) {
            Material ore = ConversionRegistry.oreForIngot(material);
            if (ore != null) {
                output.merge(ore, quantity, Integer::sum);
                return;
            }
        }

        output.merge(material, quantity, Integer::sum);
    }
}

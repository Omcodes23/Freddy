package com.freddy.plugin.advanced;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Additive deterministic planner that can be used as a non-LLM fallback.
 */
public class DeterministicPlanner {
    private final PrimitiveResolver primitiveResolver = new PrimitiveResolver();

    public List<String> planGatherSequence(Material target, int amount) {
        Map<Material, Integer> primitives = primitiveResolver.resolve(target, amount);
        List<String> steps = new ArrayList<>();

        for (Map.Entry<Material, Integer> entry : primitives.entrySet()) {
            steps.add("Locate " + entry.getKey().name());
            steps.add("Mine " + entry.getValue() + " " + entry.getKey().name());

            if (ConversionRegistry.isOre(entry.getKey())) {
                Material drop = ConversionRegistry.dropForOre(entry.getKey());
                steps.add("Collect " + drop.name());
            }
        }

        steps.add("Verify target amount in inventory");
        return steps;
    }
}

package com.freddy.plugin.ai;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

public class FreddyCraftResult {
    public final boolean crafted;
    public final String craftedItem;
    public final int craftedAmount;
    public final Map<Material, Integer> missingItems;
    public final String message;

    public FreddyCraftResult(boolean crafted, String craftedItem, int craftedAmount, Map<Material, Integer> missingItems, String message) {
        this.crafted = crafted;
        this.craftedItem = craftedItem;
        this.craftedAmount = craftedAmount;
        this.missingItems = missingItems == null ? new LinkedHashMap<>() : new LinkedHashMap<>(missingItems);
        this.message = message;
    }
}
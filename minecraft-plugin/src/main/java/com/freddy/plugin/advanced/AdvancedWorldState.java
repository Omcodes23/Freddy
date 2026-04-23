package com.freddy.plugin.advanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot model intended for deterministic planning and optional LLM inputs.
 */
public class AdvancedWorldState {
    public double x;
    public double y;
    public double z;
    public String biome = "UNKNOWN";

    public boolean playerNearby;
    public String nearestPlayer;
    public double playerDistance;

    public boolean threatNearby;
    public List<String> nearbyHostileMobs = new ArrayList<>();

    public boolean lavaNearby;
    public boolean waterNearby;
    public boolean standingOnSolidBlock;

    public boolean furnaceAvailable;
    public boolean hasFuel;

    public Map<String, Integer> nearbyBlocks = new HashMap<>();

    public boolean querySatisfied;
    public String queryResultType;
    public double queryDistance;
    public double queryX;
    public double queryY;
    public double queryZ;

    public long tickTime;
}

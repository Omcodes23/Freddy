package com.freddy.plugin.npc;

import com.freddy.plugin.ai.FreddyCraftingService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

public class AIActionExecutor {
    private static final Logger logger = Logger.getLogger("FreddyAI");
    private final NPCController npcController;
    private final org.bukkit.entity.Entity npcEntity;
    
    // Resource search state
    private int woodSearchRadius = 32;
    private int stoneSearchRadius = 24;
    private int diamondSearchRadius = 30;
    private long lastWoodExploreAt = 0L;
    private long lastDiamondExploreAt = 0L;
    private int cropSearchRadius = 30;
    private int huntSearchRadius = 30;
    
    // Approach stall detection
    private String lastApproachTargetKey = "";
    private double lastApproachDistance = Double.MAX_VALUE;
    private int approachStallTicks = 0;
    
    // Progress tracking
    private long lastWoodProgressAt = 0L;
    private int lastObservedWoodCount = 0;
    private int woodStallTicks = 0;

    // Scaffolding guardrails
    private String lastScaffoldTargetKey = "";
    private long lastScaffoldAt = 0L;
    private int scaffoldAttemptsForTarget = 0;
    private int lastScaffoldNpcBlockY = Integer.MIN_VALUE;
    private int sameScaffoldHeightCount = 0;
    private static final long SCAFFOLD_MIN_INTERVAL_MS = 1200L;
    private static final int MAX_SCAFFOLD_ATTEMPTS_PER_TARGET = 6;
    
    private static final long WOOD_PROGRESS_TIMEOUT_MS = 30000L; // 30 seconds
    private static final long WOOD_EXPLORE_COOLDOWN_MS = 8000L; // 8 seconds
    private static final long DIAMOND_STRIP_EXPLORE_COOLDOWN_MS = 15000L; // 15 seconds
    private static final double BLOCK_REACH_DISTANCE = 4.5;

    // Build template state (one-block-at-a-time)
    private String activeBuildTemplate = null;
    private String activeBuildStepKey = null;
    private int buildProgress = 0;
    private Location buildOrigin = null;
    private List<int[]> activeBuildPlan = null;
    private int activeBuildPlanIndex = 0;

    public AIActionExecutor(NPCController npcController, org.bukkit.entity.Entity npcEntity) {
        this.npcController = npcController;
        this.npcEntity = npcEntity;
    }

    /**
     * Farm crops at location
     */
    public void farmCrops(int range) {
        if (npcEntity == null) return;
        
        Location baseLoc = npcEntity.getLocation();
        Block nearest = null;
        double closestDistance = range;
        
        // Find nearest farmland with mature crops
        Material[] crops = { 
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.MELON, Material.PUMPKIN
        };
        
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Location probe = baseLoc.clone().add(x, y, z);
                    Block block = probe.getBlock();
                    for (Material crop : crops) {
                        if (block.getType() == crop) {
                            double distance = baseLoc.distance(block.getLocation());
                            if (distance < closestDistance) {
                                nearest = block;
                                closestDistance = distance;
                            }
                        }
                    }
                }
            }
        }
        
        if (nearest != null) {
            logger.info("[AI] Harvesting crops at " + nearest.getLocation());
            npcController.walkTo(nearest.getX(), nearest.getY(), nearest.getZ());
            npcController.mineBlock(nearest.getX(), nearest.getY(), nearest.getZ());
            cropSearchRadius = 30; // reset
        } else {
            if (cropSearchRadius > 70) {
               NPCInventory inv = npcController.getInventory();
               if (inv != null) inv.addItem(new org.bukkit.inventory.ItemStack(Material.WHEAT, 8));
               cropSearchRadius = 30;
               return;
            }
            cropSearchRadius = Math.min(cropSearchRadius + 20, 128);
            logger.info("[AI] No crops found nearby; exploring to find farmland. Radius: " + cropSearchRadius);
            explore(Math.max(30, cropSearchRadius));
        }
    }
    
    /**
     * Hunt animals for food
     */
    public void huntAnimals(int range) {
        if (npcEntity == null) return;
        
        Location npcLoc = npcEntity.getLocation();
        LivingEntity nearest = null;
        double closestDistance = range;
        
        // Find nearest passive animal from all entities
        for (org.bukkit.entity.Entity entity : npcLoc.getWorld().getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity living = (LivingEntity) entity;
                String entityType = entity.getType().name();
                if (entityType.contains("COW") || entityType.contains("PIG") || 
                    entityType.contains("SHEEP") || entityType.contains("CHICKEN")) {
                    double distance = npcLoc.distance(entity.getLocation());
                    if (distance < closestDistance) {
                        nearest = living;
                        closestDistance = distance;
                    }
                }
            }
        }
        
        if (nearest != null) {
            logger.info("[AI] Hunting: " + nearest.getName());
            npcController.walkTo(nearest.getX(), nearest.getY(), nearest.getZ());
            npcController.attackEntity(nearest);
            huntSearchRadius = 30; // reset
        } else {
            huntSearchRadius = Math.min(huntSearchRadius + 20, 128);
            logger.info("[AI] No huntable animals found nearby; exploring. Radius: " + huntSearchRadius);
            explore(Math.max(30, huntSearchRadius));
        }
    }
    
    /**
     * Build a simple structure
     */
    public void buildPillar(int height) {
        if (npcEntity == null) return;
        
        Location base = npcEntity.getLocation();
        logger.info("[AI] Building pillar of height " + height);
        
        for (int i = 0; i < height; i++) {
            if (npcController.getInventory().hasItem(Material.OAK_LOG)) {
                npcController.placeBlock(
                    (int)base.getX(), 
                    (int)base.getY() + i, 
                    (int)base.getZ(), 
                    Material.OAK_LOG
                );
            }
        }
    }

    /**
     * Explore in all directions
     */
    public void explore(int distance) {
        if (npcEntity == null) return;
        
        Location current = npcEntity.getLocation();
        logger.info("[AI] Exploring in radius " + distance);
        
        // Simple exploration - walk to random nearby location
        double randomX = current.getX() + (Math.random() - 0.5) * distance;
        double randomZ = current.getZ() + (Math.random() - 0.5) * distance;
        
        npcController.walkTo(randomX, current.getY(), randomZ);
    }
    
    /**
     * Descend to diamond level with proper staircasing (2 down, 1 forward pattern)
     */
    public void descendTowardsDiamondLevel() {
        if (npcEntity == null) {
            return;
        }

        Location loc = npcEntity.getLocation();
        int currentY = loc.getBlockY();
        
        // If we're already at diamond level, stop descending
        if (currentY <= 16) {
            return;
        }
        
        // Get direction and calculate forward position
        org.bukkit.util.Vector direction = loc.getDirection().normalize();
        int dx = (int) Math.round(direction.getX());
        int dz = (int) Math.round(direction.getZ());
        
        // If no clear direction, default to +X
        if (dx == 0 && dz == 0) {
            dx = 1;
        }
        
        // Staircase pattern: to go down and forward 1 step, we need to clear space.
        // We will clear:
        // 1. The forward space at eye level
        // 2. The forward space at leg level
        // 3. The forward space ONE block below leg level to step into.
        Block forwardEye = loc.clone().add(dx, 1, dz).getBlock();
        Block forwardLegs = loc.clone().add(dx, 0, dz).getBlock();
        Block forwardStepDown = loc.clone().add(dx, -1, dz).getBlock();
        
        // Mine the staircase steps
        if (forwardEye.getType() != Material.BEDROCK && forwardEye.getType() != Material.LAVA && forwardEye.getType() != Material.WATER && forwardEye.getType() != Material.AIR) {
            npcController.mineBlock(forwardEye.getX(), forwardEye.getY(), forwardEye.getZ());
        }
        
        if (forwardLegs.getType() != Material.BEDROCK && forwardLegs.getType() != Material.LAVA && forwardLegs.getType() != Material.WATER && forwardLegs.getType() != Material.AIR) {
            npcController.mineBlock(forwardLegs.getX(), forwardLegs.getY(), forwardLegs.getZ());
        }
        
        if (forwardStepDown.getType() != Material.BEDROCK && forwardStepDown.getType() != Material.LAVA && forwardStepDown.getType() != Material.WATER && forwardStepDown.getType() != Material.AIR) {
            npcController.mineBlock(forwardStepDown.getX(), forwardStepDown.getY(), forwardStepDown.getZ());
        }
        
        // Walk forward and down into the new staircase spot
        Location target = loc.clone().add(dx, -1.0, dz);
        npcController.walkTo(target.getX(), Math.max(5, target.getY()), target.getZ());
        
        logger.info("[AI] Staircasing down: Y=" + currentY + " -> mining forward-down at (" + forwardStepDown.getX() + "," + forwardStepDown.getY() + "," + forwardStepDown.getZ() + ")");
    }

    public void gatherResource(String resourceType) {
        logger.info("[AI] 🎯 Gathering: " + resourceType);
        
        switch (resourceType.toUpperCase()) {
            case "WOOD":
            case "LOG":
                int currentWood = countWoodInventory();
                long woodNow = System.currentTimeMillis();
                if (currentWood > lastObservedWoodCount) {
                    lastObservedWoodCount = currentWood;
                    lastWoodProgressAt = woodNow;
                    woodStallTicks = 0;
                } else if (lastWoodProgressAt == 0L) {
                    lastWoodProgressAt = woodNow;
                } else if (woodNow - lastWoodProgressAt >= WOOD_PROGRESS_TIMEOUT_MS) {
                    // Hard recovery: no wood progress for too long -> reset local stuck state and force retarget.
                    npcController.clearActionQueue();
                    clearApproachStall();
                    woodSearchRadius = Math.min(woodSearchRadius + 20, 128);
                    explore(Math.max(36, woodSearchRadius));
                    lastWoodProgressAt = woodNow;
                    return;
                }

                Location wood = findNearbyWoodTarget(woodSearchRadius, 12); // Reduced vertical range
                if (wood != null) {
                    Block target = wood.getBlock();
                    double heightDiff = target.getY() - npcEntity.getLocation().getY();
                    
                    if (approachAndQueueMine(target, true)) {
                        woodStallTicks = 0;
                    } else {
                        woodStallTicks++;
                        if (woodStallTicks >= 8) {
                            // Recovery: abandon unreachable targets and force a new search.
                            npcController.clearActionQueue();
                            clearApproachStall();
                            woodSearchRadius = Math.min(woodSearchRadius + 20, 128);
                            explore(Math.max(36, woodSearchRadius));
                            woodStallTicks = 0;
                            lastWoodProgressAt = woodNow;
                            return;
                        }
                    }

                    // If too many stalls, expand search
                    if (woodStallTicks >= 5) {
                        woodSearchRadius = Math.min(woodSearchRadius + 20, 128);
                        logger.info("[AI] Expanding wood search radius to " + woodSearchRadius);
                        woodStallTicks = 0;
                    }
                    return;
                }
                
                // No wood found
                woodStallTicks++;
                long now = System.currentTimeMillis();
                if (now - lastWoodExploreAt >= WOOD_EXPLORE_COOLDOWN_MS) {
                    logger.warning("[AI] No reachable wood found, exploring to find trees...");
                    explore(Math.max(40, woodSearchRadius));
                    lastWoodExploreAt = now;
                    woodSearchRadius = Math.min(woodSearchRadius + 16, 128);
                }
                break;
                
            case "STONE":
                gatherStoneWithApproach();
                break;
                
            case "DIAMOND":
            case "DIAMONDS":
                gatherDiamondsWithStrategy();
                break;
                
            case "IRON":
                mineNearestBlock(Material.IRON_ORE, 20);
                break;
                
            case "COAL":
                mineNearestBlock(Material.COAL_ORE, 20);
                break;
                
            case "FOOD":
                huntAnimals(30);
                break;
                
            case "CROPS":
                farmCrops(20);
                break;
                
            default:
                explore(30);
        }
    }

    public boolean hasNearbyResource(String resourceType, int range) {
        Location target = findResourceLocation(resourceType, range);
        return target != null;
    }

    public boolean moveToNearestResource(String resourceType, int range) {
        if (npcEntity == null) {
            return false;
        }

        Location target = findResourceLocation(resourceType, range);
        if (target == null) {
            return false;
        }

        String key = resourceType == null ? "" : resourceType.trim().toUpperCase();
        if ((key.equals("STONE") || key.equals("COBBLESTONE") || key.equals("DIAMOND") || key.equals("DIAMONDS"))
            && queueMineTowards(target)) {
            return true;
        }

        if (key.equals("WOOD") || key.equals("LOG") || key.equals("STONE") || key.equals("COBBLESTONE")
            || key.equals("DIAMOND") || key.equals("DIAMONDS")) {
            Block targetBlock = target.getBlock();
            return approachAndQueueMine(targetBlock, true);
        }

        npcController.walkTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        return true;
    }

    private Location findResourceLocation(String resourceType, int range) {
        if (npcEntity == null || resourceType == null) {
            return null;
        }

        String key = resourceType.trim().toUpperCase();
        if (key.equals("WOOD") || key.equals("LOG")) {
            return findNearbyWoodTarget(range, 24);
        }

        if (key.equals("STONE") || key.equals("COBBLESTONE")) {
            return findNearbyBlockAny(new Material[] {Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE}, range, 18);
        }

        if (key.equals("DIAMOND") || key.equals("DIAMONDS")) {
            return findNearbyBlockAny(new Material[] {Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE}, range, 22);
        }

        if (key.contains("IRON")) {
            return findNearbyBlockAny(new Material[] {Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE}, range, 24);
        }

        if (key.contains("GOLD")) {
            return findNearbyBlockAny(new Material[] {Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE}, range, 24);
        }

        if (key.contains("COAL")) {
            return findNearbyBlockAny(new Material[] {Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE}, range, 24);
        }

        return null;
    }

    private void gatherStoneWithApproach() {
        if (npcEntity == null) return;

        Block stoneBlock = findNearestMineableBlockAny(new Material[] {Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE}, stoneSearchRadius, 18);
        Location stone = stoneBlock == null ? null : stoneBlock.getLocation();
        if (stone != null) {
            approachAndQueueMine(stone.getBlock(), true);
            stoneSearchRadius = 24;
            return;
        }

        // If no exposed stone is visible, dig down realistically to reach underground stone.
        Location base = npcEntity.getLocation();
        Block below = base.clone().add(0, -1, 0).getBlock();
        if (below.getType() == Material.STONE || below.getType() == Material.COBBLESTONE || below.getType() == Material.DEEPSLATE) {
            npcController.mineBlock(below.getX(), below.getY(), below.getZ());
            return;
        }

        stoneSearchRadius = Math.min(stoneSearchRadius + 12, 96);
        explore(Math.max(30, stoneSearchRadius));
    }

    private void gatherDiamondsWithStrategy() {
        if (npcEntity == null) return;

        if (npcController.hasPendingMineAction()) {
            return;
        }

        Location loc = npcEntity.getLocation();
        if (loc.getY() > 16) {
            descendTowardsDiamondLevel();
            return;
        }

        Block ore = findNearestMineableBlockAny(new Material[] {Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE}, diamondSearchRadius, 22);
        if (ore != null) {
            Location targetCenter = ore.getLocation().clone().add(0.5, 0.5, 0.5);
            double distance = loc.distance(targetCenter);

            logger.info("[AI] Found " + ore.getType() + " at (" + ore.getX() + ", " + ore.getY() + ", " + ore.getZ() + ") distance: " + String.format("%.1f", distance));

            if (distance <= BLOCK_REACH_DISTANCE) {
                npcController.mineBlock(ore.getX(), ore.getY(), ore.getZ());
                diamondSearchRadius = 30;
                return;
            }

            // If stone blocks are between Freddy and ore, tunnel through blocker first.
            Block blocker = findBlockingSolidOnLine(ore.getLocation());
            if (blocker != null && blocker.getType() != Material.BEDROCK && blocker.getType() != Material.OBSIDIAN) {
                logger.info("[AI] Tunneling blocker " + blocker.getType() + " at "
                    + blocker.getX() + "," + blocker.getY() + "," + blocker.getZ()
                    + " to reach diamond ore");
                if (!npcController.hasQueuedMineAt(blocker.getX(), blocker.getY(), blocker.getZ())) {
                    npcController.queueAction(new NPCAction.MineBlock(blocker.getX(), blocker.getY(), blocker.getZ()));
                }
                return;
            }

            // If direct line-of-sight blocker is not detected, still dig a short tunnel step
            // toward ore so Freddy can recover from pathfinder dead-ends underground.
            Block tunnelStep = findTunnelStepTowards(ore.getLocation());
            if (tunnelStep != null && tunnelStep.getType() != Material.BEDROCK && tunnelStep.getType() != Material.OBSIDIAN) {
                logger.info("[AI] Tunneling toward ore via " + tunnelStep.getType() + " at "
                    + tunnelStep.getX() + "," + tunnelStep.getY() + "," + tunnelStep.getZ());
                if (!npcController.hasQueuedMineAt(tunnelStep.getX(), tunnelStep.getY(), tunnelStep.getZ())) {
                    npcController.queueAction(new NPCAction.MineBlock(tunnelStep.getX(), tunnelStep.getY(), tunnelStep.getZ()));
                }
                return;
            }

            approachAndQueueMine(ore, true);
            diamondSearchRadius = 30;
            return;
        }

        // Strip-mine style fallback at diamond depth.
        long now = System.currentTimeMillis();
        if (now - lastDiamondExploreAt >= DIAMOND_STRIP_EXPLORE_COOLDOWN_MS) {
            Location forward = loc.clone().add(loc.getDirection().normalize().multiply(4));
            npcController.walkTo(forward.getX(), Math.max(11, loc.getY()), forward.getZ());
            mineNearestBlock(Material.DEEPSLATE, 5);
            diamondSearchRadius = Math.min(diamondSearchRadius + 10, 80);
            lastDiamondExploreAt = now;
        }
    }

    // Helper methods
    private int countWoodInventory() {
        if (npcController == null || npcController.getInventory() == null) {
            return 0;
        }
        
        int count = 0;
        for (Map.Entry<Material, Integer> entry : npcController.getInventory().getItems().entrySet()) {
            Material material = entry.getKey();
            if (material != null && (material.name().contains("LOG") || material.name().contains("PLANK"))) {
                count += entry.getValue();
            }
        }
        return count;
    }

    private boolean approachAndQueueMine(Block targetBlock, boolean allowScaffold) {
        if (npcEntity == null || targetBlock == null) {
            return false;
        }

        Location npcLoc = npcEntity.getLocation();
        Location center = targetBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        double distance = npcLoc.distance(center);
        double heightDiff = center.getY() - npcLoc.getY();
        String key = targetBlock.getX() + ":" + targetBlock.getY() + ":" + targetBlock.getZ();
        trackApproachStall(key, distance);

        // If we've already scaffolded too many times for this same target, abandon it.
        if (!key.equals(lastScaffoldTargetKey)) {
            lastScaffoldTargetKey = key;
            scaffoldAttemptsForTarget = 0;
            lastScaffoldAt = 0L;
        }

        // If block is too high and we can't scaffold, skip it
        if (heightDiff > 3.0 && allowScaffold) {
            if (!attemptScaffoldUp(key, center)) {
                logger.info("[AI] Block too high and no scaffold materials, skipping: " + key);
                npcController.addMineCooldown(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), 30000); // 30s cooldown
                clearApproachStall();
                return false;
            }
        }

        if (distance <= BLOCK_REACH_DISTANCE) {
            npcController.mineBlock(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
            // If we scaffolded or mined at height, attempt to return to a lower, walkable spot
            // so Freddy doesn't get stuck on top of the scaffold pillar.
            if (heightDiff > 1.5) {
                scheduleReturnToGround();
            }
            clearApproachStall();
            return true;
        }

        if (center.getY() < npcLoc.getY() - 1.2 && moveAlongMiningStaircase(center)) {
            return true;
        }

        if (allowScaffold && heightDiff > 1.5 && attemptScaffoldUp(key, center)) {
            return true;
        }

        if (approachStallTicks >= 6) {
            // Check if there is a block right above the target that we should clear to make space to step on it constraint
            Block aboveTarget = targetBlock.getRelative(org.bukkit.block.BlockFace.UP);
            if (aboveTarget.getType().isSolid()) {
                logger.info("[AI] Clearing top solid block to reach target: " + aboveTarget.getType());
                if (!npcController.hasQueuedMineAt(aboveTarget.getX(), aboveTarget.getY(), aboveTarget.getZ())) {
                    npcController.queueAction(new NPCAction.MineBlock(aboveTarget.getX(), aboveTarget.getY(), aboveTarget.getZ()));
                }
                clearApproachStall();
                return true;
            }

            // Too many stall ticks - skip this block and find another
            logger.info("[AI] Stalled approaching block, adding cooldown and skipping: " + key);
            npcController.addMineCooldown(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), 20000); // 20s cooldown
            clearApproachStall();
            return false;
        }

        npcController.walkToMiningApproach(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        if (!npcController.hasQueuedMineAt(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ())) {
            npcController.queueAction(new NPCAction.MineBlock(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ()));
        }
        return true;
    }

    private void trackApproachStall(String key, double distance) {
        if (!key.equals(lastApproachTargetKey)) {
            lastApproachTargetKey = key;
            lastApproachDistance = distance;
            approachStallTicks = 0;
            return;
        }

        if (distance < lastApproachDistance - 0.15) {
            approachStallTicks = 0;
        } else {
            approachStallTicks++;
        }
        lastApproachDistance = distance;
    }

    private void clearApproachStall() {
        lastApproachTargetKey = "";
        lastApproachDistance = Double.MAX_VALUE;
        approachStallTicks = 0;
        lastScaffoldTargetKey = "";
        scaffoldAttemptsForTarget = 0;
        lastScaffoldAt = 0L;
    }

    private boolean attemptScaffoldUp(String targetKey, Location target) {
        if (target == null) {
            return false;
        }

        if (!targetKey.equals(lastScaffoldTargetKey)) {
            lastScaffoldTargetKey = targetKey;
            scaffoldAttemptsForTarget = 0;
            lastScaffoldAt = 0L;
        }

        if (scaffoldAttemptsForTarget >= MAX_SCAFFOLD_ATTEMPTS_PER_TARGET) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (lastScaffoldAt > 0L && (now - lastScaffoldAt) < SCAFFOLD_MIN_INTERVAL_MS) {
            // Scaffolding placement is still in-flight (scheduled); don't spam.
            return true;
        }

        boolean placed = tryScaffoldUp(target);
        if (!placed) {
            return false;
        }

        scaffoldAttemptsForTarget++;
        lastScaffoldAt = now;
        return true;
    }

    private boolean queueMineTowards(Location target) {
        if (target == null || npcEntity == null) {
            return false;
        }

        Block targetBlock = target.getBlock();
        Block blocker = findBlockingSolidOnLine(target);
        if (blocker != null && blocker.getType() != Material.BEDROCK && blocker.getType() != Material.OBSIDIAN) {
            if (!npcController.hasQueuedMineAt(blocker.getX(), blocker.getY(), blocker.getZ())) {
                npcController.queueAction(new NPCAction.MineBlock(blocker.getX(), blocker.getY(), blocker.getZ()));
            }
            return true;
        }

        Block tunnelStep = findTunnelStepTowards(target);
        if (tunnelStep != null && tunnelStep.getType() != Material.BEDROCK && tunnelStep.getType() != Material.OBSIDIAN) {
            if (!npcController.hasQueuedMineAt(tunnelStep.getX(), tunnelStep.getY(), tunnelStep.getZ())) {
                npcController.queueAction(new NPCAction.MineBlock(tunnelStep.getX(), tunnelStep.getY(), tunnelStep.getZ()));
            }
            return true;
        }

        return false;
    }

    private Block findBlockingSolidOnLine(Location target) {
        if (npcEntity == null || target == null) {
            return null;
        }

        Location from = npcEntity.getLocation().clone().add(0, 1.6, 0); // Eye level
        Location to = target.clone().add(0.5, 0.5, 0.5);
        double distance = from.distance(to);
        int steps = (int) Math.ceil(distance / 0.5); // Check every 0.5 blocks

        for (int i = 0; i <= steps; i++) {
            double ratio = (double) i / steps;
            Location probe = from.clone().add(to.clone().subtract(from).multiply(ratio));
            Block block = probe.getBlock();
            if (block != null && block.getType().isSolid() && block.getType() != Material.BEDROCK 
                && block.getType() != Material.OBSIDIAN && block.getType() != Material.AIR) {
                return block;
            }
        }

        return null;
    }

    private Block findTunnelStepTowards(Location target) {
        if (npcEntity == null || target == null) {
            return null;
        }

        Location from = npcEntity.getLocation();
        Location to = target.clone().add(0, 1.0, 0); // Slightly above target
        double distance = from.distance(to);
        int steps = Math.min(3, (int) Math.ceil(distance / 2.0));

        int[] yOffsets = {0, -1, -2, -3};
        for (int yOffset : yOffsets) {
            for (int i = 0; i <= steps; i++) {
                double ratio = (double) i / steps;
                Location probe = from.clone().add(to.clone().subtract(from).multiply(ratio).add(0, yOffset, 0));
                Block block = probe.getBlock();
                if (block != null && block.getType().isSolid() && block.getType() != Material.BEDROCK 
                    && block.getType() != Material.OBSIDIAN && block.getType() != Material.AIR) {
                    return block;
                }
            }
        }

        return null;
    }

    private Block findNearestMineableBlockAny(Material[] blockTypes, int horizontalRange, int verticalRange) {
        if (npcEntity == null || blockTypes == null || blockTypes.length == 0) {
            return null;
        }

        Location baseLoc = npcEntity.getLocation();
        Block nearest = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    Block block = baseLoc.clone().add(x, y, z).getBlock();
                    if (!matchesAny(block.getType(), blockTypes)) {
                        continue;
                    }
                    if (npcController.isMineTargetCoolingDown(block.getX(), block.getY(), block.getZ())) {
                        continue;
                    }

                    double d = baseLoc.distance(block.getLocation().clone().add(0.5, 0.5, 0.5));
                    if (d < bestDistance) {
                        bestDistance = d;
                        nearest = block;
                    }
                }
            }
        }

        return nearest;
    }

    private Location findNearbyWoodTarget(int horizontalRange, int verticalRange) {
        if (npcEntity == null) {
            return null;
        }

        Material[] logs = new Material[] {
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG
        };

        Location base = npcEntity.getLocation();
        double baseY = base.getY();
        Block bestReachable = null;
        Block bestAny = null;
        double bestReachableDist = Double.MAX_VALUE;
        double bestAnyDist = Double.MAX_VALUE;

        // Find best wood - prioritize reachable ones (within 2.5 blocks height)
        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    Block scanned = base.clone().add(x, y, z).getBlock();
                    if (!matchesAny(scanned.getType(), logs)) {
                        continue;
                    }
                    if (npcController.isMineTargetCoolingDown(scanned.getX(), scanned.getY(), scanned.getZ())) {
                        continue;
                    }

                    double dist = base.distance(scanned.getLocation().clone().add(0.5, 0.5, 0.5));
                    double heightDiff = Math.abs(scanned.getY() - baseY);
                    
                    // Track best reachable block (within height range or can scaffold)
                    if (heightDiff <= 2.5 || (heightDiff <= 4 && hasScaffoldMaterials())) {
                        if (dist < bestReachableDist) {
                            bestReachableDist = dist;
                            bestReachable = scanned;
                        }
                    }
                    
                    // Track best any block as fallback
                    if (dist < bestAnyDist) {
                        bestAnyDist = dist;
                        bestAny = scanned;
                    }
                }
            }
        }

        // Return reachable block if found, otherwise return any (will try to scaffold)
        return bestReachable != null ? bestReachable.getLocation() : 
               (bestAny != null ? bestAny.getLocation() : null);
    }
    
    private boolean hasScaffoldMaterials() {
        // Dirt-only scaffolding policy. If dirt is missing, supply it (creative assist).
        if (npcController == null) {
            return false;
        }
        if (npcController.getInventory() != null && !npcController.getInventory().hasItem(Material.DIRT)) {
            boolean supplied = npcController.getInventory().addItem(new ItemStack(Material.DIRT, 64));
            logger.info("[AI] Supplying dirt for scaffolding (creative assist): " + (supplied ? "added 64" : "inventory full"));
        }
        // In creative assist mode, placement can still succeed even if inventory is full.
        return true;
    }

    private Location findNearbyBlockAny(Material[] blockTypes, int horizontalRange, int verticalRange) {
        if (blockTypes == null || blockTypes.length == 0) {
            return null;
        }

        Location nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Material material : blockTypes) {
            Location candidate = findNearbyBlock(material, horizontalRange, verticalRange);
            if (candidate == null) {
                continue;
            }
            double d = npcEntity.getLocation().distance(candidate.clone().add(0.5, 0.5, 0.5));
            if (d < bestDistance) {
                bestDistance = d;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private Location findNearbyBlock(Material blockType, int range) {
        return findNearbyBlock(blockType, range, range);
    }

    private Location findNearbyBlock(Material blockType, int horizontalRange, int verticalRange) {
        if (npcEntity == null) {
            return null;
        }
        
        Location baseLoc = npcEntity.getLocation();
        Block nearest = null;
        double closestDistance = Math.max(horizontalRange, verticalRange);
        
        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    Location probe = baseLoc.clone().add(x, y, z);
                    Block block = probe.getBlock();
                    if (block.getType() == blockType) {
                        double distance = baseLoc.distance(block.getLocation());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            nearest = block;
                        }
                    }
                }
            }
        }
        
        return nearest != null ? nearest.getLocation() : null;
    }

    private boolean matchesAny(Material type, Material[] candidates) {
        if (type == null || candidates == null) {
            return false;
        }
        for (Material candidate : candidates) {
            if (type == candidate) {
                return true;
            }
        }
        return false;
    }

    private void mineNearestBlock(Material blockType, int range) {
        Location targetLoc = findNearbyBlock(blockType, range);
        if (targetLoc != null) {
            Block target = targetLoc.getBlock();
            npcController.walkToMiningApproach(target.getX(), target.getY(), target.getZ());
            npcController.mineBlock(target.getX(), target.getY(), target.getZ());
        }
    }

    // Missing methods that AutonomousAIBehavior calls
    public void attackNearestMob(int range) {
        if (npcEntity == null) return;
        
        Location npcLoc = npcEntity.getLocation();
        LivingEntity nearest = null;
        double closestDistance = range;
        
        // Find nearest hostile mob
        for (org.bukkit.entity.Entity entity : npcLoc.getWorld().getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity living = (LivingEntity) entity;
                String entityType = entity.getType().name();
                if (entityType.contains("ZOMBIE") || entityType.contains("SKELETON") || 
                    entityType.contains("SPIDER") || entityType.contains("CREEPER")) {
                    double distance = npcLoc.distance(entity.getLocation());
                    if (distance < closestDistance) {
                        nearest = living;
                        closestDistance = distance;
                    }
                }
            }
        }
        
        if (nearest != null) {
            logger.info("[AI] Attacking: " + nearest.getName());
            npcController.walkTo(nearest.getX(), nearest.getY(), nearest.getZ());
            npcController.attackEntity(nearest);
        }
    }

    public void returnToSurface() {
        if (npcEntity == null) return;
        
        Location current = npcEntity.getLocation();
        if (current.getY() >= 60) {
            return; // Already at surface
        }
        
        // Mine up to surface
        for (int i = 0; i < 10; i++) {
            Block above = current.clone().add(0, i + 1, 0).getBlock();
            if (above != null && above.getType().isSolid() && above.getType() != Material.BEDROCK) {
                npcController.mineBlock(above.getX(), above.getY(), above.getZ());
                break;
            }
        }
        
        // Mine an escape shaft upward — mine multiple blocks per call 
        int blocksToMine = Math.min(5, (int)(60 - current.getY()));
        for (int i = 1; i <= blocksToMine; i++) {
            Block above = current.clone().add(0, i, 0).getBlock();
            if (above != null && above.getType().isSolid() && above.getType() != Material.BEDROCK) {
                npcController.mineBlock(above.getX(), above.getY(), above.getZ());
            }
            // Also clear the block at head height
            Block headAbove = current.clone().add(0, i + 1, 0).getBlock();
            if (headAbove != null && headAbove.getType().isSolid() && headAbove.getType() != Material.BEDROCK) {
                npcController.mineBlock(headAbove.getX(), headAbove.getY(), headAbove.getZ());
            }
        }
        
        // Walk up through the cleared shaft
        npcController.walkTo(current.getX(), Math.min(current.getY() + blocksToMine, 65), current.getZ());
    }

    /**
     * Legacy build entrypoint (kept for other callers). Builds the full template.
     */
    public void buildTemplate(String templateName) {
        buildTemplateStep(templateName, "build all");
    }

    /**
     * Build one block per call, aligned to the current BUILD_STRUCTURE step label.
     * Returns true when the requested phase for this step is complete.
     */
    public boolean buildTemplateStep(String templateName, String stepLabel) {
        if (npcEntity == null) {
            return false;
        }

        String template = templateName == null ? "PILLAR_SMALL" : templateName.trim().toUpperCase(java.util.Locale.ROOT);
        String phase = inferBuildPhase(template, stepLabel);
        String stepKey = template + ":" + phase;

        // Initialize build origin when starting a new template.
        if (!template.equals(activeBuildTemplate) || buildOrigin == null) {
            activeBuildTemplate = template;
            buildOrigin = npcEntity.getLocation().clone();
            buildOrigin.setY(Math.floor(buildOrigin.getY()));
            
            // Offset origin so Freddy is inside the house footprint, not stuck in a wall
            if (template.contains("HOUSE_6X6")) {
                buildOrigin.add(-2, 0, -2);
            } else if (template.contains("HUT_4X4")) {
                buildOrigin.add(-1, 0, -1);
            }

            buildProgress = 0;
            activeBuildStepKey = null;
            activeBuildPlan = null;
            activeBuildPlanIndex = 0;
            logger.info("[AI] Starting build template: " + template + " at " + buildOrigin);
        }

        // Phase switch: rebuild plan for this specific step.
        if (!stepKey.equals(activeBuildStepKey) || activeBuildPlan == null) {
            activeBuildStepKey = stepKey;
            activeBuildPlan = createBuildPlan(template, phase);
            activeBuildPlanIndex = 0;
        }

        if (activeBuildPlan == null || activeBuildPlan.isEmpty()) {
            // Nothing to place for this step (e.g., MARK/INSPECT style steps).
            return true;
        }

        Material buildMat = selectBuildMaterial();
        if (buildMat == null) {
            logger.warning("[AI] No building materials available");
            return false;
        }

        int ox = buildOrigin.getBlockX();
        int oy = buildOrigin.getBlockY();
        int oz = buildOrigin.getBlockZ();

        if (activeBuildPlanIndex < activeBuildPlan.size()) {
            int[] off = activeBuildPlan.get(activeBuildPlanIndex);
            npcController.placeBlock(ox + off[0], oy + off[1], oz + off[2], buildMat);
            activeBuildPlanIndex++;
            buildProgress++;
        }

        boolean done = activeBuildPlanIndex >= activeBuildPlan.size();
        if (done) {
            // If this step finishes the overall template, mark template complete.
            if (isFinalBuildPhase(template, phase)) {
                activeBuildTemplate = null;
                activeBuildStepKey = null;
                activeBuildPlan = null;
                activeBuildPlanIndex = 0;
                logger.info("[AI] Build template complete: " + template);
                scheduleReturnToGround();
            }
        }

        return done;
    }

    /**
     * Place furniture items inside the built house.
     * Uses the buildOrigin as reference.
     */
    public boolean placeFurnitureStep(String template, String stepLabel) {
        if (buildOrigin == null) {
            logger.warning("[AI] Cannot place furniture: buildOrigin is null");
            return true;
        }

        int ox = buildOrigin.getBlockX();
        int oy = buildOrigin.getBlockY();
        int oz = buildOrigin.getBlockZ();

        // Specific furniture placement offsets for HOUSE_6X6
        // Inside space is from (1,1,1) to (4,1,4)
        if (template.contains("HOUSE_6X6")) {
            logger.info("[AI] Placing furniture inside 6x6 house at origin " + ox + "," + oy + "," + oz);
            
            // Corner 1: Crafting Table
            npcController.placeBlock(ox + 1, oy + 1, oz + 1, Material.CRAFTING_TABLE);
            
            // Corner 2: Furnace
            npcController.placeBlock(ox + 4, oy + 1, oz + 1, Material.FURNACE);
            
            // Corner 3: Chest
            npcController.placeBlock(ox + 1, oy + 1, oz + 4, Material.CHEST);
            
            // Corner 4: White Bed (2 blocks for accurate placement)
            npcController.placeBlock(ox + 4, oy + 1, oz + 3, Material.WHITE_BED);
            npcController.placeBlock(ox + 4, oy + 1, oz + 4, Material.WHITE_BED);

            // Add a Torch for light
            npcController.placeBlock(ox + 2, oy + 2, oz + 1, Material.TORCH);
            
            // Final adjustment: move Freddy to the middle of the room
            npcController.walkTo(ox + 3.0, oy + 1.0, oz + 3.0);
        } else {
            // Fallback for HUT_4X4 or others
            npcController.placeBlock(ox + 1, oy + 1, oz + 1, Material.CRAFTING_TABLE);
            npcController.placeBlock(ox + 2, oy + 1, oz + 1, Material.FURNACE);
            npcController.placeBlock(ox + 1, oy + 1, oz + 2, Material.TORCH);
            npcController.walkTo(ox + 2.0, oy + 1.0, oz + 2.0);
        }

        return true;
    }

    /**
     * Force Freddy to exit the house structure through the door.
     * Returns true only when Freddy is safely outside.
     */
    public boolean exitHouseStep() {
        if (buildOrigin == null) return true;
        
        Location cur = npcEntity.getLocation();
        // Target outside point (relative to 6x6 house)
        // Door is at x=2..3, z=0. Outside is z=-3.
        Location targetOutside = buildOrigin.clone().add(2.5, 0, -3.5);
        
        double distToOutside = cur.distance(targetOutside);
        if (distToOutside < 2.0) {
            logger.info("[AI] Freddy is now strictly outside the house.");
            return true;
        }
        
        // Still inside. Force jump and walk.
        if (npcController != null) {
            // Small jump to clear door threshold
            if (cur.getY() < buildOrigin.getY() + 1.2) {
                npcEntity.setVelocity(new org.bukkit.util.Vector(0, 0.4, 0));
            }
            npcController.walkTo(targetOutside.getX(), targetOutside.getY(), targetOutside.getZ());
        }
        
        return false;
    }

    private String inferBuildPhase(String template, String stepLabel) {
        String s = stepLabel == null ? "" : stepLabel.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("mark") || s.contains("foundation") || s.contains("segment") || s.contains("center")) {
            return "MARK";
        }
        if (s.contains("inspect") || s.contains("patch") || s.contains("fill gap") || s.contains("gaps")) {
            return "INSPECT";
        }

        if (template.contains("WALL_10")) {
            if (s.contains("first") && s.contains("layer")) return "LAYER1";
            if (s.contains("second") && s.contains("layer")) return "LAYER2";
            if (s.contains("top") && s.contains("layer")) return "LAYER3";
        }

        // Check combined floor+walls BEFORE individual floor/wall checks.
        // Step labels like "Place the floor and walls" must build both phases.
        if ((s.contains("floor") && s.contains("wall")) || s.contains("floor and wall")) {
            return "FLOOR_WALLS";
        }

        if (s.contains("floor")) return "FLOOR";
        if (s.contains("wall")) return "WALLS";
        if (s.contains("roof")) return "ROOF";
        if (s.contains("fence")) return "FENCE";

        return "ALL";
    }

    private boolean isFinalBuildPhase(String template, String phase) {
        if (phase == null) {
            return false;
        }
        if (template.contains("HOUSE_6X6")) {
            return phase.equals("ROOF") || phase.equals("ALL");
        }
        if (template.contains("HUT_4X4")) {
            return phase.equals("ROOF") || phase.equals("ALL");
        }
        if (template.contains("WALL_10")) {
            return phase.equals("LAYER3") || phase.equals("ALL");
        }
        if (template.contains("BRIDGE_8")) {
            return phase.equals("FENCE") || phase.equals("ALL");
        }
        if (template.contains("TOWER_7")) {
            return phase.equals("ALL");
        }
        return phase.equals("ALL");
    }

    private List<int[]> createBuildPlan(String template, String phase) {
        java.util.ArrayList<int[]> plan = new java.util.ArrayList<>();

        String t = template == null ? "" : template.trim().toUpperCase(java.util.Locale.ROOT);
        String p = phase == null ? "ALL" : phase.trim().toUpperCase(java.util.Locale.ROOT);

        if (p.equals("MARK") || p.equals("INSPECT")) {
            return plan;
        }

        if (t.contains("HOUSE_6X6")) {
            if (p.equals("FLOOR") || p.equals("FLOOR_WALLS") || p.equals("ALL")) {
                for (int x = 0; x < 6; x++) {
                    for (int z = 0; z < 6; z++) {
                        plan.add(new int[] {x, 0, z});
                    }
                }
            }
            if (p.equals("WALLS") || p.equals("FLOOR_WALLS") || p.equals("ALL")) {
                for (int y = 1; y <= 4; y++) {
                    for (int x = 0; x < 6; x++) {
                        // Front wall (z=0) with 2-wide door opening at x=2..3 for y=1..2
                        if (!((x == 2 || x == 3) && (y == 1 || y == 2))) {
                            plan.add(new int[] {x, y, 0});
                        }
                        plan.add(new int[] {x, y, 5});
                    }
                    for (int z = 1; z < 5; z++) {
                        plan.add(new int[] {0, y, z});
                        plan.add(new int[] {5, y, z});
                    }
                }
            }
            if (p.equals("ROOF") || p.equals("ALL")) {
                for (int x = 0; x < 6; x++) {
                    for (int z = 0; z < 6; z++) {
                        plan.add(new int[] {x, 5, z});
                    }
                }
            }
            return plan;
        }

        if (t.contains("HUT_4X4")) {
            if (p.equals("FLOOR") || p.equals("FLOOR_WALLS") || p.equals("ALL")) {
                for (int x = 0; x < 4; x++) {
                    for (int z = 0; z < 4; z++) {
                        plan.add(new int[] {x, 0, z});
                    }
                }
            }
            if (p.equals("WALLS") || p.equals("ALL") || p.equals("FLOOR_WALLS")) {
                for (int y = 1; y <= 3; y++) {
                    for (int x = 0; x < 4; x++) {
                        // Front wall (z=0) with door opening at x=1 for y=1..2
                        if (!(x == 1 && (y == 1 || y == 2))) {
                            plan.add(new int[] {x, y, 0});
                        }
                        plan.add(new int[] {x, y, 3});
                    }
                    for (int z = 1; z < 3; z++) {
                        plan.add(new int[] {0, y, z});
                        plan.add(new int[] {3, y, z});
                    }
                }
            }
            if (p.equals("ROOF") || p.equals("ALL")) {
                for (int x = 0; x < 4; x++) {
                    for (int z = 0; z < 4; z++) {
                        plan.add(new int[] {x, 4, z});
                    }
                }
            }
            return plan;
        }

        if (t.contains("WALL_10")) {
            int yStart = 1;
            int yEnd = 3;
            if (p.equals("LAYER1")) {
                yStart = 1;
                yEnd = 1;
            } else if (p.equals("LAYER2")) {
                yStart = 2;
                yEnd = 2;
            } else if (p.equals("LAYER3")) {
                yStart = 3;
                yEnd = 3;
            }
            for (int x = 0; x < 10; x++) {
                for (int y = yStart; y <= yEnd; y++) {
                    plan.add(new int[] {x, y, 0});
                }
            }
            return plan;
        }

        if (t.contains("TOWER_7")) {
            for (int y = 1; y <= 7; y++) {
                plan.add(new int[] {0, y, 0});
                plan.add(new int[] {1, y, 0});
                plan.add(new int[] {0, y, 1});
                plan.add(new int[] {1, y, 1});
            }
            return plan;
        }

        if (t.contains("FARM_PLOT_5X5")) {
            if (p.equals("FLOOR") || p.equals("ALL") || p.equals("MARK")) {
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 5; z++) {
                        plan.add(new int[] {x, 0, z});
                    }
                }
            }
            return plan;
        }

        // Default: small pillar
        for (int y = 1; y <= 5; y++) {
            plan.add(new int[] {0, y, 0});
        }
        return plan;
    }

    private void buildHouse6x6(int ox, int oy, int oz, Material mat) {
        int phase = buildProgress % 4;
        switch (phase) {
            case 0 -> {
                // Floor
                for (int x = 0; x < 6; x++)
                    for (int z = 0; z < 6; z++)
                        npcController.placeBlock(ox + x, oy, oz + z, mat);
            }
            case 1 -> {
                // Walls layer (4 high, skip door)
                for (int y = 1; y <= 4; y++) {
                    for (int x = 0; x < 6; x++) {
                        npcController.placeBlock(ox + x, oy + y, oz, mat);     // front wall
                        npcController.placeBlock(ox + x, oy + y, oz + 5, mat); // back wall
                    }
                    for (int z = 1; z < 5; z++) {
                        npcController.placeBlock(ox, oy + y, oz + z, mat);     // left wall
                        npcController.placeBlock(ox + 5, oy + y, oz + z, mat); // right wall
                    }
                }
                // Door opening (remove blocks at front center)
                npcController.placeBlock(ox + 2, oy + 1, oz, Material.AIR);
                npcController.placeBlock(ox + 2, oy + 2, oz, Material.AIR);
                npcController.placeBlock(ox + 3, oy + 1, oz, Material.AIR);
                npcController.placeBlock(ox + 3, oy + 2, oz, Material.AIR);
            }
            case 2 -> {
                // Roof
                for (int x = 0; x < 6; x++)
                    for (int z = 0; z < 6; z++)
                        npcController.placeBlock(ox + x, oy + 5, oz + z, mat);
            }
            case 3 -> {
                activeBuildTemplate = null;
                logger.info("[AI] House 6x6 build complete");
            }
        }
    }

    private void buildWall10(int ox, int oy, int oz, Material mat) {
        int phase = buildProgress % 2;
        if (phase == 0) {
            for (int x = 0; x < 10; x++)
                for (int y = 1; y <= 3; y++)
                    npcController.placeBlock(ox + x, oy + y, oz, mat);
        } else {
            activeBuildTemplate = null;
            logger.info("[AI] Wall build complete");
        }
    }

    private void buildTower7(int ox, int oy, int oz, Material mat) {
        int phase = buildProgress % 2;
        if (phase == 0) {
            for (int y = 1; y <= 7; y++) {
                npcController.placeBlock(ox, oy + y, oz, mat);
                npcController.placeBlock(ox + 1, oy + y, oz, mat);
                npcController.placeBlock(ox, oy + y, oz + 1, mat);
                npcController.placeBlock(ox + 1, oy + y, oz + 1, mat);
            }
        } else {
            activeBuildTemplate = null;
            logger.info("[AI] Tower build complete");
        }
    }

    private void buildFarmPlot5x5(int ox, int oy, int oz, Material mat) {
        int phase = buildProgress % 2;
        if (phase == 0) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    npcController.placeBlock(ox + x, oy, oz + z, mat);
                }
            }
        } else {
            activeBuildTemplate = null;
            logger.info("[AI] Farm plot build complete");
        }
    }

    private void buildHut4x4(int ox, int oy, int oz, Material mat) {
        int phase = buildProgress % 3;
        switch (phase) {
            case 0 -> {
                for (int x = 0; x < 4; x++)
                    for (int z = 0; z < 4; z++)
                        npcController.placeBlock(ox + x, oy, oz + z, mat);
            }
            case 1 -> {
                for (int y = 1; y <= 3; y++) {
                    for (int x = 0; x < 4; x++) {
                        npcController.placeBlock(ox + x, oy + y, oz, mat);
                        npcController.placeBlock(ox + x, oy + y, oz + 3, mat);
                    }
                    for (int z = 1; z < 3; z++) {
                        npcController.placeBlock(ox, oy + y, oz + z, mat);
                        npcController.placeBlock(ox + 3, oy + y, oz + z, mat);
                    }
                }
                // Door
                npcController.placeBlock(ox + 1, oy + 1, oz, Material.AIR);
                npcController.placeBlock(ox + 1, oy + 2, oz, Material.AIR);
            }
            case 2 -> {
                for (int x = 0; x < 4; x++)
                    for (int z = 0; z < 4; z++)
                        npcController.placeBlock(ox + x, oy + 4, oz + z, mat);
                activeBuildTemplate = null;
                logger.info("[AI] Hut 4x4 build complete");
            }
        }
    }

    private Material selectBuildMaterial() {
        NPCInventory inv = npcController.getInventory();
        if (inv.hasItem(Material.OAK_PLANKS)) return Material.OAK_PLANKS;
        if (inv.hasItem(Material.COBBLESTONE)) return Material.COBBLESTONE;
        if (inv.hasItem(Material.OAK_LOG)) return Material.OAK_LOG;
        if (inv.hasItem(Material.STONE)) return Material.STONE;
        if (inv.hasItem(Material.DIRT)) return Material.DIRT;
        return null;
    }

    public boolean isBuildTemplateComplete() {
        return activeBuildTemplate == null;
    }

    // Additional missing helper methods
    private boolean moveAlongMiningStaircase(Location target) {
        if (npcEntity == null || target == null) {
            return false;
        }
        
        Location current = npcEntity.getLocation();
        if (target.getY() < current.getY() - 1.2) {
            // Mine down in staircase pattern
            Block below = current.clone().add(0, -1, 0).getBlock();
            if (below.getType() != Material.BEDROCK && below.getType() != Material.LAVA && below.getType() != Material.WATER && below.getType() != Material.AIR) {
                npcController.mineBlock(below.getX(), below.getY(), below.getZ());
            }
            
            // Move down explicitly instead of digging a 2-block hole
            Location drop = current.clone();
            drop.setY(current.getY() - 1);
            npcController.walkTo(drop.getX(), drop.getY(), drop.getZ());
            return true;
        }
        
        return false;
    }
    
    private boolean tryScaffoldUp(Location target) {
        if (npcEntity == null || target == null) {
            return false;
        }
        
        // Scaffolding - place blocks at NPC feet to build upward to target
        Location current = npcEntity.getLocation();
        double heightDiff = target.getY() - current.getY();
        
        if (heightDiff > 1.5) {
            // Dirt-only scaffolding policy. Do not mine/convert other blocks.
            // If dirt is missing, supply it directly (creative assist / command-style injection).
            if (npcController.getInventory() != null && !npcController.getInventory().hasItem(Material.DIRT)) {
                boolean supplied = npcController.getInventory().addItem(new ItemStack(Material.DIRT, 64));
                logger.info("[AI] Supplying dirt for scaffolding (creative assist): " + (supplied ? "added 64" : "inventory full"));
            }

            int currentY = current.getBlockY();
            if (currentY <= lastScaffoldNpcBlockY) {
                sameScaffoldHeightCount++;
            } else {
                sameScaffoldHeightCount = 0;
                lastScaffoldNpcBlockY = currentY;
            }

            // If we're repeatedly scaffolding at the same height, shift slightly to avoid
            // placing into an obstructed column (e.g., leaves/branches). 
            Location placeFrom = current;
            if (sameScaffoldHeightCount >= 2) {
                placeFrom = current.clone().add(1.0, 0.0, 0.0);
                npcController.walkTo(placeFrom.getBlockX() + 0.5, placeFrom.getY(), placeFrom.getBlockZ() + 0.5);
            }

            Block feetBlock = placeFrom.getBlock();

            // Jump to clear bounding box before placement.
            npcEntity.setVelocity(new org.bukkit.util.Vector(0, 0.65, 0));

            org.bukkit.plugin.Plugin plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(com.freddy.plugin.FreddyPlugin.class);

            // Place the block after the jump clears the old feet cell.
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> npcController.placeBlock(feetBlock.getX(), feetBlock.getY(), feetBlock.getZ(), Material.DIRT),
                6L);

            // Force a step-up so Freddy actually gains height instead of jumping in place.
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> npcController.walkTo(feetBlock.getX() + 0.5, feetBlock.getY() + 1.15, feetBlock.getZ() + 0.5),
                10L);

            logger.info("[AI] Scaffolding up with DIRT to reach height " + target.getY());
            return true;
        }
        
        return false;
    }

    private void scheduleReturnToGround() {
        if (npcEntity == null) {
            return;
        }
        org.bukkit.plugin.Plugin plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(com.freddy.plugin.FreddyPlugin.class);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (npcEntity == null || npcController == null) {
                    return;
                }
                if (npcController.hasPendingMineAction()) {
                    return;
                }
                Location cur = npcEntity.getLocation();
                org.bukkit.block.Block below = cur.clone().add(0, -1, 0).getBlock();
                if (below.getType() == Material.DIRT) {
                    logger.info("[AI] Destroying scaffolding block beneath feet.");
                    npcController.mineBlock(below.getX(), below.getY(), below.getZ());
                    return;
                }
                Location target = findNearbyDescentTarget(3, 14);
                if (target != null) {
                    npcController.walkTo(target.getX(), target.getY(), target.getZ());
                }
            } catch (Exception ignore) {
            }
        }, 40L);
    }

    private Location findNearbyDescentTarget(int radius, int maxDrop) {
        if (npcEntity == null) {
            return null;
        }
        World world = npcEntity.getWorld();
        if (world == null) {
            return null;
        }

        Location cur = npcEntity.getLocation();
        int baseX = cur.getBlockX();
        int baseZ = cur.getBlockZ();
        int startY = cur.getBlockY();

        Location best = null;
        int bestY = Integer.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = baseX + dx;
                int z = baseZ + dz;
                for (int dy = 1; dy <= maxDrop; dy++) {
                    int y = startY - dy;
                    if (y <= world.getMinHeight() + 1) {
                        break;
                    }
                    Block ground = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);
                    Block head2 = world.getBlockAt(x, y + 2, z);
                    if (ground.getType().isSolid() && head.getType() == Material.AIR && head2.getType() == Material.AIR) {
                        if (y < bestY) {
                            bestY = y;
                            best = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                        }
                        break;
                    }
                }
            }
        }

        return best;
    }

    public void resetTransientState() {
        woodSearchRadius = 32;
        stoneSearchRadius = 24;
        diamondSearchRadius = 30;
        lastWoodExploreAt = 0L;
        lastDiamondExploreAt = 0L;
        clearApproachStall();
    }
}

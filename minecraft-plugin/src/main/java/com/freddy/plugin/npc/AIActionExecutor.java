package com.freddy.plugin.npc;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.Location;

import java.util.logging.Logger;

/**
 * Autonomous AI Action Executor - performs actions based on LLM decisions
 */
public class AIActionExecutor {
    private static final Logger logger = Logger.getLogger("AI Executor");
    
    private NPCController npcController;
    private Player npcEntity;
    
    public AIActionExecutor(NPCController controller, Player npcEntity) {
        this.npcController = controller;
        this.npcEntity = npcEntity;
    }

    public void setNPCEntity(Player npcEntity) {
        this.npcEntity = npcEntity;
    }
    
    /**
     * Mine nearest ore/block of specific type
     */
    public void mineNearestBlock(Material blockType, int range) {
        if (npcEntity == null) return;
        
        Location baseLoc = npcEntity.getLocation();
        Block nearest = null;
        double closestDistance = range;
        
        // Search for blocks within range
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Location probe = baseLoc.clone().add(x, y, z);
                    Block block = probe.getBlock();
                    if (block.getType() == blockType) {
                        double distance = baseLoc.distance(block.getLocation());
                        if (distance < closestDistance) {
                            nearest = block;
                            closestDistance = distance;
                        }
                    }
                }
            }
        }
        
        if (nearest != null) {
            Location targetLoc = nearest.getLocation();
            double distanceToTarget = baseLoc.distance(targetLoc);
            
            logger.info("[AI] Found " + blockType + " at (" + (int)targetLoc.getX() + ", " + (int)targetLoc.getY() + ", " + (int)targetLoc.getZ() + ") distance: " + String.format("%.1f", distanceToTarget));
            
            // If close enough (within 5 blocks), mine it directly
            if (distanceToTarget <= 1.5) {
                logger.info("[AI] Mining " + blockType + " at location");
                npcController.mineBlock((int)targetLoc.getX(), (int)targetLoc.getY(), (int)targetLoc.getZ());
                
                // Send telemetry about the action
                try {
                    com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                    if (t != null) t.send("ACTION:MINING " + blockType + " at " + (int)targetLoc.getX() + "," + (int)targetLoc.getY() + "," + (int)targetLoc.getZ());
                } catch (Exception ignore) { }
            } else {
                // Walk towards it first
                logger.info("[AI] Walking to " + blockType);
                npcController.walkTo(targetLoc.getX(), targetLoc.getY(), targetLoc.getZ());
                // Queue mining action to execute once movement progresses
                int tx = (int)targetLoc.getX();
                int ty = (int)targetLoc.getY();
                int tz = (int)targetLoc.getZ();
                if (!npcController.hasQueuedMineAt(tx, ty, tz)) {
                    npcController.queueAction(new NPCAction.MineBlock(tx, ty, tz));
                }
                
                try {
                    com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                    if (t != null) t.send("ACTION:WALKING_TO " + blockType + " at " + (int)targetLoc.getX() + "," + (int)targetLoc.getY() + "," + (int)targetLoc.getZ());
                } catch (Exception ignore) { }
            }
        } else {
            logger.warning("[AI] No " + blockType + " found within " + range + " blocks, exploring...");
            // Fallback: explore to discover new terrain/resources
            explore(Math.max(30, range));
            
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) t.send("ACTION:EXPLORING to find " + blockType);
            } catch (Exception ignore) { }
        }
    }
    
    /**
     * Attack nearest hostile mob
     */
    public void attackNearestMob(int range) {
        if (npcEntity == null) return;
        
        Location npcLoc = npcEntity.getLocation();
        LivingEntity nearest = null;
        double closestDistance = range;
        
        // Find nearest hostile from all entities
        for (org.bukkit.entity.Entity entity : npcLoc.getWorld().getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity living = (LivingEntity) entity;
                String entityType = entity.getType().name();
                if (entityType.contains("CREEPER") || entityType.contains("SKELETON") || 
                    entityType.contains("ZOMBIE") || entityType.contains("SPIDER")) {
                    double distance = npcLoc.distance(entity.getLocation());
                    if (distance < closestDistance) {
                        nearest = living;
                        closestDistance = distance;
                    }
                }
            }
        }
        
        if (nearest != null) {
            logger.info("[AI] Attacking hostile: " + nearest.getName());
            npcController.walkTo(nearest.getX(), nearest.getY(), nearest.getZ());
            npcController.attackEntity(nearest);
        }
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
     * Gather specific resource intelligently
     */
    public void gatherResource(String resourceType) {
        logger.info("[AI] 🎯 Gathering: " + resourceType);
        
        switch (resourceType.toUpperCase()) {
            case "WOOD":
            case "LOG":
                // Try all wood types
                Material[] woodTypes = {Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG};
                for (Material wood : woodTypes) {
                    if (findNearbyBlock(wood, 30) != null) {
                        mineNearestBlock(wood, 30);
                        return;
                    }
                }
                logger.warning("[AI] No wood found, exploring...");
                explore(40);
                break;
            case "STONE":
                mineNearestBlock(Material.STONE, 20);
                break;
            case "DIAMOND":
            case "DIAMONDS":
                mineNearestBlock(Material.DIAMOND_ORE, 30);
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
    
    /**
     * Find nearest block of specific type (returns location or null)
     */
    private Location findNearbyBlock(Material blockType, int range) {
        if (npcEntity == null) return null;
        
        Location baseLoc = npcEntity.getLocation();
        Block nearest = null;
        double closestDistance = range;
        
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Location probe = baseLoc.clone().add(x, y, z);
                    Block block = probe.getBlock();
                    if (block.getType() == blockType) {
                        double distance = baseLoc.distance(block.getLocation());
                        if (distance < closestDistance) {
                            nearest = block;
                            closestDistance = distance;
                        }
                    }
                }
            }
        }
        
        return nearest != null ? nearest.getLocation() : null;
    }
}

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
    
    /**
     * Mine nearest ore/block of specific type
     */
    public void mineNearestBlock(Material blockType, int range) {
        if (npcEntity == null) return;
        
        Location npcLoc = npcEntity.getLocation();
        Block nearest = null;
        double closestDistance = range;
        
        // Search for blocks within range
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = npcLoc.add(x, y, z).getBlock();
                    if (block.getType() == blockType) {
                        double distance = npcLoc.distance(block.getLocation());
                        if (distance < closestDistance) {
                            nearest = block;
                            closestDistance = distance;
                        }
                    }
                }
            }
        }
        
        if (nearest != null) {
            logger.info("[AI] Found " + blockType + " at " + nearest.getLocation());
            npcController.walkTo(nearest.getX(), nearest.getY(), nearest.getZ());
            // After walking, mine it
            npcController.mineBlock(nearest.getX(), nearest.getY(), nearest.getZ());
        } else {
            logger.warning("[AI] No " + blockType + " found within " + range + " blocks");
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
        
        Location npcLoc = npcEntity.getLocation();
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
                    Block block = npcLoc.add(x, y, z).getBlock();
                    for (Material crop : crops) {
                        if (block.getType() == crop) {
                            double distance = npcLoc.distance(block.getLocation());
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
        logger.info("[AI] Gathering: " + resourceType);
        
        switch (resourceType.toUpperCase()) {
            case "WOOD":
            case "LOG":
                mineNearestBlock(Material.OAK_LOG, 20);
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
}

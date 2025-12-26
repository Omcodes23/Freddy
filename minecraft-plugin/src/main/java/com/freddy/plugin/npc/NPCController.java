package com.freddy.plugin.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Logger;

/**
 * Controls NPC Freddy - executes Minecraft operations
 */
public class NPCController {
    private static final Logger logger = Logger.getLogger("Freddy NPC");
    
    private final String npcName;
    private Player npcEntity;
    private NPCInventory npcInventory;
    private Location currentGoal;
    private Queue<NPCAction> actionQueue = new LinkedList<>();
    
    public NPCController(String npcName) {
        this.npcName = npcName;
        this.npcInventory = new NPCInventory();
    }
    
    /**
     * Set NPC entity reference (when NPC spawns)
     */
    public void setNPCEntity(Player entity) {
        this.npcEntity = entity;
        logger.info("NPC Controller initialized for: " + npcName);
    }
    
    /**
     * Mine a block at coordinates
     */
    public void mineBlock(int x, int y, int z) {
        if (npcEntity == null) return;
        
        World world = npcEntity.getWorld();
        Block block = world.getBlockAt(x, y, z);
        
        if (block.getType() != Material.AIR) {
            logger.info("[NPC] Mining block: " + block.getType());
            ItemStack drop = new ItemStack(block.getType());
            world.dropItemNaturally(block.getLocation(), drop);
            block.setType(Material.AIR);
            
            // Add to inventory
            npcInventory.addItem(new ItemStack(block.getType()));
        }
    }
    
    /**
     * Place a block at coordinates
     */
    public void placeBlock(int x, int y, int z, Material material) {
        if (npcEntity == null) return;
        
        // Check if NPC has the block
        if (!npcInventory.hasItem(material)) {
            logger.warning("[NPC] Don't have " + material + " to place");
            return;
        }
        
        World world = npcEntity.getWorld();
        Block block = world.getBlockAt(x, y, z);
        
        if (block.getType() == Material.AIR) {
            logger.info("[NPC] Placing block: " + material);
            block.setType(material);
            npcInventory.removeItem(material);
        }
    }
    
    /**
     * Walk to location
     */
    public void walkTo(double x, double y, double z) {
        if (npcEntity == null) return;
        
        Location target = new Location(npcEntity.getWorld(), x, y, z);
        this.currentGoal = target;
        logger.info("[NPC] Walking to: " + x + ", " + y + ", " + z);
        
        // Simple pathfinding - walk in direction
        Location current = npcEntity.getLocation();
        Vector direction = target.toVector().subtract(current.toVector()).normalize();
        npcEntity.setVelocity(direction.multiply(0.5));
    }
    
    /**
     * Attack entity
     */
    public void attackEntity(LivingEntity target) {
        if (npcEntity == null) return;
        
        logger.info("[NPC] Attacking: " + target.getName());
        // Simple melee attack
        target.damage(4, npcEntity);
    }
    
    /**
     * Eat food
     */
    public void eatFood() {
        if (npcEntity == null) return;
        
        ItemStack food = npcInventory.findFood();
        if (food != null) {
            logger.info("[NPC] Eating: " + food.getType());
            npcEntity.setFoodLevel(20);
            npcInventory.removeItem(food.getType());
        }
    }
    
    /**
     * Pick up items near NPC
     */
    public void pickupNearbyItems() {
        if (npcEntity == null) return;
        
        npcEntity.getNearbyEntities(5, 5, 5).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.Item) {
                org.bukkit.entity.Item item = (org.bukkit.entity.Item) entity;
                ItemStack stack = item.getItemStack();
                
                if (npcInventory.addItem(stack)) {
                    logger.info("[NPC] Picked up: " + stack.getType());
                    item.remove();
                }
            }
        });
    }
    
    /**
     * Get NPC inventory
     */
    public NPCInventory getInventory() {
        return npcInventory;
    }
    
    /**
     * Queue an action to execute
     */
    public void queueAction(NPCAction action) {
        actionQueue.add(action);
        logger.info("[NPC] Queued action: " + action.getType());
    }
    
    /**
     * Execute next queued action
     */
    public void executeNextAction() {
        if (actionQueue.isEmpty()) return;
        
        NPCAction action = actionQueue.poll();
        logger.info("[NPC] Executing: " + action.getType());
        
        // Execute based on action type
        action.execute(this);
    }
    
    /**
     * Tick update for NPC logic
     */
    public void tick() {
        if (npcEntity == null) return;
        
        // Auto-pickup nearby items
        pickupNearbyItems();
        
        // Execute queued actions
        if (!actionQueue.isEmpty()) {
            executeNextAction();
        }
    }
}

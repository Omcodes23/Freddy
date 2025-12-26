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
    private net.citizensnpcs.api.npc.NPC citizensNpc;
    private NPCInventory npcInventory;
    private Location currentGoal;
    private Queue<NPCAction> actionQueue = new LinkedList<>();
    private boolean creativeMode = true; // Allow creative-like actions by default
    
    public NPCController(String npcName) {
        this.npcName = npcName;
        this.npcInventory = new NPCInventory();
    }
    
    /**
     * Set NPC entity reference (when NPC spawns)
     */
    public void setNPCEntity(Player entity) {
        if (this.npcEntity == entity) return; // Skip if same entity
        this.npcEntity = entity;
        // Only log once when entity changes
        if (entity != null && citizensNpc == null) {
            try {
                for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                    if (npc.getName().equalsIgnoreCase(npcName)) {
                        this.citizensNpc = npc;
                        logger.info("NPC Controller initialized for: " + npcName);
                        break;
                    }
                }
            } catch (Throwable ignore) { }
        }
    }

    public void setCreativeMode(boolean creative) {
        this.creativeMode = creative;
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
        
        // In creative mode, allow placement without inventory
        if (!creativeMode) {
            // Check if NPC has the block
            if (!npcInventory.hasItem(material)) {
                logger.warning("[NPC] Don't have " + material + " to place");
                return;
            }
        }
        
        World world = npcEntity.getWorld();
        Block block = world.getBlockAt(x, y, z);
        
        if (block.getType() == Material.AIR) {
            logger.info("[NPC] Placing block: " + material);
            block.setType(material);
            if (!creativeMode) {
                npcInventory.removeItem(material);
            }
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
        
        // Prefer Citizens Navigator if available
        try {
            if (citizensNpc != null) {
                citizensNpc.getNavigator().setTarget(target);
                return;
            }
        } catch (Throwable ignore) { }
        
        // Fallback: simple velocity-based movement
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

    public boolean hasQueuedMineAt(int x, int y, int z) {
        for (NPCAction a : actionQueue) {
            if (a instanceof NPCAction.MineBlock mine) {
                if (mine.x == x && mine.y == y && mine.z == z) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Execute next queued action
     */
    public void executeNextAction() {
        if (actionQueue.isEmpty()) return;
        
        NPCAction action = actionQueue.poll();
        logger.info("[NPC] Executing: " + action.getType());

        // Telemetry hint when arriving to mine
        try {
            if (action instanceof NPCAction.MineBlock mine) {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) {
                    t.send("ACTION:ARRIVED_AT " + mine.x + "," + mine.y + "," + mine.z + " MINING_NOW");
                }
            }
        } catch (Exception ignore) { }

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
            // Gate mining actions until close to target to avoid premature execution
            NPCAction next = actionQueue.peek();
            if (next instanceof NPCAction.MineBlock mine) {
                Location npcLoc = npcEntity.getLocation();
                Location targetLoc = new Location(npcEntity.getWorld(), mine.x, mine.y, mine.z);
                double distance = npcLoc.distance(targetLoc);

                // Wait until near target before mining (helps uneven terrain/pathing)
                if (distance > 4.0) {
                    // Refresh navigation toward the target to avoid stalling
                    walkTo(targetLoc.getX(), targetLoc.getY(), targetLoc.getZ());
                    return;
                }
            }
            executeNextAction();
        }
    }
}

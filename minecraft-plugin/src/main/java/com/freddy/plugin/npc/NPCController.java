package com.freddy.plugin.npc;

import com.freddy.plugin.advanced.ConversionRegistry;
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
    private static final double BLOCK_REACH_DISTANCE = 5.2;
    private static final long WALK_REISSUE_MS = 5000;
    private static final int MINE_STALL_DROP_TICKS = 80;
    private static final long MINE_TARGET_COOLDOWN_MS = 6000;
    
    private final String npcName;
    private Player npcEntity;
    private net.citizensnpcs.api.npc.NPC citizensNpc;
    private NPCInventory npcInventory;
    private Location currentGoal;
    private Queue<NPCAction> actionQueue = new LinkedList<>();
    private boolean creativeMode = true; // Allow creative-like actions by default
    private String activeMineKey = "";
    private double lastMineDistance = Double.MAX_VALUE;
    private int mineStallTicks = 0;
    private final Map<String, Integer> mineApproachRetries = new HashMap<>();
    private final Map<String, Long> mineRetryEnqueueAt = new HashMap<>();
    private static final long MINE_RETRY_COOLDOWN_MS = 500;
    private final Map<String, Long> mineTargetCooldownUntil = new HashMap<>();
    private Location lastWalkTarget;
    private long lastWalkCommandAt = 0L;
    
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

        Location npcLoc = npcEntity.getLocation();
        Location blockLoc = new Location(npcEntity.getWorld(), x + 0.5, y + 0.5, z + 0.5);
        double distance = npcLoc.distance(blockLoc);
        if (distance > BLOCK_REACH_DISTANCE) {
            logger.info("[NPC] Mine target out of reach (" + String.format("%.2f", distance) + "), moving closer first");
            walkToMiningApproach(x, y, z);
            String key = x + ":" + y + ":" + z;
            mineRetryEnqueueAt.put(key, System.currentTimeMillis());
            return;
        }
        
        World world = npcEntity.getWorld();
        Block block = world.getBlockAt(x, y, z);
        
        if (block.getType() != Material.AIR) {
            Material minedType = block.getType();
            logger.info("[NPC] Mining block: " + minedType);
            
            // Show hand swing animation
            try {
                npcEntity.swingMainHand();
            } catch (Throwable e) {
                logger.warning("[NPC] Failed to swing hand: " + e.getMessage());
            }
            
            ItemStack drop = new ItemStack(resolveDropMaterial(minedType));
            world.dropItemNaturally(block.getLocation(), drop);
            block.setType(Material.AIR);
            
            // Add to inventory
            npcInventory.addItem(drop);
        }
    }

    private Material resolveDropMaterial(Material minedType) {
        if (ConversionRegistry.isOre(minedType)) {
            return ConversionRegistry.dropForOre(minedType);
        }
        if (minedType == Material.DIAMOND_ORE || minedType == Material.DEEPSLATE_DIAMOND_ORE) {
            return Material.DIAMOND;
        }
        if (minedType == Material.COAL_ORE || minedType == Material.DEEPSLATE_COAL_ORE) {
            return Material.COAL;
        }
        if (minedType == Material.IRON_ORE || minedType == Material.DEEPSLATE_IRON_ORE) {
            return Material.RAW_IRON;
        }
        if (minedType == Material.COPPER_ORE || minedType == Material.DEEPSLATE_COPPER_ORE) {
            return Material.RAW_COPPER;
        }
        if (minedType == Material.GOLD_ORE || minedType == Material.DEEPSLATE_GOLD_ORE) {
            return Material.RAW_GOLD;
        }
        return minedType;
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
        
        if (block.getType() == Material.AIR || isReplaceable(block.getType())) {
            logger.info("[NPC] Placing block: " + material + (block.getType() != Material.AIR ? " (replacing " + block.getType() + ")" : ""));
            
            // Show hand swing animation
            try {
                npcEntity.swingMainHand();
            } catch (Throwable e) {
                logger.warning("[NPC] Failed to swing hand: " + e.getMessage());
            }
            
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

        long now = System.currentTimeMillis();
        if (lastWalkTarget != null) {
            double sameTargetDistance = lastWalkTarget.distance(target);
            if (sameTargetDistance < 0.35 && (now - lastWalkCommandAt) < WALK_REISSUE_MS) {
                return;
            }
        }

        lastWalkTarget = target.clone();
        lastWalkCommandAt = now;
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

    public void walkToMiningApproach(int x, int y, int z) {
        if (npcEntity == null) return;

        World world = npcEntity.getWorld();
        double[][] offsets = new double[][] {
            { 1.5,  0.0},
            {-1.5,  0.0},
            { 0.0,  1.5},
            { 0.0, -1.5},
            { 2.2,  0.0},
            {-2.2,  0.0},
            { 0.0,  2.2},
            { 0.0, -2.2}
        };

        Location npcLoc = npcEntity.getLocation();
        Location best = null;
        double bestDist = Double.MAX_VALUE;

        for (double[] off : offsets) {
            int ax = (int) Math.floor(x + off[0]);
            int az = (int) Math.floor(z + off[1]);
            int groundY = world.getHighestBlockYAt(ax, az);

            // Prefer target-level approach so Freddy can descend/ascend to real mine targets.
            double desiredY = y + 1.0;
            if (groundY <= y + 2) {
                desiredY = Math.max(desiredY, groundY + 1.0);
            }
            desiredY = Math.max(npcLoc.getY() - 6.0, Math.min(npcLoc.getY() + 6.0, desiredY));

            Location candidate = new Location(world, ax + 0.5, desiredY, az + 0.5);
            double d = npcLoc.distance(candidate);
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        if (best == null) {
            walkTo(x + 0.5, y, z + 0.5);
        } else {
            walkTo(best.getX(), best.getY(), best.getZ());
        }
    }
    
    /**
     * Attack entity with weapon-scaled damage
     */
    private long lastAttackTime = 0;
    private static final long ATTACK_COOLDOWN_MS = 500;

    public void attackEntity(LivingEntity target) {
        if (npcEntity == null || target == null) return;
        
        // Attack cooldown
        long now = System.currentTimeMillis();
        if (now - lastAttackTime < ATTACK_COOLDOWN_MS) return;
        lastAttackTime = now;
        
        // Check range
        double distance = npcEntity.getLocation().distance(target.getLocation());
        if (distance > 4.0) {
            walkTo(target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ());
            return;
        }
        
        // Scale damage by best weapon in inventory
        double damage = 1.0; // Fist damage
        if (npcInventory.hasItem(org.bukkit.Material.DIAMOND_SWORD)) damage = 7.0;
        else if (npcInventory.hasItem(org.bukkit.Material.IRON_SWORD)) damage = 6.0;
        else if (npcInventory.hasItem(org.bukkit.Material.STONE_SWORD)) damage = 5.0;
        else if (npcInventory.hasItem(org.bukkit.Material.WOODEN_SWORD)) damage = 4.0;
        else if (npcInventory.hasItem(org.bukkit.Material.DIAMOND_AXE)) damage = 6.0;
        else if (npcInventory.hasItem(org.bukkit.Material.IRON_AXE)) damage = 5.0;
        else if (npcInventory.hasItem(org.bukkit.Material.STONE_AXE)) damage = 4.0;
        
        logger.info("[NPC] Attacking: " + target.getName() + " for " + damage + " damage");
        target.damage(damage, npcEntity);
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

    public boolean hasPendingMineAction() {
        if (!activeMineKey.isBlank()) {
            return true;
        }
        for (NPCAction a : actionQueue) {
            if (a instanceof NPCAction.MineBlock) {
                return true;
            }
        }
        return false;
    }

    public boolean isMineTargetCoolingDown(int x, int y, int z) {
        String key = x + ":" + y + ":" + z;
        long until = mineTargetCooldownUntil.getOrDefault(key, 0L);
        return System.currentTimeMillis() < until;
    }

    public void addMineCooldown(int x, int y, int z, long durationMs) {
        String key = x + ":" + y + ":" + z;
        mineTargetCooldownUntil.put(key, System.currentTimeMillis() + durationMs);
    }

    public void clearActionQueue() {
        actionQueue.clear();
        activeMineKey = "";
        lastMineDistance = Double.MAX_VALUE;
        mineStallTicks = 0;
        mineApproachRetries.clear();
        mineRetryEnqueueAt.clear();
        mineTargetCooldownUntil.clear();
        lastWalkTarget = null;
        lastWalkCommandAt = 0L;
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
                Block targetBlock = npcEntity.getWorld().getBlockAt(mine.x, mine.y, mine.z);
                if (targetBlock.getType() == Material.AIR) {
                    actionQueue.poll();
                    mineApproachRetries.remove(mine.x + ":" + mine.y + ":" + mine.z);
                    activeMineKey = "";
                    lastMineDistance = Double.MAX_VALUE;
                    mineStallTicks = 0;
                    return;
                }

                Location npcLoc = npcEntity.getLocation();
                Location targetLoc = new Location(npcEntity.getWorld(), mine.x + 0.5, mine.y + 0.5, mine.z + 0.5);
                double distance = npcLoc.distance(targetLoc);

                String key = mine.x + ":" + mine.y + ":" + mine.z;
                if (!key.equals(activeMineKey)) {
                    activeMineKey = key;
                    lastMineDistance = distance;
                    mineStallTicks = 0;
                } else {
                    if (Math.abs(lastMineDistance - distance) < 0.05) {
                        mineStallTicks++;
                    } else {
                        mineStallTicks = 0;
                    }
                    lastMineDistance = distance;
                }

                // Wait until close enough for real player-like mining reach.
                if (distance > BLOCK_REACH_DISTANCE) {
                    walkToMiningApproach(mine.x, mine.y, mine.z);

                    if (mineStallTicks >= MINE_STALL_DROP_TICKS) {
                        int retries = mineApproachRetries.getOrDefault(key, 0) + 1;
                        mineApproachRetries.put(key, retries);

                        if (retries >= 3) {
                            logger.info("[NPC] Mine approach stalled too long; abandoning target (cooldown): " + key);
                            actionQueue.poll();
                            addMineCooldown(mine.x, mine.y, mine.z, 30000L);
                            mineApproachRetries.remove(key);
                            lastWalkTarget = null;
                            lastWalkCommandAt = 0L;
                            activeMineKey = "";
                            lastMineDistance = Double.MAX_VALUE;
                            mineStallTicks = 0;
                            return;
                        }

                        int hash = Math.abs(key.hashCode() + mineStallTicks);
                        double dx = (hash % 2 == 0) ? 2.2 : -2.2;
                        double dz = ((hash / 2) % 2 == 0) ? 2.2 : -2.2;
                        walkTo(mine.x + 0.5 + dx, mine.y + 1.0, mine.z + 0.5 + dz);
                        logger.info("[NPC] Mine approach stalled; retrying alternate path (" + retries + "/3): " + key);
                        // Keep the same mine action queued; just reset stall tracking and retry from a new angle.
                        lastWalkTarget = null;
                        lastWalkCommandAt = 0L;
                        activeMineKey = "";
                        lastMineDistance = Double.MAX_VALUE;
                        mineStallTicks = 0;
                    }
                    return;
                }

                activeMineKey = "";
                lastMineDistance = Double.MAX_VALUE;
                mineStallTicks = 0;
                mineApproachRetries.remove(key);
            }
            executeNextAction();
        }
    }

    /**
     * Check if a block material is replaceable (vegetation, snow layers, etc.)
     * These blocks should be cleared automatically when placing build blocks.
     */
    private boolean isReplaceable(Material mat) {
        if (mat == null || mat == Material.AIR) return true;
        String name = mat.name();
        // Short grass, tall grass, ferns, dead bushes
        if (name.contains("GRASS") && !name.contains("GRASS_BLOCK")) return true;
        if (name.equals("SHORT_GRASS") || name.equals("TALL_GRASS")) return true;
        if (name.contains("FERN")) return true;
        if (name.equals("DEAD_BUSH")) return true;
        // Flowers
        if (name.contains("FLOWER") || name.contains("TULIP") || name.contains("ORCHID")
            || name.contains("ALLIUM") || name.contains("AZURE") || name.contains("DAISY")
            || name.contains("POPPY") || name.contains("DANDELION") || name.contains("CORNFLOWER")
            || name.contains("LILY_OF_THE_VALLEY") || name.contains("BLUET")) return true;
        // Snow layer, vines, kelp, seagrass, etc.
        if (name.equals("SNOW")) return true;  // Snow layer (not SNOW_BLOCK)
        if (name.contains("VINE")) return true;
        if (name.contains("SEAGRASS")) return true;
        if (name.contains("KELP")) return true;
        // Mushrooms, saplings, sweet berry bushes
        if (name.contains("MUSHROOM") && !name.contains("BLOCK") && !name.contains("STEM")) return true;
        if (name.contains("SAPLING")) return true;
        if (name.equals("SWEET_BERRY_BUSH")) return true;
        // Cave air / void air
        if (name.equals("CAVE_AIR") || name.equals("VOID_AIR")) return true;
        // Water and lava should NOT be auto-replaced (could be dangerous)
        return false;
    }
}

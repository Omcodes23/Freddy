package com.freddy.plugin.perception;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * Creates ASCII/visual representations of the Minecraft world around a location.
 * Used to send visual data to the dashboard for real-time visualization.
 */
public class WorldVisualization {
    
    private static final int VIEW_RANGE = 12;  // blocks to show in each direction
    private static final int HEIGHT_RANGE = 3; // blocks above and below
    
    /**
     * Create a top-down ASCII map of blocks around a location
     * ░ = air/empty
     * █ = solid block
     * ▓ = semi-transparent block (leaves, glass, etc)
     * ▒ = liquid
     * @ = player/NPC position
     * ◆ = other entity
     */
    public static String createTopDownView(Location center, List<Entity> nearbyEntities) {
        World world = center.getWorld();
        if (world == null) return "═══════════════════════════════════════════════════════════\nERROR: No world\n═══════════════════════════════════════════════════════════";
        
        StringBuilder map = new StringBuilder();
        
        // Header
        map.append("╔════════════════════════════════════════════════════════════╗\n");
        map.append(String.format("║ VISUAL FEED | POS: (%.0f, %.0f) | HEADING: %s    ║\n",
            center.getX(), center.getZ(), getHeading(center.getYaw())));
        map.append("╠════════════════════════════════════════════════════════════╣\n");
        
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int centerY = center.getBlockY();
        
        // Create the map view (overhead view)
        for (int z = centerZ - VIEW_RANGE; z <= centerZ + VIEW_RANGE; z++) {
            map.append("║ ");
            
            for (int x = centerX - VIEW_RANGE; x <= centerX + VIEW_RANGE; x++) {
                char blockChar = ' ';
                
                // Check if this is the center (NPC position)
                if (x == centerX && z == centerZ) {
                    blockChar = '@'; // NPC position
                } else {
                    // Check for nearby entities
                    boolean hasEntity = false;
                    for (Entity entity : nearbyEntities) {
                        int ex = entity.getLocation().getBlockX();
                        int ez = entity.getLocation().getBlockZ();
                        if (ex == x && ez == z && !entity.getLocation().equals(center)) {
                            blockChar = '◆';
                            hasEntity = true;
                            break;
                        }
                    }
                    
                    if (!hasEntity) {
                        // Get the topmost solid block at this location
                        Block block = world.getHighestBlockAt(x, z);
                        if (block != null) {
                            Material material = block.getType();
                            blockChar = getMaterialChar(material);
                        }
                    }
                }
                
                map.append(blockChar);
            }
            
            map.append(" ║\n");
        }
        
        map.append("╠════════════════════════════════════════════════════════════╣\n");
        map.append("║ @ = You | ◆ = Entities | █ = Blocks | ░ = Empty            ║\n");
        map.append("╚════════════════════════════════════════════════════════════╝");
        
        return map.toString();
    }
    
    /**
     * Create a first-person view showing nearby blocks in a cone-like pattern
     */
    public static String createFirstPersonView(Location center, List<Entity> nearbyEntities) {
        World world = center.getWorld();
        if (world == null) return "ERROR: No world";
        
        StringBuilder view = new StringBuilder();
        
        // Header with position and heading
        view.append("╔════════════════════════════════════════════════════════════╗\n");
        view.append("║                    ◉ NEURAL VISUAL FEED ◉                  ║\n");
        view.append(String.format("║ POS: (%.1f, %.1f, %.1f) | Y: %d | HEADING: %s        ║\n",
            center.getX(), center.getY(), center.getZ(), center.getBlockY(), 
            getHeading(center.getYaw())));
        view.append("╠════════════════════════════════════════════════════════════╣\n");
        
        // Display what's in front (simplified first-person view)
        view.append("║                                                            ║\n");
        
        // Check blocks in front
        int frontDistance = 5;
        double yaw = center.getYaw();
        double pitch = center.getPitch();
        
        // Calculate direction vector
        double dirX = -Math.sin(Math.toRadians(yaw));
        double dirZ = Math.cos(Math.toRadians(yaw));
        double dirY = -Math.sin(Math.toRadians(pitch));
        
        // Show nearby blocks
        StringBuilder blockLine = new StringBuilder();
        blockLine.append("║ ");
        
        for (int i = 1; i <= frontDistance; i++) {
            int blockX = center.getBlockX() + (int)(dirX * i);
            int blockZ = center.getBlockZ() + (int)(dirZ * i);
            int blockY = center.getBlockY() + (int)(dirY * i);
            
            Block block = world.getBlockAt(blockX, blockY, blockZ);
            if (block != null) {
                char c = getMaterialChar(block.getType());
                blockLine.append(c).append(" ");
            } else {
                blockLine.append("░ ");
            }
        }
        
        // Pad to full width
        while (blockLine.length() < 58) {
            blockLine.append(" ");
        }
        blockLine.append("║\n");
        view.append(blockLine);
        
        // Entity list
        if (nearbyEntities != null && !nearbyEntities.isEmpty()) {
            view.append("║ NEARBY ENTITIES:                                           ║\n");
            int entityCount = 0;
            for (Entity entity : nearbyEntities) {
                if (entityCount >= 3) break; // Show max 3 entities
                
                String entityName = entity.getType().name().substring(0, Math.min(10, entity.getType().name().length()));
                double distance = entity.getLocation().distance(center);
                
                String line = String.format("║   • %s (%.1f blocks away)                        ║\n",
                    entityName, distance);
                view.append(line);
                entityCount++;
            }
        }
        
        view.append("║                                                            ║\n");
        view.append("╚════════════════════════════════════════════════════════════╝");
        
        return view.toString();
    }
    
    /**
     * Get ASCII character for a block material
     */
    private static char getMaterialChar(Material material) {
        if (material == null || material == Material.AIR) {
            return '░'; // Empty
        }
        
        String name = material.name();
        
        // Solid blocks
        if (name.contains("STONE") || name.contains("DIRT") || name.contains("GRASS") || 
            name.contains("SAND") || name.contains("GRAVEL") || name.contains("ORE")) {
            return '█'; // Solid block
        }
        
        // Wood/trees
        if (name.contains("LOG") || name.contains("WOOD") || name.contains("PLANKS")) {
            return '▲'; // Tree/Wood
        }
        
        // Leaves
        if (name.contains("LEAVES")) {
            return '▓'; // Semi-transparent
        }
        
        // Water/Lava
        if (name.contains("WATER") || name.contains("LAVA")) {
            return '≈'; // Liquid
        }
        
        // Glass
        if (name.contains("GLASS")) {
            return '▢'; // Glass
        }
        
        // Default
        return '■';
    }
    
    /**
     * Create a raytracing vision cone visualization
     * Shows what blocks are in the NPC's line of sight
     * Casts rays in a cone pattern from the NPC's position
     */
    public static String createVisionConeView(Location center, List<Entity> nearbyEntities) {
        World world = center.getWorld();
        if (world == null) return "═══════════════════════════════════════════════════════════\nERROR: No world\n═══════════════════════════════════════════════════════════";
        
        StringBuilder view = new StringBuilder();
        
        // Header
        view.append("╔════════════════════════════════════════════════════════════╗\n");
        view.append("║           ⚡ VISION RAYTRACING CONE (LINE-OF-SIGHT)         ║\n");
        view.append(String.format("║ POS: (%.1f, %.1f, %.1f) | HEADING: %s        ║\n",
            center.getX(), center.getY(), center.getZ(), getHeading(center.getYaw())));
        view.append("╠════════════════════════════════════════════════════════════╣\n");
        
        double yaw = center.getYaw();
        double pitch = center.getPitch();
        
        // Calculate direction vector
        double dirX = -Math.sin(Math.toRadians(yaw));
        double dirZ = Math.cos(Math.toRadians(yaw));
        double dirY = -Math.sin(Math.toRadians(pitch));
        
        // Normalize direction
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= length;
        dirY /= length;
        dirZ /= length;
        
        // Cast rays in a cone pattern (3x5 grid of rays)
        int rayDistance = 15;
        int rayConeWidth = 3;   // 3 rays horizontally
        int rayConHeight = 5;   // 5 rays vertically
        int hitCount = 0;
        StringBuilder rayMap = new StringBuilder();
        
        for (int ry = -rayConHeight / 2; ry <= rayConHeight / 2; ry++) {
            for (int rx = -rayConeWidth / 2; rx <= rayConeWidth / 2; rx++) {
                // Spread rays slightly for cone effect
                double spreadX = dirX + (rx * 0.15);
                double spreadY = dirY + (ry * 0.10);
                double spreadZ = dirZ;
                
                // Normalize spread direction
                double spreadLen = Math.sqrt(spreadX * spreadX + spreadY * spreadY + spreadZ * spreadZ);
                spreadX /= spreadLen;
                spreadY /= spreadLen;
                spreadZ /= spreadLen;
                
                // Cast ray and find first hit
                char hitChar = '·'; // Empty ray
                int distance = rayDistance;
                
                for (int d = 1; d <= rayDistance; d++) {
                    int blockX = center.getBlockX() + (int)(spreadX * d);
                    int blockY = center.getBlockY() + (int)(spreadY * d);
                    int blockZ = center.getBlockZ() + (int)(spreadZ * d);
                    
                    Block block = world.getBlockAt(blockX, blockY, blockZ);
                    if (block != null && !block.getType().isAir()) {
                        // Hit a solid block
                        hitChar = getMaterialChar(block.getType());
                        distance = d;
                        hitCount++;
                        break;
                    }
                }
                
                rayMap.append(hitChar);
            }
            rayMap.append("\n║ ");
        }
        
        // Format the ray grid
        view.append("║ ");
        view.append(rayMap);
        
        // Entity detections (what's in the vision cone)
        view.append("║\n");
        view.append("║ RAYCAST HITS: " + hitCount + "/15 rays hit objects                       ║\n");
        view.append("║ VISIBLE ENTITIES:                                          ║\n");
        
        if (nearbyEntities != null && !nearbyEntities.isEmpty()) {
            int entityCount = 0;
            for (Entity entity : nearbyEntities) {
                if (entityCount >= 5) break;
                
                // Check if entity is in vision cone
                double entityX = entity.getLocation().getX() - center.getX();
                double entityY = entity.getLocation().getY() - center.getY();
                double entityZ = entity.getLocation().getZ() - center.getZ();
                
                double dot = entityX * dirX + entityY * dirY + entityZ * dirZ;
                if (dot > 0) { // Entity is in front (positive dot product)
                    double distance = Math.sqrt(entityX * entityX + entityY * entityY + entityZ * entityZ);
                    String entityName = entity.getType().name();
                    if (entityName.length() > 12) entityName = entityName.substring(0, 12);
                    
                    view.append(String.format("║   ◆ %s @ %.1f blocks (angle: %.1f°)     ║\n",
                        entityName, distance, Math.toDegrees(Math.acos(dot / distance))));
                    entityCount++;
                }
            }
        }
        
        view.append("╚════════════════════════════════════════════════════════════╝");
        return view.toString();
    }
    
    private static String getHeading(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 315 || yaw < 45) return "SOUTH ↓";
        if (yaw >= 45 && yaw < 135) return "WEST ←";
        if (yaw >= 135 && yaw < 225) return "NORTH ↑";
        return "EAST →";
    }
}

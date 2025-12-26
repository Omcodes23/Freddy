package com.freddy.plugin.perception;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import java.util.*;

/**
 * AI First-Person Perception System
 * Simulates what Freddy sees from their perspective
 */
public class AIPerception {
    
    private final Location npcLocation;
    private final double perceptionRadius;
    private final float yaw; // Direction facing
    
    private List<PlayerPerception> seenPlayers;
    private List<BlockPerception> seenBlocks;
    private EnvironmentData environment;
    
    public AIPerception(Location npcLocation, double perceptionRadius, float yaw) {
        this.npcLocation = npcLocation;
        this.perceptionRadius = perceptionRadius;
        this.yaw = yaw;
        this.seenPlayers = new ArrayList<>();
        this.seenBlocks = new ArrayList<>();
        this.environment = new EnvironmentData();
    }
    
    /**
     * Build what Freddy sees from POV
     */
    public POVData buildPOV(Collection<? extends Entity> nearbyEntities) {
        seenPlayers.clear();
        seenBlocks.clear();
        
        // Scan for players
        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (isVisible(player.getLocation())) {
                    double distance = npcLocation.distance(player.getLocation());
                    double angle = getRelativeAngle(player.getLocation());
                    double verticalAngle = getVerticalAngle(player.getLocation());
                    
                    seenPlayers.add(new PlayerPerception(
                        player.getName(),
                        distance,
                        angle,
                        verticalAngle,
                        player.getHealth(),
                        player.getFoodLevel()
                    ));
                }
            }
        }
        
        // Scan blocks ahead (simplified)
        scanBlocksAhead();
        
        return new POVData(seenPlayers, seenBlocks, environment);
    }
    
    private void scanBlocksAhead() {
        // Scan 3D grid in front of Freddy
        Location center = npcLocation.clone().add(
            getDirectionVector().multiply(5)
        );
        
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Location blockLoc = center.clone().add(x, y, z);
                    Block block = blockLoc.getBlock();
                    
                    if (block.getType().toString().equals("AIR")) continue;
                    
                    double distance = npcLocation.distance(blockLoc);
                    seenBlocks.add(new BlockPerception(
                        block.getType().toString(),
                        distance,
                        new int[]{x, y, z}
                    ));
                }
            }
        }
    }
    
    private boolean isVisible(Location target) {
        // Simple line of sight check
        double distance = npcLocation.distance(target);
        return distance <= perceptionRadius;
    }
    
    private double getRelativeAngle(Location target) {
        // Get angle relative to where NPC is facing
        double dx = target.getX() - npcLocation.getX();
        double dz = target.getZ() - npcLocation.getZ();
        double angle = Math.atan2(dx, dz) * 180 / Math.PI;
        return normalizeAngle(angle - yaw);
    }
    
    private double getVerticalAngle(Location target) {
        // Pitch angle (up/down)
        double dy = target.getY() - npcLocation.getY();
        double dx = target.getX() - npcLocation.getX();
        double dz = target.getZ() - npcLocation.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return Math.atan2(dy, horizontal) * 180 / Math.PI;
    }
    
    private org.bukkit.util.Vector getDirectionVector() {
        double radians = Math.toRadians(yaw + 90); // Bukkit yaw offset
        double x = Math.cos(radians);
        double z = Math.sin(radians);
        return new org.bukkit.util.Vector(x, 0, z).normalize();
    }
    
    private double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
    
    // ===== DATA CLASSES FOR POV =====
    
    public static class POVData {
        public final List<PlayerPerception> players;
        public final List<BlockPerception> blocks;
        public final EnvironmentData environment;
        
        public POVData(List<PlayerPerception> players, 
                      List<BlockPerception> blocks,
                      EnvironmentData environment) {
            this.players = players;
            this.blocks = blocks;
            this.environment = environment;
        }
        
        @Override
        public String toString() {
            return String.format(
                "POV{players=%d, blocks=%d, brightness=%d°C}",
                players.size(), blocks.size(), 
                (int) environment.brightness
            );
        }
    }
    
    public static class PlayerPerception {
        public final String name;
        public final double distance;        // meters
        public final double angle;           // -180 to 180 (relative to facing)
        public final double verticalAngle;   // -90 to 90 (up/down)
        public final double health;
        public final int hunger;
        
        public PlayerPerception(String name, double distance, double angle, 
                               double verticalAngle, double health, int hunger) {
            this.name = name;
            this.distance = distance;
            this.angle = angle;
            this.verticalAngle = verticalAngle;
            this.health = health;
            this.hunger = hunger;
        }
        
        @Override
        public String toString() {
            String position = "CENTER";
            if (angle < -45) position = "LEFT";
            else if (angle > 45) position = "RIGHT";
            
            String height = "EYE_LEVEL";
            if (verticalAngle < -15) height = "BELOW";
            else if (verticalAngle > 15) height = "ABOVE";
            
            return String.format("%s (%s, %s) - %.1fm away", 
                name, position, height, distance);
        }
    }
    
    public static class BlockPerception {
        public final String blockType;
        public final double distance;
        public final int[] relativePosition; // [x, y, z] relative to center
        
        public BlockPerception(String blockType, double distance, int[] relativePosition) {
            this.blockType = blockType;
            this.distance = distance;
            this.relativePosition = relativePosition;
        }
        
        @Override
        public String toString() {
            return String.format("%s at [%d,%d,%d] (%.1fm)",
                blockType, relativePosition[0], relativePosition[1], 
                relativePosition[2], distance);
        }
    }
    
    public static class EnvironmentData {
        public double brightness;    // 0-15 light level
        public String weather;       // CLEAR, RAIN, THUNDER
        public String biome;         // PLAINS, FOREST, etc
        public int skyLight;
        public int blockLight;
        
        public EnvironmentData() {
            this.brightness = 12;
            this.weather = "CLEAR";
            this.biome = "PLAINS";
            this.skyLight = 15;
            this.blockLight = 0;
        }
    }
}

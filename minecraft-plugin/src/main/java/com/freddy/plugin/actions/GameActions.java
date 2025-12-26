package com.freddy.plugin.actions;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Advanced game actions for Freddy
 * Handles: movement, blocks, combat, chat, gestures
 */
public class GameActions {
    
    private final NPC freddy;
    private final Player targetPlayer;
    private static final double PERCEPTION_RADIUS = 50.0;
    
    public GameActions(NPC freddy, Player targetPlayer) {
        this.freddy = freddy;
        this.targetPlayer = targetPlayer;
    }
    
    // ===== MOVEMENT ACTIONS =====
    
    public void walkTo(double x, double z) {
        Location current = freddy.getEntity().getLocation();
        double groundY = getGroundLevel(new Location(current.getWorld(), x, 100, z));
        Location target = new Location(current.getWorld(), x, groundY, z);
        freddy.getNavigator().setTarget(target);
    }
    
    public void followPlayer(Player player) {
        freddy.getNavigator().setTarget(player, false);
    }
    
    public void wander() {
        Location current = freddy.getEntity().getLocation();
        double angle = Math.random() * 360;
        double distance = 15 + Math.random() * 25;
        
        double x = current.getX() + Math.cos(Math.toRadians(angle)) * distance;
        double z = current.getZ() + Math.sin(Math.toRadians(angle)) * distance;
        
        walkTo(x, z);
    }
    
    public void idle() {
        freddy.getNavigator().cancelNavigation();
    }
    
    public void jump() {
        freddy.getEntity().setVelocity(
            freddy.getEntity().getVelocity().setY(0.5)
        );
    }
    
    public void turnTowards(Player player) {
        Location target = player.getLocation();
        Location current = freddy.getEntity().getLocation();
        
        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        double yaw = Math.atan2(-dx, dz);
        
        current.setYaw((float) Math.toDegrees(yaw));
        freddy.getEntity().teleport(current);
    }
    
    // ===== INTERACTION ACTIONS =====
    
    public void interactBlock(Block block) {
        // Punch/interact with block (doors, buttons, levers)
        // NPC will visually move to the block
        turnTowards(targetPlayer);
    }
    
    public void placeBlock(Material material) {
        // Place block in front of Freddy
        Location loc = freddy.getEntity().getLocation();
        Location front = loc.add(
            loc.getDirection().multiply(2)
        );
        if (front.getBlock().getType() == Material.AIR) {
            front.getBlock().setType(material);
        }
    }
    
    public void breakBlock(Block block) {
        // Break block
        block.breakNaturally();
    }
    
    public void mineNearby() {
        // Mine blocks in front of Freddy
        Location loc = freddy.getEntity().getLocation();
        Location front = loc.add(
            loc.getDirection().multiply(3)
        );
        if (front.getBlock().getType() != Material.AIR) {
            breakBlock(front.getBlock());
        }
    }
    
    // ===== COMBAT ACTIONS =====
    
    public void attackNearby() {
        if (targetPlayer != null && isInRange(targetPlayer, 3)) {
            // NPC attacks by moving close to target
            freddy.getNavigator().setTarget(targetPlayer, true);
        }
    }
    
    public void shield() {
        // Hold defensive position (stop moving)
        freddy.getNavigator().cancelNavigation();
    }
    
    public void dodge() {
        // Jump back from attacker
        Location current = freddy.getEntity().getLocation();
        org.bukkit.util.Vector away = current.getDirection().multiply(-1);
        freddy.getEntity().setVelocity(away);
    }
    
    // ===== COMMUNICATION ACTIONS =====
    
    public void chat(String message) {
        if (freddy.getEntity() instanceof Player) {
            ((Player) freddy.getEntity()).chat(message);
        }
    }
    
    public void sayHello(Player player) {
        String[] greetings = {
            "Hello " + player.getName() + "!",
            "Hi there!",
            "Nice to meet you " + player.getName(),
            "Welcome!",
            "What's up " + player.getName() + "?"
        };
        chat(greetings[(int) (Math.random() * greetings.length)]);
    }
    
    public void sayGoodbye(Player player) {
        String[] farewells = {
            "See you later " + player.getName() + "!",
            "Goodbye!",
            "Until next time!",
            "Take care!",
            "Safe travels " + player.getName() + "!"
        };
        chat(farewells[(int) (Math.random() * farewells.length)]);
    }
    
    public void expressConfusion() {
        String[] expressions = {
            "Hmm, I'm not sure what to do...",
            "That's confusing",
            "I'm thinking...",
            "Let me consider that",
            "Interesting choice"
        };
        chat(expressions[(int) (Math.random() * expressions.length)]);
    }
    
    // ===== OBSERVATION/SENSING =====
    
    public BlockInfo getBlockAhead() {
        Location loc = freddy.getEntity().getLocation();
        Location ahead = loc.add(
            loc.getDirection().multiply(2)
        );
        Block block = ahead.getBlock();
        return new BlockInfo(
            block.getType().toString(),
            ahead,
            isObstructing(block.getType())
        );
    }
    
    public int countNearbyPlayers() {
        return (int) freddy.getEntity().getNearbyEntities(
            PERCEPTION_RADIUS, PERCEPTION_RADIUS, PERCEPTION_RADIUS
        ).stream()
        .filter(e -> e instanceof Player)
        .count();
    }
    
    public double distanceTo(Player player) {
        return freddy.getEntity().getLocation().distance(player.getLocation());
    }
    
    // ===== UTILITY METHODS =====
    
    private double getGroundLevel(Location loc) {
        Location check = loc.clone();
        check.setY(100);
        while (check.getY() > 0) {
            if (check.getBlock().getType() != Material.AIR) {
                return check.getY() + 1;
            }
            check.setY(check.getY() - 1);
        }
        return loc.getY();
    }
    
    private boolean isInRange(Player player, double range) {
        return distanceTo(player) <= range;
    }
    
    private boolean isObstructing(Material material) {
        return material != Material.AIR && 
               material != Material.WATER &&
               material != Material.LAVA;
    }
    
    // ===== DATA CLASSES =====
    
    public static class BlockInfo {
        public final String type;
        public final Location location;
        public final boolean isObstructing;
        
        public BlockInfo(String type, Location location, boolean isObstructing) {
            this.type = type;
            this.location = location;
            this.isObstructing = isObstructing;
        }
    }
}

package com.freddy.plugin.brain;

import com.freddy.ai.Action;
import com.freddy.ai.AgentBrain;
import com.freddy.ai.Observation;
import com.freddy.common.TelemetryClient;
import com.freddy.plugin.actions.GameActions;
import com.freddy.plugin.chat.ChatSystem;
import com.freddy.plugin.perception.AIPerception;
import com.freddy.plugin.perception.WorldVisualization;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Brain loop that drives Freddy's autonomous behavior.
 * Runs asynchronously every 2-3 seconds.
 * 
 * Cycle: Observe → Perceive → Think → Decide → Act → Chat
 */
public class BrainLoop extends BukkitRunnable {
    
    private final NPC freddy;
    private final AgentBrain brain;
    private final Plugin plugin;
    private final Logger logger;
    private final TelemetryClient telemetry;
    private final GameActions actions;
    private final ChatSystem chatSystem;
    private AIPerception perception;
    
    private static final long TICK_INTERVAL_MS = 3000; // 3 seconds
    private static final double PERCEPTION_RADIUS = 50.0; // blocks
    
    private boolean running = false;
    private int tickCount = 0;
    private long lastActionTime = 0;
    
    // Cache for nearby entities (populated on main thread, read on async thread)
    private List<Entity> cachedNearbyEntities = List.of();
    
    public BrainLoop(NPC freddy, AgentBrain brain, Plugin plugin, ChatSystem chatSystem) {
        this.freddy = freddy;
        this.brain = brain;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.telemetry = new TelemetryClient("localhost", 25566);
        this.actions = new GameActions(freddy, null);
        this.chatSystem = chatSystem;
        this.lastActionTime = System.currentTimeMillis();
    }
    
    /**
     * Start the brain loop
     */
    public void start() {
        if (running) {
            logger.warning("Brain loop already running!");
            return;
        }
        
        running = true;
        tickCount = 0;
        
        // Try to connect telemetry (optional)
        if (telemetry.connect()) {
            logger.info("📡 Connected to dashboard");
        } else {
            logger.info("📡 Dashboard not available (optional)");
        }
        
        // Schedule on async thread, convert ms to ticks (20 ticks/sec = 50ms per tick)
        long tickInterval = TICK_INTERVAL_MS / 50;
        
        this.runTaskTimerAsynchronously(plugin, 20L, tickInterval); // Start after 1 second
        
        logger.info("🧠 Brain Loop started (tick interval: " + TICK_INTERVAL_MS + "ms)");
    }
    
    /**
     * Stop the brain loop
     */
    public void stop() {
        if (!running) return;
        
        running = false;
        this.cancel();
        
        telemetry.disconnect();
        
        logger.info("🧠 Brain Loop stopped (total ticks: " + tickCount + ")");
    }
    
    /**
     * Main thinking cycle (runs every tick)
     */
    @Override
    public void run() {
        if (!running || !freddy.isSpawned()) {
            return;
        }
        
        // Pause autonomous LLM-driven actions when AIBrainLoop has an active goal
        try {
            com.freddy.plugin.brain.AIBrainLoop aiLoop = com.freddy.plugin.FreddyPlugin.getAIBrainLoop();
            if (aiLoop != null) {
                com.freddy.plugin.npc.Goal current = aiLoop.getCurrentGoal();
                if (current != null && current.getStatus() == com.freddy.plugin.npc.Goal.GoalStatus.IN_PROGRESS) {
                    // Skip this tick to avoid conflicting behaviors
                    return;
                }
            }
        } catch (Throwable ignore) { }
        
        tickCount++;
        telemetry.sendTick(tickCount);
        
        try {
            long thinkStartTime = System.currentTimeMillis();
            
            // OBSERVE: What's the world state?
            Observation observation = observe();
            
            if (observation == null) {
                logger.warning("⚠️ Failed to create observation, skipping tick");
                telemetry.sendError("Failed to create observation");
                return;
            }
            
            logger.info("👁️ TICK " + tickCount + " - Observing: " + observation);
            
            // Send observation to dashboard
            telemetry.sendObservation(formatObservation(observation));
            
            // Send RAYTRACING vision cone (what the NPC actually sees with line-of-sight)
            String visionCone = WorldVisualization.createVisionConeView(
                freddy.getEntity().getLocation(), 
                cachedNearbyEntities
            );
            telemetry.sendPOV(visionCone);
            
            telemetry.sendPosition(observation.currentX(), observation.currentY(), observation.currentZ());
            telemetry.sendPlayers(observation.nearbyPlayers().isEmpty() ? "0" : 
                String.join(", ", observation.nearbyPlayers()));
            
            // THINK: Ask LLM what to do (this is async-safe)
            logger.info("💭 THINK: Calling LLM...");
            telemetry.sendThinking("Asking LLM for decision...");
            
            Action action = brain.think(observation, telemetry);
            
            long thinkEndTime = System.currentTimeMillis();
            long responseTime = thinkEndTime - thinkStartTime;
            telemetry.sendResponseTime(responseTime);
            
            if (action == null) {
                logger.warning("⚠️ Brain returned null action, defaulting to IDLE");
                action = new Action.Idle();
                telemetry.sendError("Brain returned null, defaulting to IDLE");
            }
            
            logger.info("✨ DECISION: " + action);
            telemetry.sendAction(action.toString());
            
            // Store the action time
            final long actionTime = System.currentTimeMillis();
            
            // DECIDE & ACT: Execute on main thread (Bukkit APIs require this)
            final Action finalAction = action;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    executeAction(finalAction);
                    brain.setAction(finalAction.toString());
                    lastActionTime = actionTime;
                } catch (Exception e) {
                    logger.severe("❌ Error executing action: " + e.getMessage());
                    telemetry.sendError("Action execution failed: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
        } catch (Exception e) {
            logger.severe("❌ Brain loop error: " + e.getMessage());
            telemetry.sendError("Brain loop error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Observe the world state (runs on async thread, but reads main-thread-safe data)
     */
    private Observation observe() {
        if (!freddy.isSpawned()) return null;
        
        var entity = freddy.getEntity();
        if (entity == null) return null;
        
        var loc = entity.getLocation();
        
        // Get nearby entities on the main thread (required by Bukkit API)
        // Since we're on async thread, schedule sync task and wait for result
        List<Entity> nearbyEntities = getNearbyEntitiesSync(entity);
        cachedNearbyEntities = nearbyEntities; // Cache for use in run() method
        
        // Get nearby players within perception radius
        List<String> nearbyPlayers = Bukkit.getOnlinePlayers().stream()
            .filter(p -> {
                Location pLoc = p.getLocation();
                return pLoc.getWorld().equals(loc.getWorld()) && 
                       pLoc.distance(loc) < PERCEPTION_RADIUS;
            })
            .map(Player::getName)
            .collect(Collectors.toList());
        
        // Get world time
        int worldTime = (int) (entity.getWorld().getTime() % 24000);
        
        // Get last action from brain
        String lastAction = brain.getCurrentAction() != null ? 
            brain.getCurrentAction() : "Initialized";
        
        return new Observation(
            nearbyPlayers,
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            worldTime,
            null,  // TODO: Add last interaction tracking in Phase 3
            0,
            lastAction,
            lastActionTime
        );
    }
    
    /**
     * Get nearby entities on the main thread (synchronously).
     * This is needed because getNearbyEntities() can only be called from the main thread.
     */
    private List<Entity> getNearbyEntitiesSync(Entity entity) {
        // Use a holder to capture the result from the sync task
        List<Entity> result = new java.util.ArrayList<>();
        
        // Schedule sync task and wait for it
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                List<Entity> nearby = entity.getNearbyEntities(50, 50, 50);
                result.addAll(nearby);
            } catch (Exception e) {
                logger.warning("⚠️ Failed to get nearby entities: " + e.getMessage());
            }
        });
        
        // Wait briefly for task to complete (it's immediate on main thread)
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return result;
    }
    
    /**
     * Execute an action on the main thread
     */
    private void executeAction(Action action) {
        if (!freddy.isSpawned()) {
            logger.warning("⚠️ Cannot execute action - Freddy not spawned");
            return;
        }
        
        var entity = freddy.getEntity();
        if (entity == null) return;
        
        switch (action.type) {
            case WALK_TO:
                if (action instanceof Action.WalkTo walk) {
                    Location target = new Location(
                        entity.getWorld(),
                        walk.x,
                        entity.getLocation().getY(),
                        walk.z
                    );
                    freddy.getNavigator().setTarget(target);
                    logger.info("🚶 Walking to: " + String.format("%.1f, %.1f", walk.x, walk.z));
                }
                break;
                
            case FOLLOW_PLAYER:
                if (action instanceof Action.FollowPlayer follow) {
                    Player target = Bukkit.getPlayer(follow.playerName);
                    if (target != null && target.isOnline()) {
                        freddy.getNavigator().setTarget(target, false);
                        logger.info("🏃 Following player: " + follow.playerName);
                    } else {
                        logger.warning("⚠️ Player not found: " + follow.playerName);
                        freddy.getNavigator().cancelNavigation();
                    }
                }
                break;
                
            case IDLE:
                freddy.getNavigator().cancelNavigation();
                logger.info("🧘 Standing idle");
                break;
                
            case LOOK_AT:
                if (action instanceof Action.LookAt look) {
                    Player target = Bukkit.getPlayer(look.target);
                    if (target != null && target.isOnline()) {
                        freddy.faceLocation(target.getLocation());
                        logger.info("👀 Looking at: " + look.target);
                    } else {
                        logger.warning("⚠️ Cannot look at - target not found: " + look.target);
                    }
                }
                break;
                
            case RESPOND:
                if (action instanceof Action.Respond respond) {
                    String message = "§a[" + freddy.getName() + "] §f" + respond.message;
                    Bukkit.broadcastMessage(message);
                    logger.info("💬 Said: " + respond.message);
                }
                break;
                
            case WANDER:
                // Random wander within 10 blocks
                var base = entity.getLocation();
                double randomX = base.getX() + (Math.random() * 10) - 5;
                double randomZ = base.getZ() + (Math.random() * 10) - 5;
                Location wanderTarget = new Location(
                    entity.getWorld(),
                    randomX,
                    base.getY(),
                    randomZ
                );
                freddy.getNavigator().setTarget(wanderTarget);
                logger.info("🎲 Wandering to: " + String.format("%.1f, %.1f", randomX, randomZ));
                break;
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getTickCount() {
        return tickCount;
    }
    
    /**
     * Format observation for dashboard display
     */
    private String formatObservation(Observation obs) {
        StringBuilder sb = new StringBuilder();
        sb.append("👁️ OBSERVATION:\n\n");
        sb.append("Position: ").append(String.format("(%.1f, %.1f, %.1f)", 
            obs.currentX(), obs.currentY(), obs.currentZ())).append("\n\n");
        
        sb.append("Nearby Players:\n");
        if (obs.nearbyPlayers().isEmpty()) {
            sb.append("  [none]\n");
        } else {
            for (String player : obs.nearbyPlayers()) {
                sb.append("  • ").append(player).append("\n");
            }
        }
        
        sb.append("\nTime: ").append(obs.getTimeOfDay());
        if (obs.isDayTime()) {
            sb.append(" ☀️ (day)");
        } else {
            sb.append(" 🌙 (night)");
        }
        sb.append("\n\n");
        
        sb.append("Last Action: ").append(obs.lastAction());
        
        return sb.toString();
    }
    
    /**
     * Format POV scene description for visual cortex display
     */
    private String formatPOVScene(Observation obs) {
        Entity entity = freddy.getEntity();
        if (entity == null) return "║ >>> ENTITY OFFLINE                        ║";
        
        Location loc = entity.getLocation();
        
        StringBuilder scene = new StringBuilder();
        
        // Return simplified scene text for logging
        scene.append("║ VISUAL: ACTIVE | BIOME: ");
        World world = loc.getWorld();
        if (world != null) {
            scene.append(world.getBiome(loc).name());
        }
        scene.append(" | Y: ").append((int)obs.currentY()).append(" ║");
        
        return scene.toString();
    }
    
    private String getCardinalDirection(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 315 || yaw < 45) return "SOUTH";
        if (yaw >= 45 && yaw < 135) return "WEST";
        if (yaw >= 135 && yaw < 225) return "NORTH";
        return "EAST";
    }
}

package com.freddy.plugin.brain;

import com.freddy.plugin.advanced.AdvancedFeatureManager;
import com.freddy.plugin.perception.AIPerception;
import com.freddy.plugin.npc.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * AI Brain Loop - integrates AI decision making with NPC actions
 */
public class AIBrainLoop extends BukkitRunnable {
    private static final Logger logger = Logger.getLogger("AI Brain");
    
    private String npcName;
    private NPCController npcController;
    private AIActionExecutor actionExecutor;
    private AutonomousAIBehavior aiBehavior;
    private GoalManager goalManager;
    private final AdvancedFeatureManager advancedFeatures;
    private int tickCount = 0;
    private boolean bootstrapTelemetrySent = false;
    
    public AIBrainLoop(String npcName) {
        this.npcName = npcName;
        this.npcController = new NPCController(npcName);
        this.goalManager = new GoalManager();
        this.actionExecutor = new AIActionExecutor(npcController, getNPCEntity());
        this.aiBehavior = new AutonomousAIBehavior(npcController, actionExecutor, goalManager, getNPCEntity());
        this.advancedFeatures = new AdvancedFeatureManager(getNPCEntity(), goalManager);
        
        logger.info("[AI BRAIN] Initialized for: " + npcName);
        logger.info("[AI BRAIN] Advanced features enabled");
    }
    
    /**
     * Get NPC entity by name or via Citizens NPC registry
     */
    private Player getNPCEntity() {
        Player p = Bukkit.getPlayer(npcName);
        if (p != null) return p;
        try {
            for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                if (npc.getName().equalsIgnoreCase(npcName) && npc.getEntity() instanceof Player) {
                    return (Player) npc.getEntity();
                }
            }
        } catch (Throwable ignore) { }
        return null;
    }
    
    @Override
    public void run() {
        long loopStartNanos = System.nanoTime();
        Player npcEntity = getNPCEntity();
        if (npcEntity == null) {
            if (tickCount % 100 == 0) { // log and telemetry every ~5s
                logger.warning("[AI BRAIN] NPC entity not found: " + npcName);
                try {
                    com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                    if (t != null) t.send("ERROR:NPC entity not found; ensure Citizens NPC '" + npcName + "' exists and is spawned.");
                } catch (Exception ignore) { }
            }
            return;
        }
        
        // Update NPC entity reference
        npcController.setNPCEntity(npcEntity);
        // Keep behavior and executor in sync with current entity
        if (actionExecutor != null) {
            // actionExecutor.setNPCEntity(npcEntity); // Removed - method doesn't exist
        }
        advancedFeatures.setNpcEntity(npcEntity);
        
        // Main AI tick
        aiBehavior.tick();
        advancedFeatures.tickReactive();
        tickCount++;
        
        // Log status periodically
        if (tickCount % 400 == 0) { // Every ~20 seconds
            logger.info("[AI BRAIN] Status: " + aiBehavior.getStatus());
            logger.info("[AI BRAIN] Inventory: " + npcController.getInventory().getItems());
        }

        // Send periodic inventory snapshot to dashboard (every 40 ticks ~2s)
        if (tickCount % 40 == 0) {
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) {
                    java.util.Map<org.bukkit.Material, Integer> items = npcController.getInventory().getItems();
                    StringBuilder sb = new StringBuilder();
                    boolean first = true;
                    for (java.util.Map.Entry<org.bukkit.Material, Integer> e : items.entrySet()) {
                        if (!first) sb.append(",");
                        sb.append(e.getKey().name()).append("=").append(e.getValue());
                        first = false;
                    }
                    t.send("INVENTORY:" + sb.toString());
                }
            } catch (Exception ignore) { }
        }

        // Send core live telemetry for mission control + travel map (~2 times/sec)
        if (tickCount % 10 == 0) {
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) {
                    if (!bootstrapTelemetrySent) {
                        t.send("GOAL_CATALOG:GATHER_WOOD,GATHER_STONE,MINE_DIAMONDS,BUILD_STRUCTURE,EXPLORE_AREA,AUTOPILOT,RETURN_TO_PLAYER,FOLLOW_PLAYER,HUNT_ANIMALS,CREATE_ITEM,PROTECT_PLAYER");
                        t.send("ADV_FEATURES:deterministic_planner,reactive_goals,workflow_safety,goal_queue,staircase_mining,surface_return,autopilot_live_steps");
                        bootstrapTelemetrySent = true;
                    }

                    t.send("TICK:" + tickCount);

                    org.bukkit.Location loc = npcEntity.getLocation();
                    t.send(String.format("POSITION:%.2f,%.2f,%.2f", loc.getX(), loc.getY(), loc.getZ()));

                    List<String> players = new ArrayList<>();
                    for (Player p : loc.getWorld().getPlayers()) {
                        if (!p.getName().equalsIgnoreCase(npcEntity.getName())) {
                            players.add(p.getName());
                        }
                    }
                    t.send("PLAYERS:" + (players.isEmpty() ? "none" : String.join(",", players)));
                    t.send("GOAL:" + com.freddy.plugin.FreddyPlugin.getCurrentGoalLabel());
                    t.send("GOAL_QUEUE:active=" + goalManager.hasActiveGoal()
                        + ",current=" + goalManager.getCurrentGoalType()
                        + ",pending=" + goalManager.getPendingGoalCount()
                        + ",completed=" + goalManager.getCompletedGoalCount());
                    t.send("WORKFLOW_SAFETY:goalFailures=" + goalManager.getGoalFailureCount()
                        + ",blacklisted=" + goalManager.workflowSafetyBlacklistedCount());

                    long elapsedMs = Math.max(1L, (System.nanoTime() - loopStartNanos) / 1_000_000L);
                    t.send("RESPONSE_TIME:" + elapsedMs);
                }
            } catch (Exception ignore) { }
        }

        // Send POV text stream for mission control visual cortex.
        if (tickCount % 20 == 0) {
            try {
                com.freddy.common.TelemetryClient t = com.freddy.plugin.FreddyPlugin.getTelemetry();
                if (t != null) {
                    org.bukkit.Location loc = npcEntity.getLocation();
                    AIPerception perception = new AIPerception(loc, 50.0, loc.getYaw());
                    var pov = perception.buildPOV(npcEntity.getNearbyEntities(50, 50, 50));
                    t.send("POV:" + formatPovFrame(loc, pov).replace("\n", "\\n"));

                    if (advancedFeatures.isEnabled()) {
                        var ws = advancedFeatures.perception().observe();
                        t.send(String.format("ADV_STATE:threat=%s,lava=%s,playerNearby=%s,blocks=%d",
                            ws.threatNearby,
                            ws.lavaNearby,
                            ws.playerNearby,
                            ws.nearbyBlocks.size()));
                    }
                }
            } catch (Exception ignore) { }
        }
    }

    private String formatPovFrame(org.bukkit.Location loc, AIPerception.POVData pov) {
        StringBuilder frame = new StringBuilder();
        frame.append("╔════════════════════════════════════════════════════════════╗\n");
        frame.append("║                 RAYTRACING VISION ACTIVE                  ║\n");
        frame.append("╠════════════════════════════════════════════════════════════╣\n");
        frame.append(String.format("║ POS: X=%6.1f Y=%6.1f Z=%6.1f  YAW=%6.1f           ║\n",
            loc.getX(), loc.getY(), loc.getZ(), (double) loc.getYaw()));
        frame.append(String.format("║ BIOME: %-50s║\n", safeBiome(loc)));
        frame.append(String.format("║ LIGHT: sky=%02d block=%02d visiblePlayers=%02d blocks=%02d     ║\n",
            pov.environment.skyLight,
            pov.environment.blockLight,
            pov.players.size(),
            pov.blocks.size()));
        frame.append("╠════════════════════════════════════════════════════════════╣\n");

        if (pov.players.isEmpty()) {
            frame.append("║ PLAYERS: [none]                                            ║\n");
        } else {
            frame.append("║ PLAYERS:                                                  ║\n");
            for (AIPerception.PlayerPerception player : pov.players) {
                frame.append(String.format("║  • %-16s %6.1fm  %-6s %-6s        ║\n",
                    player.name,
                    player.distance,
                    playerToSide(player.angle),
                    playerToHeight(player.verticalAngle)));
            }
        }

        frame.append("╠════════════════════════════════════════════════════════════╣\n");
        if (pov.blocks.isEmpty()) {
            frame.append("║ BLOCKS: [none]                                             ║\n");
        } else {
            int limit = Math.min(6, pov.blocks.size());
            frame.append("║ BLOCKS:                                                   ║\n");
            for (int i = 0; i < limit; i++) {
                AIPerception.BlockPerception block = pov.blocks.get(i);
                frame.append(String.format("║  • %-14s %5.1fm [%d,%d,%d]            ║\n",
                    block.blockType,
                    block.distance,
                    block.relativePosition[0],
                    block.relativePosition[1],
                    block.relativePosition[2]));
            }
        }

        frame.append("╚════════════════════════════════════════════════════════════╝");
        return frame.toString();
    }

    private String safeBiome(org.bukkit.Location loc) {
        try {
            return loc.getWorld() == null ? "UNKNOWN" : loc.getWorld().getBiome(loc).toString();
        } catch (Exception ignore) {
            return "UNKNOWN";
        }
    }

    private String playerToSide(double angle) {
        if (angle < -45) return "LEFT";
        if (angle > 45) return "RIGHT";
        return "CENTER";
    }

    private String playerToHeight(double angle) {
        if (angle < -15) return "BELOW";
        if (angle > 15) return "ABOVE";
        return "LEVEL";
    }
    
    /**
     * Set a goal for AI to pursue
     */
    public void setGoal(Goal.GoalType type, String description) {
        aiBehavior.setGoal(type, description);
    }
    
    /**
     * Set a goal with steps for AI to pursue
     */
    public void setGoalWithSteps(Goal.GoalType type, String description, java.util.List<GoalStep> steps) {
        aiBehavior.setGoalWithSteps(type, description, steps);
    }
    
    /**
     * Get current goal
     */
    public Goal getCurrentGoal() {
        return goalManager.getCurrentGoal();
    }
    
    /**
     * Get AI status
     */
    public String getAIStatus() {
        return aiBehavior.getStatus();
    }

    public NPCController getNpcController() {
        return npcController;
    }

    public void pauseAutonomyTicks(int ticks) {
        if (aiBehavior != null) {
            aiBehavior.pauseDecisions(ticks);
        }
    }

    public void clearGoals() {
        if (goalManager != null) {
            goalManager.clearAllGoals();
        }
    }
    
    /**
     * Start the AI brain loop (call from plugin enable)
     */
    public void start() {
        // Schedule using the correct plugin name from plugin.yml
        this.runTaskTimer(Bukkit.getPluginManager().getPlugin("FreddyAI"), 0, 1);  // Run every tick
        logger.info("[AI BRAIN] Started for: " + npcName);
    }
}

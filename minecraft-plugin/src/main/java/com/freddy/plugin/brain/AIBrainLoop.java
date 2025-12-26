package com.freddy.plugin.brain;

import com.freddy.plugin.npc.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
    private int tickCount = 0;
    
    public AIBrainLoop(String npcName) {
        this.npcName = npcName;
        this.npcController = new NPCController(npcName);
        this.goalManager = new GoalManager();
        this.actionExecutor = new AIActionExecutor(npcController, getNPCEntity());
        this.aiBehavior = new AutonomousAIBehavior(npcController, actionExecutor, goalManager, getNPCEntity());
        
        logger.info("[AI BRAIN] Initialized for: " + npcName);
    }
    
    /**
     * Get NPC entity by name
     */
    private Player getNPCEntity() {
        return Bukkit.getPlayer(npcName);
    }
    
    @Override
    public void run() {
        Player npcEntity = getNPCEntity();
        if (npcEntity == null) {
            logger.warning("[AI BRAIN] NPC entity not found: " + npcName);
            return;
        }
        
        // Update NPC entity reference
        npcController.setNPCEntity(npcEntity);
        
        // Main AI tick
        aiBehavior.tick();
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
    
    /**
     * Start the AI brain loop (call from plugin enable)
     */
    public void start() {
        // Schedule using the correct plugin name from plugin.yml
        this.runTaskTimer(Bukkit.getPluginManager().getPlugin("FreddyAI"), 0, 1);  // Run every tick
        logger.info("[AI BRAIN] Started for: " + npcName);
    }
}

package com.freddy.plugin;

import com.freddy.ai.AgentBrain;
import com.freddy.plugin.brain.BrainLoop;
import com.freddy.plugin.brain.AIBrainLoop;
import com.freddy.plugin.commands.CommandServer;
import com.freddy.plugin.listener.PlayerChatListener;
import com.freddy.plugin.npc.Goal;
import com.freddy.plugin.npc.GoalStep;
import com.freddy.plugin.npc.StepPlanner;
import com.freddy.common.TelemetryClient;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

public class FreddyPlugin extends JavaPlugin {

    private static NPC freddy;
    private static AgentBrain brain;
    private static BrainLoop brainLoop;
    private static AIBrainLoop aiBrainLoop;
    private static CommandServer commandServer;
    private static TelemetryClient telemetry;
    private static final String NPC_NAME = "Freddy";
    private static Map<String, Goal.GoalType> goalMap = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("🤖 FreddyAI enabled, waiting for Citizens...");

        // Create brain first
        brain = new AgentBrain("Freddy");
        getLogger().info("🧠 AgentBrain initialized");

        // Initialize goal map
        initializeGoalMap();
        
        // Initialize telemetry for dashboard
        telemetry = new TelemetryClient("localhost", 25566);
        if (telemetry.connect()) {
            getLogger().info("📡 Connected to dashboard on port 25566");
        } else {
            getLogger().info("📡 Dashboard not available (optional)");
        }

        // Start command server for dashboard commands
        commandServer = new CommandServer();
        commandServer.start();
        getLogger().info("🌐 Command server started on port 25567");

        getServer().getPluginManager()
                .registerEvents(new PlayerChatListener(), this);

        // Delay Citizens access (Citizens loads after plugins)
        Bukkit.getScheduler().runTaskLater(this, this::loadFreddyNPC, 20L);
    }

    private void loadFreddyNPC() {
        freddy = StreamSupport.stream(
                        CitizensAPI.getNPCRegistry().spliterator(),
                        false
                )
                .filter(npc -> npc.getName().equalsIgnoreCase("Freddy"))
                .findFirst()
                .orElse(null);

        if (freddy == null) {
            getLogger().severe("❌ Freddy NPC not found! Create it using /npc create Freddy");
        } else {
            getLogger().info("✅ Freddy NPC linked successfully!");
            
            // INITIALIZE AUTONOMOUS AI BRAIN LOOP
            initializeAIBrainLoop();
            
            // Create chat system
            com.freddy.plugin.chat.ChatSystem chatSystem = 
                new com.freddy.plugin.chat.ChatSystem(freddy, this, new com.freddy.llm.LLMClient());
            
            // Start the autonomous brain loop
            brainLoop = new BrainLoop(freddy, brain, this, chatSystem);
            brainLoop.start();
            getLogger().info("🚀 Freddy is now autonomous!");
        }
    }
    
    /**
     * Initialize AI Brain Loop for autonomous operations
     */
    private void initializeAIBrainLoop() {
        if (freddy == null || freddy.getEntity() == null) {
            getLogger().warning("⚠️ Cannot initialize AI - NPC entity not found");
            return;
        }
        
        if (!(freddy.getEntity() instanceof Player)) {
            getLogger().warning("⚠️ NPC must be a player type for AI");
            return;
        }
        
        Player freddyPlayer = (Player) freddy.getEntity();
        getLogger().info("🤖 Starting AI Brain Loop for: " + NPC_NAME);
        
        aiBrainLoop = new AIBrainLoop(NPC_NAME);
        aiBrainLoop.start();
        
        // Set initial goal
        aiBrainLoop.setGoal(Goal.GoalType.EXPLORE_AREA, "Initial autonomous exploration");
        
        getLogger().info("✅ AI Brain Loop STARTED");
        getLogger().info("   Status: " + aiBrainLoop.getAIStatus());
    }
    
    /**
     * Set goal for AI from dashboard
     */
    public static void setAIGoal(String goalName) {
        // Resolve goal type (accept exact UI labels and plain names)
        Goal.GoalType goalType = goalMap.getOrDefault(goalName.toUpperCase(), Goal.GoalType.EXPLORE_AREA);
        Bukkit.getLogger().info("📌 AI Goal: " + goalType + " - " + goalName);

        // Plan asynchronously to avoid blocking the server thread while LLM responds
        new Thread(() -> {
            List<GoalStep> steps = StepPlanner.planFor(goalType);
            Bukkit.getLogger().info("   📋 Generated " + steps.size() + " steps for goal (LLM-driven)");

            // Build steps JSON once
            StringBuilder stepsJson = new StringBuilder();
            stepsJson.append("[");
            for (int i = 0; i < steps.size(); i++) {
                stepsJson.append(steps.get(i).toJson());
                if (i < steps.size() - 1) stepsJson.append(",");
            }
            stepsJson.append("]");
            String json = stepsJson.toString();

            // Schedule back on main thread to update AI and send telemetry
            Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("FreddyAI"),
                () -> {
                    if (telemetry != null) {
                        telemetry.send("GOAL_STEPS:" + json);
                        Bukkit.getLogger().info("   📡 Sent steps to dashboard");
                    }

                    if (aiBrainLoop != null) {
                        aiBrainLoop.setGoalWithSteps(goalType, "Goal: " + goalName, steps);
                    } else {
                        Bukkit.getLogger().warning("⚠️ AI Brain Loop not initialized! NPC might be missing.");
                        if (telemetry != null) {
                            telemetry.send("ERROR:AI Brain not initialized. Ensure Citizens is installed and NPC 'Freddy' exists.");
                        }
                    }
                }
            );
        }, "StepPlannerLLM").start();
    }
    
    /**
     * Get AI status
     */
    public static String getAIStatus() {
        if (aiBrainLoop == null) return "NOT INITIALIZED";
        return aiBrainLoop.getAIStatus();
    }
    
    /**
     * Initialize goal mapping
     */
    private void initializeGoalMap() {
        goalMap.put("🌳 GATHER WOOD", Goal.GoalType.GATHER_WOOD);
        goalMap.put("GATHER_WOOD", Goal.GoalType.GATHER_WOOD);
        goalMap.put("🪨 GATHER STONE", Goal.GoalType.GATHER_STONE);
        goalMap.put("GATHER_STONE", Goal.GoalType.GATHER_STONE);
        goalMap.put("💎 MINE DIAMONDS", Goal.GoalType.MINE_DIAMONDS);
        goalMap.put("MINE_DIAMONDS", Goal.GoalType.MINE_DIAMONDS);
        goalMap.put("⚔️ HUNT MOBS", Goal.GoalType.HUNT_ANIMALS);
        goalMap.put("HUNT_ANIMALS", Goal.GoalType.HUNT_ANIMALS);
        goalMap.put("🌾 FARM CROPS", Goal.GoalType.FARM_CROPS);
        goalMap.put("FARM_CROPS", Goal.GoalType.FARM_CROPS);
        goalMap.put("🗺️ EXPLORE AREA", Goal.GoalType.EXPLORE_AREA);
        goalMap.put("EXPLORE_AREA", Goal.GoalType.EXPLORE_AREA);
        goalMap.put("🏠 BUILD STRUCTURE", Goal.GoalType.BUILD_STRUCTURE);
        goalMap.put("BUILD_STRUCTURE", Goal.GoalType.BUILD_STRUCTURE);
        goalMap.put("👥 FOLLOW PLAYER", Goal.GoalType.FOLLOW_PLAYER);
        goalMap.put("🎯 FOLLOW PLAYER", Goal.GoalType.FOLLOW_PLAYER);
        goalMap.put("FOLLOW_PLAYER", Goal.GoalType.FOLLOW_PLAYER);
    }

    /**
     * Expose telemetry for other components
     */
    public static TelemetryClient getTelemetry() {
        return telemetry;
    }

    @Override
    public void onDisable() {
        // Stop command server
        if (commandServer != null) {
            commandServer.shutdown();
            getLogger().info("🛑 Command server stopped");
        }
        
        // Disconnect telemetry
        if (telemetry != null) {
            telemetry.disconnect();
            getLogger().info("🛑 Telemetry disconnected");
        }
        
        // Stop AI brain loop
        if (aiBrainLoop != null) {
            aiBrainLoop.cancel();
            getLogger().info("🛑 AI Brain Loop stopped");
        }
        
        // Stop brain loop cleanly
        if (brainLoop != null && brainLoop.isRunning()) {
            brainLoop.stop();
            getLogger().info("🛑 Brain loop stopped");
        }
        
        getLogger().info("🤖 FreddyAI disabled");
    }


    public static NPC getFreddy() {
        return freddy;
    }
    
    public static AgentBrain getBrain() {
        return brain;
    }
    
    public static BrainLoop getBrainLoop() {
        return brainLoop;
    }
}

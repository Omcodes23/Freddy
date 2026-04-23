package com.freddy.plugin;

import com.freddy.ai.AgentBrain;
import com.freddy.plugin.brain.BrainLoop;
import com.freddy.plugin.brain.AIBrainLoop;
import com.freddy.plugin.ai.FreddyCraftRequest;
import com.freddy.plugin.ai.FreddyCraftResult;
import com.freddy.plugin.ai.FreddyCraftingService;
import com.freddy.plugin.commands.CommandServer;
import com.freddy.plugin.commands.DevToolsCommand;
import com.freddy.plugin.listener.PlayerChatListener;
import com.freddy.plugin.actions.GameActions;
import com.freddy.plugin.npc.FreddyMovement;
import com.freddy.plugin.npc.Goal;
import com.freddy.plugin.npc.GoalStep;
import com.freddy.plugin.npc.StepPlanner;
import com.freddy.plugin.perception.AIPerception;
import com.freddy.common.TelemetryClient;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.StreamSupport;

public class FreddyPlugin extends JavaPlugin {

    private static FreddyPlugin instance;
    private static NPC freddy;
    private static AgentBrain brain;
    private static BrainLoop brainLoop;
    private static AIBrainLoop aiBrainLoop;
    private static CommandServer commandServer;
    private static TelemetryClient telemetry;
    private static FreddyCraftingService craftingService;
    private static final String NPC_NAME = "Freddy";
    private static Map<String, Goal.GoalType> goalMap = new HashMap<>();
    private static String currentGoalLabel = "none";
    private static String lastGoalCommandRaw = "";
    private static long lastGoalCommandAt = 0L;
    private static final long GOAL_COMMAND_DEBOUNCE_MS = 900L;
    private static long lastGoalSetAt = 0L;
    private static final long GOAL_CLEAR_PROTECTION_MS = 5000L;
    private static long lastGoalClearRequestedAt = 0L;
    private static final long GOAL_CLEAR_CONFIRM_WINDOW_MS = 1000L;
    private static volatile long lastDashboardCommandAt = 0L;
    private static final long DASHBOARD_CONTROL_TTL_MS = 10L * 60_000L;

    public static boolean isDashboardControlActive() {
        long last = lastDashboardCommandAt;
        return last > 0L && (System.currentTimeMillis() - last) < DASHBOARD_CONTROL_TTL_MS;
    }

    // Peer feature compatibility systems
    private FreddyMovement freddyMovement;
    private com.freddy.plugin.ai.FreddyPerception freddyPerception;
    private com.freddy.plugin.ai.FreddyInventory freddyInventory;
    private com.freddy.plugin.ai.FreddyWorkflowSafety freddyWorkflowSafety;
    private com.freddy.plugin.ai.FreddyWorkflowExecutor freddyWorkflowExecutor;
    private com.freddy.plugin.ai.FreddyPlanner freddyPlanner;
    private com.freddy.plugin.ai.crafting.PrimitiveResolver primitiveResolver;
    private com.freddy.plugin.ai.crafting.GatheringPlanner gatheringPlanner;
    private com.freddy.plugin.ai.crafting.AutoCrafter autoCrafter;
    private com.freddy.plugin.ai.planning.DeterministicPlanner deterministicPlannerPeer;
    private com.freddy.plugin.ai.goal.GoalManager goalManagerPeer;
    private com.freddy.plugin.ai.reactive.ReactiveGoalGenerator reactiveGoalGeneratorPeer;

    /**
     * Ensure the AI brain loop is initialized once the Citizens NPC is available/spawned.
     */
    private static synchronized boolean ensureAIBrainLoopInitialized() {
        if (aiBrainLoop != null) {
            return true;
        }

        try {
            NPC npc = StreamSupport.stream(
                    CitizensAPI.getNPCRegistry().spliterator(),
                    false
            ).filter(n -> n.getName().equalsIgnoreCase(NPC_NAME)).findFirst().orElse(null);

            if (npc == null) {
                Bukkit.getLogger().warning("[AI] Citizens NPC '" + NPC_NAME + "' not found.");
                return false;
            }

            if (npc.getEntity() == null) {
                try {
                    npc.spawn(Bukkit.getWorlds().get(0).getSpawnLocation());
                    Bukkit.getLogger().info("[AI] Spawned NPC '" + NPC_NAME + "' for AI initialization.");
                } catch (Exception e) {
                    Bukkit.getLogger().severe("[AI] Failed to spawn NPC '" + NPC_NAME + "': " + e.getMessage());
                    return false;
                }
            }

            if (!(npc.getEntity() instanceof Player)) {
                Bukkit.getLogger().warning("[AI] NPC '" + NPC_NAME + "' is not a player entity; AI cannot control it.");
                return false;
            }

            aiBrainLoop = new AIBrainLoop(NPC_NAME);
            aiBrainLoop.start();
            Bukkit.getLogger().info("[AI] Brain loop initialized lazily for NPC '" + NPC_NAME + "'.");
            return true;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[AI] Error ensuring brain loop: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("[FreddyAI] Plugin enabling...");

        // Load config.yml
        saveDefaultConfig();
        reloadConfig();

        // Configure LLM from config
        String llmModel = getConfig().getString("llm.model", "qwen2.5:3b");
        String llmUrl = getConfig().getString("llm.url", "http://localhost:11434/api/generate");
        int llmTimeout = getConfig().getInt("llm.timeout-ms", 12000);
        com.freddy.llm.LLMClient.configure(llmModel, llmUrl, llmTimeout);
        getLogger().info("[FreddyAI] LLM configured: model=" + llmModel + ", timeout=" + llmTimeout + "ms");

        // Create brain first
        brain = new AgentBrain("Freddy");
        getLogger().info("[FreddyAI] AgentBrain initialized");

        // Initialize goal map
        initializeGoalMap();

        // Advanced modules are part of the default runtime path.
        getLogger().info("[FreddyAI] Advanced features enabled");
        
        // Initialize telemetry for dashboard
        String telemetryHost = getConfig().getString("telemetry.host", "localhost");
        int telemetryPort = getConfig().getInt("telemetry.port", 25566);
        telemetry = new TelemetryClient(telemetryHost, telemetryPort);
        if (telemetry.connect()) {
            getLogger().info("[FreddyAI] Connected to dashboard on port " + telemetryPort);
        } else {
            getLogger().info("[FreddyAI] Dashboard not available (optional)");
        }

        // Start command server for dashboard commands
        commandServer = new CommandServer();
        commandServer.start();
        getLogger().info("🌐 Command server started on port 25567");

        getServer().getPluginManager()
                .registerEvents(new PlayerChatListener(), this);

        DevToolsCommand devToolsCommand = new DevToolsCommand(this);
        if (getCommand("devtools") != null) {
            getCommand("devtools").setExecutor(devToolsCommand);
            getCommand("devtools").setTabCompleter(devToolsCommand);
            getLogger().info("🛠️ DevTools command bridge enabled: /devtools");
        } else {
            getLogger().warning("⚠️ Command 'devtools' is not defined in plugin.yml");
        }

        if (getCommand("freddy-observe") != null) {
            getCommand("freddy-observe").setExecutor(new com.freddy.plugin.command.FreddyPerceptionCommand(this));
        }
        if (getCommand("freddy-craft") != null) {
            getCommand("freddy-craft").setExecutor(new com.freddy.plugin.command.FreddyCraftCommand(this));
        }
        if (getCommand("freddy-inv") != null) {
            getCommand("freddy-inv").setExecutor(new com.freddy.plugin.command.FreddyInventoryCommand(this));
        }
        if (getCommand("freddy-mine") != null) {
            getCommand("freddy-mine").setExecutor(new com.freddy.plugin.command.FreddyMineCommand(this));
        }
        if (getCommand("freddy-goal") != null) {
            getCommand("freddy-goal").setExecutor(new com.freddy.plugin.command.FreddyGoalCommand(this));
        }

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

            // Ensure the NPC is spawned in the world
            if (freddy.getEntity() == null) {
                try {
                    freddy.spawn(Bukkit.getWorlds().get(0).getSpawnLocation());
                    getLogger().info("✅ Spawned Freddy at world spawn");
                } catch (Exception e) {
                    getLogger().severe("❌ Failed to spawn Freddy NPC: " + e.getMessage());
                }
            }
            
            // INITIALIZE AUTONOMOUS AI BRAIN LOOP
            initializeAIBrainLoop();

            if (aiBrainLoop != null && aiBrainLoop.getNpcController() != null) {
                craftingService = new FreddyCraftingService(aiBrainLoop.getNpcController().getInventory());
            }
            initializePeerCompatibilitySystems();
            
            // Keep only one active autonomous loop to avoid command conflicts.
            // Legacy BrainLoop (LLM chatter loop) is disabled for dashboard-controlled mode.
            if (brainLoop != null && brainLoop.isRunning()) {
                brainLoop.stop();
            }

            // Initialize ChatSystem for conversational interaction
            try {
                new com.freddy.plugin.chat.ChatSystem(freddy, this);
                getLogger().info("[FreddyAI] ChatSystem initialized");
            } catch (Exception e) {
                getLogger().warning("[FreddyAI] ChatSystem failed to initialize: " + e.getMessage());
            }

            getLogger().info("[FreddyAI] Freddy is now autonomous via AIBrainLoop");
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

    private void initializePeerCompatibilitySystems() {
        try {
            if (freddy == null) {
                return;
            }

            freddyMovement = new FreddyMovement(freddy, this);
            freddyPerception = new com.freddy.plugin.ai.FreddyPerception(freddy);
            if (freddyInventory == null) {
                freddyInventory = new com.freddy.plugin.ai.FreddyInventory();
            }
            freddyWorkflowSafety = new com.freddy.plugin.ai.FreddyWorkflowSafety();
            freddyWorkflowExecutor = new com.freddy.plugin.ai.FreddyWorkflowExecutor(this);
            freddyPlanner = new com.freddy.plugin.ai.FreddyPlanner(this);

            primitiveResolver = new com.freddy.plugin.ai.crafting.PrimitiveResolver();
            gatheringPlanner = new com.freddy.plugin.ai.crafting.GatheringPlanner(this);

            com.freddy.plugin.ai.FreddyCraftingService peerCraftService = getFreddyCraftingService();
            autoCrafter = new com.freddy.plugin.ai.crafting.AutoCrafter(this, peerCraftService);
            com.freddy.plugin.ai.crafting.WeaponToolCraftingModule weaponToolModule =
                new com.freddy.plugin.ai.crafting.WeaponToolCraftingModule(this, freddyInventory, autoCrafter);
            autoCrafter.setWeaponToolModule(weaponToolModule);

            deterministicPlannerPeer = new com.freddy.plugin.ai.planning.DeterministicPlanner(this);
            goalManagerPeer = new com.freddy.plugin.ai.goal.GoalManager(this, deterministicPlannerPeer);
            reactiveGoalGeneratorPeer = new com.freddy.plugin.ai.reactive.ReactiveGoalGenerator(this, goalManagerPeer);
            getLogger().info("✅ Peer compatibility systems initialized");
        } catch (Exception e) {
            getLogger().warning("⚠️ Failed to initialize peer compatibility systems: " + e.getMessage());
        }
    }
    
    /**
     * Set goal for AI from dashboard
     */
    public static void setAIGoal(String goalName) {
        ParsedGoalPayload payload = parseGoalPayload(goalName);

        // Resolve goal type (accept exact UI labels and plain names)
        Goal.GoalType goalType = goalMap.getOrDefault(payload.goalToken.toUpperCase(Locale.ROOT), Goal.GoalType.EXPLORE_AREA);
        Bukkit.getLogger().info("📌 AI Goal: " + goalType + " - " + payload.rawGoal);

        // Ensure brain loop exists
        if (!ensureAIBrainLoopInitialized()) {
            Bukkit.getLogger().warning("⚠️ AI Brain Loop not initialized; cannot set goal.");
            if (telemetry != null) {
                telemetry.send("ERROR:AI Brain not initialized. Ensure Citizens NPC '" + NPC_NAME + "' exists and is spawned.");
            }
            return;
        }

        List<GoalStep> steps;
        // Always include amount in the goal description so step executors can infer quantities
        // even when a step label doesn't contain digits (e.g., "Verify ... in inventory").
        String goalDescription = "Goal: " + payload.goalToken + ":" + payload.craftAmount;
        if (goalType == Goal.GoalType.BUILD_STRUCTURE && payload.buildTemplate != null && !payload.buildTemplate.isBlank()) {
            steps = StepPlanner.planBuildTemplate(payload.buildTemplate);
            goalDescription = "Goal: BUILD_STRUCTURE:" + payload.buildTemplate;
        } else if (goalType == Goal.GoalType.FIGHT_MOB && payload.fightMob != null && !payload.fightMob.isBlank()) {
            steps = StepPlanner.planFor(goalType, 1);
            goalDescription = "Goal: FIGHT_MOB:" + payload.fightMob;
        } else if (goalType == Goal.GoalType.CREATE_ITEM && payload.craftItem != null && !payload.craftItem.isBlank()) {
            steps = StepPlanner.planCreateItemDeterministic(payload.craftItem, payload.craftAmount);
            goalDescription = "Goal: CREATE_ITEM:" + payload.craftItem + ":" + payload.craftAmount;
        } else {
            steps = StepPlanner.planFor(goalType, payload.craftAmount);
        }
        Bukkit.getLogger().info("   📋 Generated " + steps.size() + " concrete steps for goal");

        StringBuilder stepsJson = new StringBuilder();
        stepsJson.append("[");
        for (int i = 0; i < steps.size(); i++) {
            stepsJson.append(steps.get(i).toJson());
            if (i < steps.size() - 1) stepsJson.append(",");
        }
        stepsJson.append("]");
        String json = stepsJson.toString();

        if (telemetry != null) {
            telemetry.send("GOAL:" + payload.rawGoal);
            telemetry.send("GOAL_STEPS:" + json);
            Bukkit.getLogger().info("   📡 Sent steps to dashboard");
        }

        currentGoalLabel = payload.rawGoal;
        setAICreativeAssist(true);
        aiBrainLoop.clearGoals();
        if (aiBrainLoop.getNpcController() != null) {
            aiBrainLoop.getNpcController().clearActionQueue();
        }
        aiBrainLoop.setGoalWithSteps(goalType, goalDescription, steps);
        lastGoalSetAt = System.currentTimeMillis();
    }

    private static void setAIGoalFromPrompt(String goalPrompt) {
        setAIGoalFromPromptAsync(goalPrompt);
    }

    private static void setAIGoalFromPromptAsync(String goalPrompt) {
        if (!ensureAIBrainLoopInitialized()) {
            sendErrorTelemetry("AI Brain not initialized. Ensure Freddy exists and is spawned.");
            return;
        }

        String normalized = goalPrompt == null ? "" : goalPrompt.trim();
        if (normalized.isEmpty()) {
            sendErrorTelemetry("Goal prompt is empty.");
            return;
        }

        if (telemetry != null) {
            telemetry.send("GOAL:Custom - " + normalized);
            telemetry.send("ACTION:PROCESSING CUSTOM GOAL");
        }

        if (instance == null) {
            applyPromptGoal(normalized, StepPlanner.planFromPrompt(normalized));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(instance, () -> {
            List<GoalStep> steps = StepPlanner.planFromPrompt(normalized);
            Bukkit.getScheduler().runTask(instance, () -> applyPromptGoal(normalized, steps));
        });
    }

    private static void applyPromptGoal(String normalizedPrompt, List<GoalStep> plannedSteps) {
        Goal.GoalType goalType = inferGoalTypeFromPrompt(normalizedPrompt);
        List<GoalStep> steps = (plannedSteps == null || plannedSteps.isEmpty())
            ? StepPlanner.planFor(goalType, 1)
            : plannedSteps;

        StringBuilder stepsJson = new StringBuilder();
        stepsJson.append("[");
        for (int i = 0; i < steps.size(); i++) {
            stepsJson.append(steps.get(i).toJson());
            if (i < steps.size() - 1) stepsJson.append(",");
        }
        stepsJson.append("]");

        if (telemetry != null) {
            telemetry.send("GOAL:Custom - " + normalizedPrompt);
            telemetry.send("GOAL_STEPS:" + stepsJson);
        }

        currentGoalLabel = "Custom - " + normalizedPrompt;
        setAICreativeAssist(true);
        aiBrainLoop.clearGoals();
        if (aiBrainLoop.getNpcController() != null) {
            aiBrainLoop.getNpcController().clearActionQueue();
        }
        aiBrainLoop.setGoalWithSteps(goalType, "Goal: " + normalizedPrompt, steps);
        lastGoalSetAt = System.currentTimeMillis();
    }

    private static Goal.GoalType inferGoalTypeFromPrompt(String prompt) {
        String p = prompt == null ? "" : prompt.toLowerCase();

        if (p.contains("ender dragon") || p.contains("end dragon") || p.contains("beat the game") || p.contains("fight dragon") || p.contains("kill dragon")) return Goal.GoalType.FIGHT_MOB;
        if (p.contains("protect") && p.contains("player")) return Goal.GoalType.PROTECT_PLAYER;
        if ((p.contains("come back") || p.contains("come here") || p.contains("come to me") || p.contains("return to player"))
            && (p.contains("player") || p.contains("me"))) {
            return Goal.GoalType.RETURN_TO_PLAYER;
        }
        if (p.contains("build") || p.contains("house") || p.contains("structure") || p.contains("base") || p.contains("shelter")) return Goal.GoalType.BUILD_STRUCTURE;
        if (p.contains("craft") || p.contains("create") || p.contains("make")) return Goal.GoalType.CREATE_ITEM;
        if (p.contains("follow")) return Goal.GoalType.FOLLOW_PLAYER;
        if (p.contains("diamond")) return Goal.GoalType.MINE_DIAMONDS;
        if (p.contains("stone") || p.contains("cobble")) return Goal.GoalType.GATHER_STONE;
        if (p.contains("farm") || p.contains("crop") || p.contains("wheat")) return Goal.GoalType.FARM_CROPS;
        if (p.contains("hunt") || p.contains("animal") || p.contains("food")) return Goal.GoalType.HUNT_ANIMALS;
        if (p.contains("wood") || p.contains("log") || p.contains("tree") || p.contains("sword") || p.contains("pickaxe")) {
            return Goal.GoalType.GATHER_WOOD;
        }
        return Goal.GoalType.EXPLORE_AREA;
    }

    private static ParsedGoalPayload parseGoalPayload(String rawGoal) {
        String normalized = rawGoal == null ? "" : rawGoal.trim();
        if (normalized.isBlank()) {
            return new ParsedGoalPayload("EXPLORE_AREA", "EXPLORE_AREA", null, null, null, 1);
        }

        String[] parts = normalized.split(":");
        String token = parts[0].trim();
        String buildTemplate = null;
        String craftItem = null;
        String fightMob = null;
        int goalAmount = 1;

        if (token.equalsIgnoreCase("BUILD_STRUCTURE") && parts.length >= 2) {
            buildTemplate = parts[1].trim().toUpperCase(Locale.ROOT);
        }

        if (token.equalsIgnoreCase("CREATE_ITEM") && parts.length >= 2) {
            craftItem = parts[1].trim().toUpperCase(Locale.ROOT);
            if (parts.length >= 3) {
                try {
                    goalAmount = Math.max(1, Integer.parseInt(parts[2].trim()));
                } catch (NumberFormatException ignore) {
                    goalAmount = 1;
                }
            }
        } else if (token.equalsIgnoreCase("FIGHT_MOB") && parts.length >= 2) {
            fightMob = parts[1].trim().toUpperCase(Locale.ROOT);
        } else if (parts.length >= 2) {
            try {
                goalAmount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignore) {
                goalAmount = 1;
            }
        }

        return new ParsedGoalPayload(normalized, token, buildTemplate, craftItem, fightMob, goalAmount);
    }

    private static class ParsedGoalPayload {
        private final String rawGoal;
        private final String goalToken;
        private final String buildTemplate;
        private final String craftItem;
        private final String fightMob;
        private final int craftAmount;

        private ParsedGoalPayload(String rawGoal, String goalToken, String buildTemplate, String craftItem, String fightMob, int craftAmount) {
            this.rawGoal = rawGoal;
            this.goalToken = goalToken;
            this.buildTemplate = buildTemplate;
            this.craftItem = craftItem;
            this.fightMob = fightMob;
            this.craftAmount = craftAmount;
        }
    }

    private static void clearAIGoal() {
        if (aiBrainLoop != null) {
            aiBrainLoop.clearGoals();
            if (aiBrainLoop.getNpcController() != null) {
                aiBrainLoop.getNpcController().clearActionQueue();
            }
        }
        setAICreativeAssist(true);
        currentGoalLabel = "none";
        if (telemetry != null) {
            telemetry.send("GOAL:none");
        }
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
        goalMap.put("↩️ RETURN TO PLAYER", Goal.GoalType.RETURN_TO_PLAYER);
        goalMap.put("RETURN_TO_PLAYER", Goal.GoalType.RETURN_TO_PLAYER);
        goalMap.put("COME_TO_PLAYER", Goal.GoalType.RETURN_TO_PLAYER);
        goalMap.put("COME_BACK_TO_PLAYER", Goal.GoalType.RETURN_TO_PLAYER);
        goalMap.put("🛡️ PROTECT PLAYER", Goal.GoalType.PROTECT_PLAYER);
        goalMap.put("PROTECT_PLAYER", Goal.GoalType.PROTECT_PLAYER);
        goalMap.put("🛠️ CRAFT ITEM", Goal.GoalType.CREATE_ITEM);
        goalMap.put("🧰 CREATE ITEM", Goal.GoalType.CREATE_ITEM);
        goalMap.put("CREATE_ITEM", Goal.GoalType.CREATE_ITEM);
        goalMap.put("🤖 AUTOPILOT", Goal.GoalType.AUTOPILOT);
        goalMap.put("AUTOPILOT", Goal.GoalType.AUTOPILOT);
        goalMap.put("🐉 FIGHT MOB", Goal.GoalType.FIGHT_MOB);
        goalMap.put("FIGHT_MOB", Goal.GoalType.FIGHT_MOB);
        goalMap.put("🚀 SPEEDRUN", Goal.GoalType.SPEEDRUN);
        goalMap.put("SPEEDRUN", Goal.GoalType.SPEEDRUN);
    }

    /**
     * Expose telemetry for other components
     */
    public static TelemetryClient getTelemetry() {
        return telemetry;
    }

    public static String getCurrentGoalLabel() {
        return currentGoalLabel == null || currentGoalLabel.isBlank() ? "none" : currentGoalLabel;
    }

    public static void handleDashboardCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return;
        }

        lastDashboardCommandAt = System.currentTimeMillis();

        if (brainLoop != null && brainLoop.isRunning()) {
            brainLoop.stop();
            Bukkit.getLogger().info("[AI] Stopped legacy BrainLoop to avoid action conflicts.");
        }

        if (!ensureAIBrainLoopInitialized()) {
            Bukkit.getLogger().warning("[AI] Dashboard command ignored; Freddy is not ready.");
            return;
        }

        String command = rawCommand.trim();

        if ((command.equalsIgnoreCase("GOAL_CLEAR") || command.equalsIgnoreCase("GOAL_CLEAR_FORCE") || command.startsWith("GOAL_TEXT:"))
            && isDuplicateGoalCommand(command)) {
            Bukkit.getLogger().info("[AI] Ignored duplicate goal command: " + command);
            return;
        }

        if (command.startsWith("ACTION:")) {
            handleActionCommand(command.substring(7).trim());
            return;
        }

        if (command.startsWith("CRAFT:")) {
            handleCraftCommand(command.substring(6).trim());
            return;
        }

        if (command.equalsIgnoreCase("OBSERVE")) {
            handleObserveCommand();
            return;
        }

        if (command.equalsIgnoreCase("RETURN_HOME")) {
            handleReturnHome();
            return;
        }

        if (command.startsWith("GOAL:")) {
            setAIGoal(command.substring(5).trim());
            lastGoalClearRequestedAt = 0L;
            return;
        }

        if (command.startsWith("GOAL_TEXT:")) {
            setAIGoalFromPromptAsync(command.substring(10).trim());
            lastGoalClearRequestedAt = 0L;
            return;
        }

        if (command.equalsIgnoreCase("GOAL_CLEAR_FORCE")) {
            if (shouldProtectActiveGoalFromClear()) {
                return;
            }
            lastGoalClearRequestedAt = 0L;
            clearAIGoal();
            return;
        }

        if (command.equalsIgnoreCase("GOAL_CLEAR")) {
            if (!isClearConfirmedForActiveGoal()) {
                return;
            }
            if (shouldProtectActiveGoalFromClear()) {
                return;
            }
            clearAIGoal();
            return;
        }

        Bukkit.getLogger().warning("[AI] Unknown dashboard command: " + command);
    }

    private static boolean isDuplicateGoalCommand(String command) {
        long now = System.currentTimeMillis();
        boolean duplicate = command.equals(lastGoalCommandRaw) && (now - lastGoalCommandAt) < GOAL_COMMAND_DEBOUNCE_MS;
        lastGoalCommandRaw = command;
        lastGoalCommandAt = now;
        return duplicate;
    }

    private static boolean shouldProtectActiveGoalFromClear() {
        if (aiBrainLoop == null) {
            return false;
        }

        Goal active = aiBrainLoop.getCurrentGoal();
        if (active == null || active.getStatus() != Goal.GoalStatus.IN_PROGRESS) {
            return false;
        }

        long elapsedSinceSet = System.currentTimeMillis() - lastGoalSetAt;
        if (elapsedSinceSet < GOAL_CLEAR_PROTECTION_MS) {
            Bukkit.getLogger().warning("[AI] Ignored GOAL_CLEAR during goal activation window (" + elapsedSinceSet + "ms).");
            return true;
        }

        return false;
    }

    private static boolean isClearConfirmedForActiveGoal() {
        if (aiBrainLoop == null) {
            return true;
        }

        Goal active = aiBrainLoop.getCurrentGoal();
        if (active == null || active.getStatus() != Goal.GoalStatus.IN_PROGRESS) {
            // No active goal -> allow clear.
            lastGoalClearRequestedAt = 0L;
            return true;
        }

        long now = System.currentTimeMillis();
        if (lastGoalClearRequestedAt > 0L && (now - lastGoalClearRequestedAt) <= GOAL_CLEAR_CONFIRM_WINDOW_MS) {
            lastGoalClearRequestedAt = 0L;
            return true;
        }

        lastGoalClearRequestedAt = now;
        Bukkit.getLogger().warning("[AI] Ignored GOAL_CLEAR for in-progress goal; send again within "
            + GOAL_CLEAR_CONFIRM_WINDOW_MS + "ms to confirm.");
        return false;
    }

    private static void handleActionCommand(String payload) {
        if (freddy == null || freddy.getEntity() == null) {
            return;
        }

        String[] parts = payload.split(":", 3);
        String action = parts[0].trim().toUpperCase();
        String target = parts.length > 1 ? parts[1].trim() : "";
        String extra = parts.length > 2 ? parts[2].trim() : "";
        String primaryArg = !target.isBlank() ? target : extra;
        String secondaryArg = !target.isBlank() && !extra.isBlank() ? extra : "";

        if (action.equals("FOLLOW")) {
            String playerName = target.isBlank() ? extra : target;
            if (playerName.isBlank()) {
                sendErrorTelemetry("FOLLOW requires player name");
                return;
            }

            GoalStep s1 = new GoalStep("Find player " + playerName);
            GoalStep s2 = new GoalStep("Follow player " + playerName + " continuously");
            s2.addDependency(s1.getId());
            aiBrainLoop.clearGoals();
            if (aiBrainLoop.getNpcController() != null) {
                aiBrainLoop.getNpcController().clearActionQueue();
            }
            aiBrainLoop.setGoalWithSteps(Goal.GoalType.FOLLOW_PLAYER, "Follow player " + playerName, List.of(s1, s2));
            currentGoalLabel = "FOLLOW_PLAYER:" + playerName;
            lastGoalSetAt = System.currentTimeMillis();
            sendActionTelemetry("GOAL FOLLOW " + playerName);
            return;
        }

        if (action.equals("PROTECT")) {
            String playerName = target.isBlank() ? extra : target;
            if (playerName.isBlank()) {
                sendErrorTelemetry("PROTECT requires player name");
                return;
            }

            GoalStep s1 = new GoalStep("Find player " + playerName);
            GoalStep s2 = new GoalStep("Stay near player " + playerName);
            GoalStep s3 = new GoalStep("Attack nearby hostile mobs around " + playerName);
            s2.addDependency(s1.getId());
            s3.addDependency(s2.getId());
            aiBrainLoop.clearGoals();
            if (aiBrainLoop.getNpcController() != null) {
                aiBrainLoop.getNpcController().clearActionQueue();
            }
            aiBrainLoop.setGoalWithSteps(Goal.GoalType.PROTECT_PLAYER, "Protect player " + playerName, List.of(s1, s2, s3));
            currentGoalLabel = "PROTECT_PLAYER:" + playerName;
            lastGoalSetAt = System.currentTimeMillis();
            sendActionTelemetry("GOAL PROTECT " + playerName);
            return;
        }

        if (action.equals("CREATE") || action.equals("CRAFT_ITEM")) {
            String itemName = target.isBlank() ? extra : target;
            if (itemName.isBlank()) {
                sendErrorTelemetry("CREATE requires item name");
                return;
            }
            setAIGoalFromPromptAsync("craft " + itemName.toLowerCase(Locale.ROOT));
            sendActionTelemetry("GOAL CREATE " + itemName);
            return;
        }

        if (aiBrainLoop != null) {
            aiBrainLoop.clearGoals();
            aiBrainLoop.pauseAutonomyTicks(240); // ~12 seconds manual control window
        }
        setAICreativeAssist(false);
        currentGoalLabel = "manual-direct-action";
        if (telemetry != null) {
            telemetry.send("GOAL:manual-direct-action");
        }

        GameActions actions = new GameActions(freddy, null);

        switch (action) {
            case "WANDER" -> {
                actions.wander();
                sendActionTelemetry("WANDER");
            }
            case "IDLE" -> {
                actions.idle();
                sendActionTelemetry("IDLE");
            }
            case "JUMP" -> {
                actions.jump();
                sendActionTelemetry("JUMP");
            }
            case "LOOK_AT", "TURN_TO" -> {
                if (!primaryArg.isBlank()) {
                    Player player = Bukkit.getPlayer(primaryArg);
                    if (player != null) {
                        actions.turnTowards(player);
                        sendActionTelemetry("LOOK_AT " + player.getName());
                    } else {
                        org.bukkit.Location location = parseLocation(primaryArg, freddy.getEntity().getLocation());
                        if (location != null) {
                            actions.lookAt(location);
                            sendActionTelemetry("LOOK_AT " + formatLocation(location));
                        } else {
                            sendErrorTelemetry("LOOK_AT target not found: " + primaryArg);
                        }
                    }
                } else {
                    sendErrorTelemetry("LOOK_AT requires player name or coordinates");
                }
            }
            case "MOVE_TO", "WALK_TO" -> {
                org.bukkit.Location location = parseLocation(primaryArg, freddy.getEntity().getLocation());
                if (location != null) {
                    actions.walkTo(location);
                    sendActionTelemetry("WALK_TO " + formatLocation(location));
                } else {
                    sendErrorTelemetry("WALK_TO target not valid: " + primaryArg);
                }
            }
            case "MINE_NEARBY" -> {
                actions.mineNearby();
                sendActionTelemetry("MINE_NEARBY");
            }
            case "BREAK_BLOCK" -> {
                org.bukkit.Location location = parseLocation(primaryArg, freddy.getEntity().getLocation());
                if (location != null) {
                    actions.breakBlockAt(location);
                    sendActionTelemetry("BREAK_BLOCK " + formatLocation(location));
                } else {
                    actions.mineNearby();
                    sendActionTelemetry("BREAK_BLOCK FRONT");
                }
            }
            case "PLACE_BLOCK" -> {
                org.bukkit.Material material = parseMaterial(primaryArg, secondaryArg);
                org.bukkit.Location location = parseLocation(secondaryArg, freddy.getEntity().getLocation());
                if (material != null) {
                    if (location != null) {
                        actions.placeBlockAt(location, material);
                        sendActionTelemetry("PLACE_BLOCK " + material.name() + " " + formatLocation(location));
                    } else {
                        actions.placeBlock(material);
                        sendActionTelemetry("PLACE_BLOCK " + material.name());
                    }
                } else {
                    sendErrorTelemetry("PLACE_BLOCK material not valid: " + primaryArg);
                }
            }
            case "ATTACK_NEARBY" -> {
                actions.attackNearby();
                sendActionTelemetry("ATTACK_NEARBY");
            }
            case "SHIELD" -> {
                actions.shield();
                sendActionTelemetry("SHIELD");
            }
            case "DODGE" -> {
                actions.dodge();
                sendActionTelemetry("DODGE");
            }
            case "CHAT" -> {
                String message = primaryArg.isBlank() ? "Hello!" : primaryArg;
                actions.chat(message);
                sendActionTelemetry("CHAT");
            }
            case "SAY_HELLO" -> {
                Player player = Bukkit.getPlayer(primaryArg);
                if (player != null) {
                    actions.sayHello(player);
                    sendActionTelemetry("SAY_HELLO " + player.getName());
                } else {
                    sendErrorTelemetry("SAY_HELLO target not found: " + primaryArg);
                }
            }
            case "SAY_GOODBYE" -> {
                Player player = Bukkit.getPlayer(primaryArg);
                if (player != null) {
                    actions.sayGoodbye(player);
                    sendActionTelemetry("SAY_GOODBYE " + player.getName());
                } else {
                    sendErrorTelemetry("SAY_GOODBYE target not found: " + primaryArg);
                }
            }
            case "CONFUSED" -> {
                actions.expressConfusion();
                sendActionTelemetry("CONFUSED");
            }
            case "PICKUP_ITEMS" -> {
                aiBrainLoop.getNpcController().pickupNearbyItems();
                sendActionTelemetry("PICKUP_ITEMS");
            }
            case "OBSERVE" -> handleObserveCommand();
            case "RETURN_HOME" -> handleReturnHome();
            default -> Bukkit.getLogger().warning("[AI] Unknown action: " + action);
        }
    }

    private static org.bukkit.Location parseLocation(String raw, org.bukkit.Location fallback) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] parts = raw.trim().split("[,\\s]+");
        try {
            if (parts.length == 2) {
                double x = Double.parseDouble(parts[0]);
                double z = Double.parseDouble(parts[1]);
                double y = fallback != null ? fallback.getY() : 0.0;
                return new org.bukkit.Location(fallback != null ? fallback.getWorld() : null, x, y, z);
            }
            if (parts.length >= 3) {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                return new org.bukkit.Location(fallback != null ? fallback.getWorld() : null, x, y, z);
            }
        } catch (Exception ignore) { }
        return null;
    }

    private static org.bukkit.Material parseMaterial(String first, String second) {
        String primary = first == null ? "" : first.trim();
        String fallback = second == null ? "" : second.trim();

        if (!primary.isBlank()) {
            try {
                return org.bukkit.Material.valueOf(primary.toUpperCase(Locale.ROOT));
            } catch (Exception ignore) { }
        }

        if (fallback.isBlank()) {
            return null;
        }
        try {
            return org.bukkit.Material.valueOf(fallback.toUpperCase(Locale.ROOT));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String formatLocation(org.bukkit.Location location) {
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", location.getX(), location.getY(), location.getZ());
    }

    private static void handleCraftCommand(String payload) {
        if (aiBrainLoop != null) {
            aiBrainLoop.clearGoals();
            aiBrainLoop.pauseAutonomyTicks(160);
        }
        setAICreativeAssist(false);
        currentGoalLabel = "manual-direct-action";
        if (telemetry != null) {
            telemetry.send("GOAL:manual-direct-action");
        }

        if (craftingService == null && aiBrainLoop != null && aiBrainLoop.getNpcController() != null) {
            craftingService = new FreddyCraftingService(aiBrainLoop.getNpcController().getInventory());
        }

        if (craftingService == null) {
            sendErrorTelemetry("Crafting service not ready.");
            return;
        }

        String[] parts = payload.split(":", 2);
        String item = parts[0].trim();
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignore) { }
        }

        FreddyCraftResult result = craftingService.craft(new FreddyCraftRequest(item, amount));
        if (telemetry != null) {
            if (result.crafted) {
                telemetry.send("ACTION:CRAFTED " + result.craftedAmount + "x " + result.craftedItem);
            } else {
                telemetry.send("ERROR:CRAFTING " + result.message);
            }
        }
    }

    private static void handleObserveCommand() {
        if (freddy == null || freddy.getEntity() == null) {
            return;
        }

        if (aiBrainLoop != null) {
            aiBrainLoop.pauseAutonomyTicks(120);
        }

        Location loc = freddy.getEntity().getLocation();
        AIPerception perception = new AIPerception(loc, 50.0, loc.getYaw());
        var pov = perception.buildPOV(freddy.getEntity().getNearbyEntities(50, 50, 50));
        sendActionTelemetry("OBSERVE");

        if (telemetry != null) {
            telemetry.send("POV:" + pov.toString().replace("\n", "\\n"));
        }
    }

    private static void handleReturnHome() {
        if (freddy == null || freddy.getEntity() == null) {
            return;
        }

        if (aiBrainLoop != null) {
            aiBrainLoop.clearGoals();
            aiBrainLoop.pauseAutonomyTicks(200);
        }
        setAICreativeAssist(false);
        currentGoalLabel = "manual-direct-action";
        if (telemetry != null) {
            telemetry.send("GOAL:manual-direct-action");
        }

        Location spawn = freddy.getEntity().getWorld().getSpawnLocation();
        freddy.getNavigator().setTarget(spawn);
        sendActionTelemetry("RETURN_HOME");
    }

    private static void sendActionTelemetry(String action) {
        if (telemetry != null) {
            telemetry.send("ACTION:" + action);
        }
    }

    private static void sendErrorTelemetry(String message) {
        if (telemetry != null) {
            telemetry.send("ERROR:" + message);
        }
    }

    private static void setAICreativeAssist(boolean enabled) {
        if (aiBrainLoop != null && aiBrainLoop.getNpcController() != null) {
            aiBrainLoop.getNpcController().setCreativeMode(enabled);
        }
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

    /**
     * Expose AIBrainLoop to other components (e.g., BrainLoop coordination)
     */
    public static AIBrainLoop getAIBrainLoop() {
        return aiBrainLoop;
    }

    public static FreddyCraftingService getCraftingService() {
        return craftingService;
    }

    public FreddyMovement getFreddyMovement() {
        return freddyMovement;
    }

    public com.freddy.plugin.ai.FreddyPerception getFreddyPerception() {
        return freddyPerception;
    }

    public com.freddy.plugin.ai.FreddyInventory getFreddyInventory() {
        return freddyInventory;
    }

    public com.freddy.plugin.ai.FreddyWorkflowSafety getFreddyWorkflowSafety() {
        return freddyWorkflowSafety;
    }

    public com.freddy.plugin.ai.FreddyWorkflowExecutor getFreddyWorkflowExecutor() {
        return freddyWorkflowExecutor;
    }

    public com.freddy.plugin.ai.FreddyPlanner getFreddyPlanner() {
        return freddyPlanner;
    }

    public com.freddy.plugin.ai.FreddyCraftingService getFreddyCraftingService() {
        if (craftingService == null) {
            if (freddyInventory == null) {
                freddyInventory = new com.freddy.plugin.ai.FreddyInventory();
            }
            craftingService = new FreddyCraftingService(freddyInventory);
        }
        return craftingService;
    }

    public com.freddy.plugin.ai.crafting.PrimitiveResolver getPrimitiveResolver() {
        return primitiveResolver;
    }

    public com.freddy.plugin.ai.crafting.GatheringPlanner getGatheringPlanner() {
        return gatheringPlanner;
    }

    public com.freddy.plugin.ai.crafting.AutoCrafter getAutoCrafter() {
        return autoCrafter;
    }

    public com.freddy.plugin.ai.planning.DeterministicPlanner getDeterministicPlanner() {
        return deterministicPlannerPeer;
    }

    public com.freddy.plugin.ai.goal.GoalManager getGoalManager() {
        return goalManagerPeer;
    }

    public com.freddy.plugin.ai.reactive.ReactiveGoalGenerator getReactiveGoalGenerator() {
        return reactiveGoalGeneratorPeer;
    }
}

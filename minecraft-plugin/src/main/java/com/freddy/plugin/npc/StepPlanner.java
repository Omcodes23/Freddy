package com.freddy.plugin.npc;

import com.freddy.llm.LLMClient;
import com.freddy.plugin.advanced.DeterministicPlanner;
import org.bukkit.Material;

import java.util.*;

public class StepPlanner {
    private static final Random AUTOPILOT_RANDOM = new Random();
    private static final Deque<String> AUTOPILOT_RECENT_TASKS = new ArrayDeque<>();
    private static final int AUTOPILOT_RECENT_LIMIT = 12;
    /**
     * Fast step generation: use predefined steps for common goals.
     * Only call LLM for complex goals where extra refinement is helpful.
     */
    public static List<GoalStep> planFor(Goal.GoalType goalType, int amount) {
        if (goalType == Goal.GoalType.AUTOPILOT) {
            return planAutopilot("Autonomous free-will mode", 0, 20, false, false);
        }
        return fallbackPlan(goalType, amount);
    }
    
    public static List<GoalStep> planFor(Goal.GoalType goalType) {
        return planFor(goalType, 1);
    }

    public static List<GoalStep> planAutopilot(String context, int queuedActions, int foodLevel, boolean threatNearby, boolean hasPendingMineAction) {
        String safeContext = context == null || context.isBlank() ? "Autonomous free-will mode" : context.trim();
        String dynamicTask = nextAutopilotTaskIdea(safeContext, queuedActions, foodLevel, threatNearby, hasPendingMineAction);
        return chainSteps(List.of(
            "Observe world and evaluate free-will context: " + safeContext,
            threatNearby ? "Prioritize safety and stay alert" : "Scan for useful work or resources",
            hasPendingMineAction ? "Continue current mining task" : "Select next autonomous task",
            foodLevel < 10 ? "Recover hunger before extended activity" : "Maintain readiness for the next task",
            queuedActions > 0 ? "Execute queued action stack" : dynamicTask,
            "Re-evaluate and choose the next free-will step"
        ));
    }

    private static String nextAutopilotTaskIdea(String context, int queuedActions, int foodLevel, boolean threatNearby, boolean hasPendingMineAction) {
        String llmIdea = proposeAutopilotTaskWithLLM(context, queuedActions, foodLevel, threatNearby, hasPendingMineAction);
        if (llmIdea != null && !llmIdea.isBlank() && !isRecentAutopilotTask(llmIdea)) {
            rememberAutopilotTask(llmIdea);
            return llmIdea;
        }

        List<String> fallback = new ArrayList<>(List.of(
            "Scout terrain and map a safer route forward",
            "Gather nearby logs and prepare building stock",
            "Mine exposed stone and secure extra materials",
            "Harvest nearby crops and restock food reserves",
            "Build a small shelter marker at current area",
            "Explore a new direction and identify resource clusters",
            "Craft utility supplies and prepare for deeper mining",
            "Assist nearby player by moving into support range"
        ));
        Collections.shuffle(fallback, AUTOPILOT_RANDOM);
        for (String candidate : fallback) {
            if (!isRecentAutopilotTask(candidate)) {
                rememberAutopilotTask(candidate);
                return candidate;
            }
        }

        String forced = fallback.get(0);
        rememberAutopilotTask(forced);
        return forced;
    }

    private static String proposeAutopilotTaskWithLLM(String context, int queuedActions, int foodLevel, boolean threatNearby, boolean hasPendingMineAction) {
        try {
            String prompt = "You are generating ONE Minecraft NPC AUTOPILOT task sentence. " +
                "Return exactly one plain sentence, <= 12 words, no numbering, no quotes. " +
                "Task must be executable by movement/mining/building/gathering/crafting. " +
                "Avoid repeating generic 'explore'. Prefer concrete work. " +
                "Context: " + context + ", queuedActions=" + queuedActions + ", foodLevel=" + foodLevel
                + ", threatNearby=" + threatNearby + ", hasPendingMineAction=" + hasPendingMineAction + ".";
            String response = LLMClient.ask(prompt);
            if (response == null || response.isBlank() || response.contains("thinking too hard")) {
                return null;
            }
            String line = response.split("\\r?\\n")[0].trim().replaceAll("^[\\-\\*\\d\\.)\\s]+", "");
            if (line.length() < 8) {
                return null;
            }
            if (!line.endsWith(".")) {
                line = line + ".";
            }
            return line;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isRecentAutopilotTask(String task) {
        String normalized = normalizeAutopilotTask(task);
        for (String prior : AUTOPILOT_RECENT_TASKS) {
            if (prior.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static void rememberAutopilotTask(String task) {
        String normalized = normalizeAutopilotTask(task);
        AUTOPILOT_RECENT_TASKS.addLast(normalized);
        while (AUTOPILOT_RECENT_TASKS.size() > AUTOPILOT_RECENT_LIMIT) {
            AUTOPILOT_RECENT_TASKS.removeFirst();
        }
    }

    private static String normalizeAutopilotTask(String task) {
        return task == null ? "" : task.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").trim();
    }

    public static List<GoalStep> planBuildTemplate(String templateName) {
        String normalized = templateName == null ? "PILLAR_SMALL" : templateName.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("HOUSE_6X6") || normalized.equals("WOOD_HOUSE_6X6") || normalized.equals("HOUSE")) {
            return chainSteps(List.of(
                "Find a flat and safe build area",
                "Gather 40 logs",
                "Craft planks for building blocks",
                "Mark a 6x6 foundation",
                "Place the 6x6 floor",
                "Build 4-block-high walls",
                "Leave a 2-block door opening",
                "Build a flat roof",
                "Inspect and patch missing blocks"
            ));
        }

        if (normalized.equals("WALL_10") || normalized.equals("DEFENSE_WALL_10")) {
            return chainSteps(List.of(
                "Find a straight build line",
                "Gather 30 stone or cobblestone",
                "Mark a 10-block segment",
                "Place the first wall layer",
                "Place the second wall layer",
                "Place the top wall layer",
                "Inspect the wall and fill gaps"
            ));
        }

        if (normalized.equals("TOWER_7") || normalized.equals("WATCH_TOWER_7")) {
            return chainSteps(List.of(
                "Find a compact and safe tower spot",
                "Gather 16 cobblestone blocks",
                "Mark the center position",
                "Build vertical tower base",
                "Raise tower to 7-block height",
                "Inspect tower stability"
            ));
        }

        if (normalized.equals("FARM_PLOT_5X5") || normalized.equals("FARM_PLOT")) {
            return chainSteps(List.of(
                "Find a flat area near water for farming",
                "Gather seeds and a hoe",
                "Mark a 5x5 farm plot",
                "Till the dirt to create farmland",
                "Plant seeds in the tilled soil",
                "Inspect the farm plot"
            ));
        }

        if (normalized.equals("HUT_4X4") || normalized.equals("WOOD_HUT_4X4")) {
            return chainSteps(List.of(
                "Find a flat area for hut",
                "Gather 24 logs",
                "Craft planks for hut walls",
                "Mark a 4x4 hut base",
                "Place the floor and walls",
                "Build a compact roof",
                "Inspect and patch missing blocks"
            ));
        }

        return chainSteps(List.of(
            "Find build spot",
            "Gather 16 logs",
            "Build a 5-block pillar",
            "Inspect final build"
        ));
    }

    public static List<GoalStep> planCreateItem(String itemName, int amount) {
        String item = (itemName == null || itemName.isBlank()) ? "WOODEN_SWORD" : itemName.trim().toUpperCase(Locale.ROOT);
        int qty = Math.max(1, amount);

        if (item.equals("WOODEN_SWORD")) {
            return chainSteps(List.of(
                "Gather 2 logs",
                "Craft planks and sticks",
                "Craft " + qty + " WOODEN_SWORD",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("WOODEN_PICKAXE")) {
            return chainSteps(List.of(
                "Gather 2 logs",
                "Craft planks and sticks",
                "Craft " + qty + " WOODEN_PICKAXE",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("WOODEN_AXE")) {
            return chainSteps(List.of(
                "Gather 2 logs",
                "Craft planks and sticks",
                "Craft " + qty + " WOODEN_AXE",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("WOODEN_SHOVEL")) {
            return chainSteps(List.of(
                "Gather 2 logs",
                "Craft planks and sticks",
                "Craft " + qty + " WOODEN_SHOVEL",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("STONE_PICKAXE") || item.equals("STONE_AXE") || item.equals("STONE_SHOVEL") || item.equals("STONE_SWORD")) {
            return chainSteps(List.of(
                "Gather 2 logs for sticks",
                "Mine 3 cobblestone for stone tools",
                "Craft helper items (planks/sticks)",
                "Craft " + qty + " " + item,
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("CHEST")) {
            return chainSteps(List.of(
                "Gather 4 logs",
                "Craft 8 planks for chest",
                "Craft " + qty + " CHEST",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("FURNACE")) {
            return chainSteps(List.of(
                "Mine 8 cobblestone for furnace",
                "Craft " + qty + " FURNACE",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("CRAFTING_TABLE")) {
            return chainSteps(List.of(
                "Gather 2 logs",
                "Craft 4 planks for crafting table",
                "Craft " + qty + " CRAFTING_TABLE",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("BREAD")) {
            return chainSteps(List.of(
                "Find and harvest wheat",
                "Collect enough wheat for crafting",
                "Craft " + qty + " BREAD",
                "Confirm crafted output in inventory"
            ));
        }

        if (item.equals("TORCH")) {
            return chainSteps(List.of(
                "Gather logs for sticks",
                "Mine coal ore",
                "Craft sticks",
                "Craft " + qty + " TORCH",
                "Confirm crafted output in inventory"
            ));
        }

        return chainSteps(List.of(
            "Collect materials required for " + item,
            "Craft helper items if needed (planks/sticks/tools)",
            "Craft " + qty + " " + item,
            "Confirm crafted output in inventory"
        ));
    }

    /**
     * Additive deterministic planner path. Existing behavior remains unchanged unless this API is used.
     */
    public static List<GoalStep> planCreateItemDeterministic(String itemName, int amount) {
        // Deterministic should still be a full, concrete plan.
        // The regular planner already models: collect required items → craft helpers → craft output → verify.
        if (itemName == null || itemName.isBlank()) {
            return planCreateItem("WOODEN_SWORD", Math.max(1, amount));
        }

        String item = itemName.trim().toUpperCase(Locale.ROOT);
        int qty = Math.max(1, amount);
        return planCreateItem(item, qty);
    }

    public static List<GoalStep> planFromPrompt(String userGoalPrompt) {
        if (userGoalPrompt == null || userGoalPrompt.isBlank()) {
            return fallbackPlan(Goal.GoalType.EXPLORE_AREA, 1);
        }

        String prompt = userGoalPrompt.trim().toLowerCase(Locale.ROOT);
        if ((prompt.contains("wood") || prompt.contains("oak") || prompt.contains("log"))
            && (prompt.contains("sword") || prompt.contains("swad") || prompt.contains("blade"))) {
            return chainSteps(List.of(
                "Locate nearby trees",
                "Gather 2 logs",
                "Use inventory crafting to convert logs into planks and sticks",
                "Craft WOODEN_SWORD"
            ));
        }

        if (prompt.contains("stone") && prompt.contains("pickaxe")) {
            return chainSteps(List.of(
                "Gather wood for starter tools",
                "Mine at least 3 cobblestone",
                "Craft STONE_PICKAXE"
            ));
        }

        if (prompt.contains("protect") && prompt.contains("player")) {
            return chainSteps(List.of(
                "Locate target player",
                "Follow target player closely",
                "Attack nearby hostile mobs around player"
            ));
        }

        if ((prompt.contains("come back") || prompt.contains("return") || prompt.contains("come here") || prompt.contains("come to me"))
            && (prompt.contains("player") || prompt.contains("me"))) {
            return chainSteps(List.of(
                "Select nearest player as return target",
                "Navigate back to target player",
                "Stay within 2 blocks of player"
            ));
        }

        if (prompt.contains("build") || prompt.contains("house") || prompt.contains("structure") || prompt.contains("base") || prompt.contains("shelter")) {
            if (prompt.contains("house") || prompt.contains("home")) {
                return chainSteps(List.of(
                    "Find a flat area to build the house",
                    "Gather 40 logs",
                    "Craft planks from logs",
                    "Mark a 6x6 base",
                    "Place the full 6x6 floor",
                    "Build walls up to 4 blocks high",
                    "Leave a door opening in the front wall",
                    "Build a flat roof",
                    "Inspect and fill missing blocks"
                ));
            }

            if (prompt.contains("tower")) {
                return planBuildTemplate("TOWER_7");
            }

            if (prompt.contains("farm") || prompt.contains("plot")) {
                return planBuildTemplate("FARM_PLOT_5X5");
            }

            if (prompt.contains("hut")) {
                return planBuildTemplate("HUT_4X4");
            }

            return chainSteps(List.of(
                "Choose a safe build location",
                "Gather required materials",
                "Mark the build footprint",
                "Build foundation",
                "Build main structure",
                "Inspect and refine the result"
            ));
        }

        if (prompt.contains("craft") || prompt.contains("create") || (prompt.contains("make ") && (prompt.contains("item") || prompt.contains("tool") || prompt.contains("sword") || prompt.contains("pickaxe") || prompt.contains("armor") || prompt.contains("weapon")))) {
            String inferredItem = inferItemFromPrompt(prompt);
            int inferredAmount = inferAmountFromPrompt(prompt);
            if (inferredItem != null) {
                return planCreateItem(inferredItem, inferredAmount);
            }

            List<GoalStep> llmCraftPlan = planFromPromptWithLLM(userGoalPrompt);
            if (!llmCraftPlan.isEmpty()) {
                return llmCraftPlan;
            }

            return chainSteps(List.of(
                "Identify exact item and quantity to craft",
                "Locate required raw materials",
                "Pathfind to each material source",
                "Mine/collect all required ingredients",
                "Craft helper components (planks/sticks/tools)",
                "Craft final requested item",
                "Verify crafted output count"
            ));
        }

        if (prompt.contains("ender dragon") || prompt.contains("end dragon") || prompt.contains("beat the game") || prompt.contains("fight dragon") || prompt.contains("kill dragon")) {
            return chainSteps(List.of(
                "Equip endgame gear via creative supplies",
                "Spawn Ender Dragon magically",
                "Fight and defeat the Ender Dragon instantly"
            ));
        }

        if (prompt.contains("diamond")) {
            return chainSteps(List.of(
                "Locate nearby diamond ore path",
                "Descend to Y level 12",
                "Mine deepslate tunnels to expose ores",
                "Locate diamond ore vein",
                "Mine diamond ore safely",
                "Collect dropped diamonds",
                "Return to surface with diamonds",
                "Navigate back to nearest player"
            ));
        }

        if (prompt.contains("stone") || prompt.contains("cobble")) {
            return chainSteps(List.of(
                "Find exposed stone or cave wall",
                "Walk close to the stone block",
                "Mine the stone block",
                "Collect dropped stone/cobblestone",
                "Repeat until target amount is reached"
            ));
        }

        if (prompt.contains("wood") || prompt.contains("tree") || prompt.contains("log")) {
            return chainSteps(List.of(
                "Search for nearby trees",
                "Walk to the nearest tree trunk",
                "Mine the trunk logs",
                "Collect dropped logs",
                "Repeat on additional trees until target amount"
            ));
        }

        if (prompt.contains("home") || prompt.contains("spawn") || prompt.contains("return")) {
            return chainSteps(List.of(
                "Stop current task",
                "Navigate back to home or spawn",
                "Wait at the home position"
            ));
        }

        List<GoalStep> llmPlan = planFromPromptWithLLM(userGoalPrompt);
        if (!llmPlan.isEmpty()) {
            return llmPlan;
        }

        return chainSteps(List.of(
            "Understand requested goal: " + userGoalPrompt,
            "Find best nearby place to start",
            "Collect required resources",
            "Craft or prepare required tools",
            "Execute the main objective",
            "Validate completion and stop"
        ));
    }

    private static List<GoalStep> planFromPromptWithLLM(String userGoalPrompt) {
        try {
            String llmPrompt = "You are a precise Minecraft planning assistant. " +
                "Given a user goal, produce a detailed execution plan made of strict, decisive micro-steps. " +
                "Do NOT hallucinate generic actions. Every step MUST explicitly state standard commands like: " +
                "'Locate nearby [Resource]', 'Pathfind shortest route to target coordinate', 'Mine 5 [Block]', 'Use inventory to craft [Item]', 'Place [Block]'. " +
                "Force exact sequence: locate -> pathfind shortest path -> mine/collect -> craft -> verify. " +
                "Use as many steps as needed (typically 6-14). " +
                "Return only plain lines, one step per line, no numbering, no extra commentary. Goal: " + userGoalPrompt;
            String llmResponse = LLMClient.ask(llmPrompt);
            if (llmResponse == null || llmResponse.isBlank()) {
                return List.of();
            }

            List<String> labels = new ArrayList<>();
            String[] lines = llmResponse.split("\\r?\\n");
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }

                line = line.replaceFirst("^[\\-\\*\\d\\.)\\s]+", "").trim();
                if (!line.isEmpty() && labels.size() < 20) {
                    labels.add(line);
                }
            }

            if (labels.size() < 4) {
                return List.of();
            }

            if (labels.size() < 6) {
                labels = expandToMicroSteps(labels, userGoalPrompt);
            }

            return chainSteps(labels);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<String> expandToMicroSteps(List<String> labels, String goalPrompt) {
        List<String> expanded = new ArrayList<>();
        expanded.add("Interpret goal precisely: " + goalPrompt);
        expanded.add("Locate nearby resources related to the goal");
        expanded.add("Pathfind to first required target");
        expanded.addAll(labels);
        expanded.add("Verify progress and repeat missing sub-steps if needed");
        expanded.add("Validate final completion state");

        if (expanded.size() > 20) {
            return expanded.subList(0, 20);
        }
        return expanded;
    }

    private static String inferItemFromPrompt(String prompt) {
        String p = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (p.contains("wooden sword")) return "WOODEN_SWORD";
        if (p.contains("wooden pickaxe")) return "WOODEN_PICKAXE";
        if (p.contains("wooden axe")) return "WOODEN_AXE";
        if (p.contains("wooden shovel")) return "WOODEN_SHOVEL";
        if (p.contains("stone sword")) return "STONE_SWORD";
        if (p.contains("stone pickaxe")) return "STONE_PICKAXE";
        if (p.contains("stone axe")) return "STONE_AXE";
        if (p.contains("stone shovel")) return "STONE_SHOVEL";
        if (p.contains("crafting table")) return "CRAFTING_TABLE";
        if (p.contains("furnace")) return "FURNACE";
        if (p.contains("chest")) return "CHEST";
        if (p.contains("bread")) return "BREAD";
        if (p.contains("torch")) return "TORCH";
        return null;
    }

    private static int inferAmountFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(prompt);
        if (m.find()) {
            try {
                return Math.max(1, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignore) {
                return 1;
            }
        }
        return 1;
    }

    private static List<GoalStep> chainSteps(List<String> labels) {
        List<GoalStep> steps = new ArrayList<>();
        GoalStep previous = null;
        for (String label : labels) {
            GoalStep step = new GoalStep(label);
            if (previous != null) {
                step.addDependency(previous.getId());
            }
            steps.add(step);
            previous = step;
        }
        return steps;
    }

    private static List<GoalStep> fallbackPlan(Goal.GoalType goalType, int amount) {
        int qty = Math.max(1, amount);
        switch (goalType) {
            case GATHER_WOOD: {
                return chainSteps(List.of(
                    "Locate nearby forest and tree trunks",
                    "Walk near reachable log blocks",
                    "Collect " + qty + " wood logs",
                    "Verify " + qty + " wood logs in inventory"
                ));
            }
            case GATHER_STONE: {
                return chainSteps(List.of(
                    "Find exposed stone outcrop or cave entrance",
                    "Approach nearest stone face",
                    "Mine " + qty + " stone blocks",
                    "Confirm " + qty + " stone blocks in inventory"
                ));
            }
            case MINE_DIAMONDS: {
                return chainSteps(List.of(
                    "Locate nearby diamond ore path",
                    "Descend to diamond level",
                    "Mine " + qty + " diamond ore",
                    "Return to surface with diamonds",
                    "Navigate back to nearest player"
                ));
            }
            case HUNT_ANIMALS: {
                return chainSteps(List.of(
                    "Locate nearby animals in vicinity",
                    "Hunt animals safely",
                    "Collect " + qty + " food drops",
                    "Confirm " + qty + " food drops in inventory"
                ));
            }
            case FARM_CROPS: {
                return chainSteps(List.of(
                    "Find farmable crop area",
                    "Harvest mature crops",
                    "Collect " + qty + " wheat or crops",
                    "Verify " + qty + " crops in inventory"
                ));
            }
            case EXPLORE_AREA: {
                return chainSteps(List.of(
                    "Explore area for 60 seconds",
                    "Locate notable terrain and resources",
                    "Continue explore sweep and navigation"
                ));
            }
            case BUILD_STRUCTURE: {
                return chainSteps(List.of(
                    "Gather " + qty + " wood or primary materials",
                    "Craft planks for building blocks",
                    "Find flat area and mark build footprint",
                    "Build structure layout and place blocks",
                    "Inspect structure and patch gaps"
                ));
            }
            case FOLLOW_PLAYER: {
                return chainSteps(List.of(
                    "Find nearest player",
                    "Move to player and follow continuously",
                    "Maintain follow distance near player"
                ));
            }
            case CREATE_ITEM: {
                return chainSteps(List.of(
                    "Check recipe requirements for target item",
                    "Gather required material resources",
                    "Craft target item using crafting system",
                    "Verify crafted item in inventory"
                ));
            }
            case AUTOPILOT: {
                return planAutopilot("Autonomous free-will mode", 0, 20, false, false);
            }
            case PROTECT_PLAYER: {
                return chainSteps(List.of(
                    "Find target player",
                    "Stay near target player",
                    "Attack nearby hostile mobs",
                    "Maintain protect position around player"
                ));
            }
            case RETURN_HOME: {
                return chainSteps(List.of(
                    "Stop current task queue",
                    "Return to home or spawn",
                    "Verify arrival at home position"
                ));
            }
            case RETURN_TO_PLAYER: {
                return chainSteps(List.of(
                    "Select nearest player as return target",
                    "Navigate back to target player",
                    "Stay within 2 blocks near player"
                ));
            }
            case FIGHT_MOB: {
                return chainSteps(List.of(
                    "Equip combat gear via creative supplies",
                    "Spawn target mob magically",
                    "Fight and defeat the target mob",
                    "Verify fight completion"
                ));
            }
            case SPEEDRUN: {
                return chainSteps(List.of(
                    "Locate nearby trees",
                    "Gather 10 oak logs",
                    "Find exposed stone",
                    "Mine 10 stone blocks",
                    "Find flat area and build 6x6 house",
                    "Craft a crafting table",
                    "Craft a furnace",
                    "Craft a chest",
                    "Craft a white bed",
                    "Place each furniture inside the house",
                    "Store all inventory items into the chest",
                    "Leave house for random AI generated goals"
                ));
            }
            default: {
                return chainSteps(List.of(
                    "Wander and observe environment",
                    "Locate useful nearby objective",
                    "Execute nearest practical task"
                ));
            }
        }
    }
}

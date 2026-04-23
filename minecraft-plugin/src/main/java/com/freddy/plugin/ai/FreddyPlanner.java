package com.freddy.plugin.ai;

import com.freddy.plugin.FreddyPlugin;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.Material;

import java.util.Map;

/**
 * FreddyPlanner - LLM integration for workflow generation
 * 
 * Generates structured workflow plans using LLM reasoning.
 * The LLM decides WHAT to do, but never executes directly.
 */
public class FreddyPlanner {

    private final FreddyPlugin plugin;
    private final Gson gson;

    public FreddyPlanner(FreddyPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * Create a workflow plan for a goal
     * 
     * @param goal      Goal description (e.g., "CRAFT_IRON_PICKAXE")
     * @param inventory Current inventory state
     * @return Generated workflow, or null if planning failed
     */
    public FreddyWorkflow createPlan(String goal, FreddyInventory inventory) {
        plugin.getLogger().info("Creating plan for goal: " + goal);

        // Get inventory snapshot
        Map<Material, Integer> items = inventory.getAll();

        // Check if goal requires crafting
        Material targetMaterial;
        try {
            targetMaterial = Material.valueOf(goal.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown goal material: " + goal);
            return null;
        }

        // Check missing items for crafting
        FreddyCraftingService crafting = plugin.getFreddyCraftingService();
        if (crafting == null) {
            plugin.getLogger().warning("FreddyCraftingService not initialized");
            return null;
        }

        FreddyCraftRequest request = new FreddyCraftRequest();
        request.item = targetMaterial.name();
        request.amount = 1;

        Map<String, Integer> missing = crafting.getMissingItems(request);

        // Build prompt for LLM
        String prompt = buildPrompt(goal, items, missing);

        plugin.getLogger().info("LLM Prompt:\n" + prompt);

        // TODO: Call actual LLM API here
        // For now, create a simple hardcoded plan for testing
        FreddyWorkflow workflow = createMockPlan(goal, targetMaterial, missing);

        if (workflow != null) {
            plugin.getLogger().info("Generated workflow: " + workflow);
        }

        return workflow;
    }

    /**
     * Build prompt for LLM
     */
    private String buildPrompt(String goal, Map<Material, Integer> inventory, Map<String, Integer> missing) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a Minecraft autonomous agent planner.\n\n");
        prompt.append("Goal: ").append(goal).append("\n\n");

        prompt.append("Current inventory:\n");
        if (inventory.isEmpty()) {
            prompt.append("- (empty)\n");
        } else {
            for (Map.Entry<Material, Integer> entry : inventory.entrySet()) {
                prompt.append(String.format("- %s x%d\n", entry.getKey(), entry.getValue()));
            }
        }
        prompt.append("\n");

        if (missing != null && !missing.isEmpty()) {
            prompt.append("Missing items:\n");
            for (Map.Entry<String, Integer> entry : missing.entrySet()) {
                prompt.append(String.format("- %s x%d\n", entry.getKey(), entry.getValue()));
            }
            prompt.append("\n");
        }

        prompt.append("Rules:\n");
        prompt.append("- You may output ONLY the following commands:\n");
        prompt.append("  FREDDY_MINE <BLOCK> <QTY>\n");
        prompt.append("  FREDDY_KILL <MOB> <QTY>\n");
        prompt.append("  FREDDY_CRAFT <ITEM> <QTY>\n");
        prompt.append("- Prefer mining over killing if possible\n");
        prompt.append("- Do NOT repeat commands\n");
        prompt.append("- Maximum 10 steps\n");
        prompt.append("- Commands must be in logical order\n");
        prompt.append("- Do not assume items magically exist\n");
        prompt.append("- Output JSON only\n\n");

        prompt.append("Schema:\n");
        prompt.append("{\n");
        prompt.append("  \"goal\": \"CRAFT_CRAFTING_TABLE\",\n");
        prompt.append("  \"steps\": [\n");
        prompt.append("    {\"command\": \"FREDDY_MINE\", \"target\": \"OAK_LOG\", \"quantity\": 1},\n");
        prompt.append("    {\"command\": \"FREDDY_CRAFT\", \"target\": \"OAK_PLANKS\", \"quantity\": 4},\n");
        prompt.append("    {\"command\": \"FREDDY_CRAFT\", \"target\": \"CRAFTING_TABLE\", \"quantity\": 1}\n");
        prompt.append("  ]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * Parse LLM response into workflow
     */
    public FreddyWorkflow parseWorkflow(String llmResponse) {
        try {
            // Try to extract JSON from response
            String json = llmResponse.trim();

            // If response contains markdown code blocks, extract JSON
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}") + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            FreddyWorkflow workflow = gson.fromJson(json, FreddyWorkflow.class);

            if (workflow == null || !workflow.isValid()) {
                plugin.getLogger().warning("Invalid workflow from LLM");
                return null;
            }

            return workflow;

        } catch (JsonSyntaxException e) {
            plugin.getLogger().severe("Failed to parse LLM response: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create a mock plan for testing (before LLM integration)
     */
    private FreddyWorkflow createMockPlan(String goal, Material target, Map<String, Integer> missing) {
        FreddyWorkflow workflow = new FreddyWorkflow(goal);

        // Simple mock logic for common cases
        if (target == Material.CRAFTING_TABLE) {
            // Need 4 planks
            if (missing.containsKey("OAK_PLANKS") || missing.containsKey("PLANKS")) {
                workflow.addStep(new FreddyStep("FREDDY_MINE", "OAK_LOG", 1));
                workflow.addStep(new FreddyStep("FREDDY_CRAFT", "OAK_PLANKS", 4));
            }
            workflow.addStep(new FreddyStep("FREDDY_CRAFT", "CRAFTING_TABLE", 1));
        } else if (target == Material.STICK) {
            workflow.addStep(new FreddyStep("FREDDY_MINE", "OAK_LOG", 1));
            workflow.addStep(new FreddyStep("FREDDY_CRAFT", "OAK_PLANKS", 4));
            workflow.addStep(new FreddyStep("FREDDY_CRAFT", "STICK", 4));
        } else if (target == Material.WOODEN_PICKAXE) {
            workflow.addStep(new FreddyStep("FREDDY_MINE", "OAK_LOG", 2));
            workflow.addStep(new FreddyStep("FREDDY_CRAFT", "OAK_PLANKS", 8));
            workflow.addStep(new FreddyStep("FREDDY_CRAFT", "STICK", 2));
            workflow.addStep(new FreddyStep("FREDDY_CRAFT", "WOODEN_PICKAXE", 1));
        } else {
            // Generic: just try to craft
            workflow.addStep(new FreddyStep("FREDDY_CRAFT", target.name(), 1));
        }

        return workflow;
    }
}

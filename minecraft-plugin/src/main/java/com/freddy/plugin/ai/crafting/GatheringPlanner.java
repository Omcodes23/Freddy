package com.freddy.plugin.ai.crafting;

import com.freddy.llm.LLMClient;
import com.freddy.plugin.FreddyPlugin;
import com.freddy.plugin.ai.FreddyStep;
import com.freddy.plugin.ai.FreddyWorkflow;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.Material;

import java.util.Map;

/**
 * GatheringPlanner - LLM integration for gathering workflows
 * 
 * This planner ONLY generates gathering steps (FREDDY_MINE, FREDDY_KILL).
 * It does NOT generate crafting steps - that's handled deterministically.
 * 
 * This is the ONLY place where LLM makes decisions in the craft system.
 */
public class GatheringPlanner {

    private final FreddyPlugin plugin;
    private final Gson gson;

    public GatheringPlanner(FreddyPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * Create gathering plan via LLM
     * 
     * @param goal    Goal item (for context)
     * @param missing Missing primitive materials
     * @return Gathering workflow (only MINE/KILL steps)
     */
    public FreddyWorkflow plan(Material goal, Map<Material, Integer> missing) {

        if (missing.isEmpty()) {
            plugin.getLogger().info("No missing materials - no gathering needed");
            return null;
        }

        // ==================== DETERMINISTIC FALLBACK ====================
        // For simple, obvious primitives, skip LLM entirely
        if (canUseDeterministicFallback(missing)) {
            plugin.getLogger().info("Using deterministic fallback (no LLM needed)");
            return createDeterministicGatherPlan(missing);
        }

        // ==================== LLM PLANNING ====================
        // Build constrained prompt
        String prompt = GatheringPrompt.build(goal, missing);

        plugin.getLogger().info("========== GATHERING PLANNER ==========");
        plugin.getLogger().info("Goal: " + goal.name());
        plugin.getLogger().info("Missing: " + missing);
        plugin.getLogger().info("\nLLM Prompt:\n" + prompt);

        // Call LLM
        String llmResponse = LLMClient.ask(prompt);

        plugin.getLogger().info("\nLLM Response:\n" + llmResponse);

        // Parse response safely
        FreddyWorkflow workflow = safeParseWorkflow(llmResponse);

        if (workflow != null) {
            // Validate: ensure NO crafting steps
            if (!isGatheringOnly(workflow)) {
                plugin.getLogger().warning("⚠️ LLM generated crafting steps! Rejecting workflow.");
                plugin.getLogger().warning("Workflow: " + workflow);
                return null;
            }

            // Validate: ensure ONLY requested materials
            if (!isRequestedMaterialsOnly(workflow, missing)) {
                plugin.getLogger().warning("⚠️ LLM attempted to gather unrequested materials! Rejecting workflow.");
                plugin.getLogger().warning("Workflow: " + workflow);
                return null;
            }

            plugin.getLogger().info("✓ Valid gathering workflow generated");
            plugin.getLogger().info("Steps: " + workflow.getStepCount());
        } else {
            plugin.getLogger().warning("✗ Failed to parse LLM response");
        }

        plugin.getLogger().info("======================================");

        return workflow;
    }

    /**
     * Check if we can skip LLM and use deterministic fallback
     * 
     * Use fallback for single simple primitives (logs, stone, dirt, etc.)
     */
    private boolean canUseDeterministicFallback(Map<Material, Integer> missing) {
        // Only use fallback for single primitives
        if (missing.size() != 1) {
            return false;
        }

        Material material = missing.keySet().iterator().next();

        // Simple gatherable blocks that are obvious
        return switch (material) {
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG,
                    ACACIA_LOG, DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG,
                    COBBLESTONE, STONE, DIRT, SAND, GRAVEL ->
                true;
            default -> false;
        };
    }

    /**
     * Create deterministic gathering plan for simple primitives
     */
    private FreddyWorkflow createDeterministicGatherPlan(Map<Material, Integer> missing) {
        FreddyWorkflow workflow = new FreddyWorkflow();
        workflow.goal = "GATHER";

        for (Map.Entry<Material, Integer> entry : missing.entrySet()) {
            workflow.addStep(new FreddyStep(
                    "FREDDY_MINE",
                    entry.getKey().name(),
                    entry.getValue()));
        }

        return workflow;
    }

    /**
     * Parse LLM response with validation (safe)
     */
    private FreddyWorkflow safeParseWorkflow(String llmResponse) {
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
     * Validate that workflow contains ONLY gathering steps
     * 
     * @param workflow Workflow to validate
     * @return true if only MINE/KILL steps, false if contains CRAFT
     */
    private boolean isGatheringOnly(FreddyWorkflow workflow) {
        return workflow.steps.stream().allMatch(step -> {
            String cmd = step.command.toUpperCase();
            return cmd.equals("FREDDY_MINE") || cmd.equals("FREDDY_KILL");
        });
    }

    /**
     * Validate that workflow only gathers materials from the missing set
     * 
     * CRITICAL: This prevents LLM from choosing alternative materials.
     * Example: If missing=[IRON_ORE], reject attempts to mine DIAMOND_ORE.
     * 
     * @param workflow Workflow to validate
     * @param missing  Missing primitives (allowed materials)
     * @return true if all steps target materials in missing set
     */
    private boolean isRequestedMaterialsOnly(FreddyWorkflow workflow, Map<Material, Integer> missing) {
        for (FreddyStep step : workflow.steps) {
            try {
                Material stepMaterial = Material.valueOf(step.target.toUpperCase());

                // Check if this material is in the missing set
                if (!missing.containsKey(stepMaterial)) {
                    plugin.getLogger().warning(String.format(
                            "  ✗ Step attempts to gather %s, but only %s are requested",
                            stepMaterial.name(), missing.keySet()));
                    return false;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("  ✗ Unknown material: " + step.target);
                return false;
            }
        }
        return true;
    }
}

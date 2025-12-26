package com.freddy.plugin.npc;

import com.freddy.llm.LLMClient;

import java.util.*;

public class StepPlanner {
    /**
     * Generate steps using the LLM. Falls back to a built-in plan on error.
     */
    public static List<GoalStep> planFor(Goal.GoalType goalType) {
        try {
            List<GoalStep> llmSteps = planWithLLM(goalType);
            if (!llmSteps.isEmpty()) return llmSteps;
        } catch (Exception ignore) { }
        return fallbackPlan(goalType);
    }

    private static List<GoalStep> planWithLLM(Goal.GoalType goalType) {
        String prompt = "You are planning discrete Minecraft steps for an autonomous NPC. " +
                "Return STRICT JSON array of objects with fields: label (string), dependsOn (array of labels). " +
                "No prose. Example: [{\"label\":\"Navigate to forest\",\"dependsOn\":[]},{\"label\":\"Collect 64 oak logs\",\"dependsOn\":[\"Navigate to forest\"]}]. " +
                "Goal: " + goalType.name();

        String response = LLMClient.ask(prompt);
        return parseStepsJson(response);
    }

    private static List<GoalStep> parseStepsJson(String json) {
        List<GoalStep> steps = new ArrayList<>();
        if (json == null) return steps;
        String trimmed = json.trim();
        // Extract array if wrapped in extra text
        int arrStart = trimmed.indexOf('[');
        int arrEnd = trimmed.lastIndexOf(']');
        if (arrStart == -1 || arrEnd <= arrStart) return steps;
        String array = trimmed.substring(arrStart + 1, arrEnd);

        String[] objs = array.split("\\},\\{");
        Map<String, GoalStep> byLabel = new HashMap<>();
        List<String[]> depPairs = new ArrayList<>(); // [childLabel, depLabel]

        for (String obj : objs) {
            String o = obj;
            if (!o.startsWith("{")) o = "{" + o;
            if (!o.endsWith("}")) o = o + "}";

            String label = extractJsonValue(o, "label");
            if (label == null || label.isEmpty()) continue;
            GoalStep s = new GoalStep(label);
            steps.add(s);
            byLabel.put(label, s);

            List<String> deps = extractJsonArray(o, "dependsOn");
            for (String d : deps) depPairs.add(new String[]{label, d});
        }

        // Wire dependencies by label
        for (String[] pair : depPairs) {
            GoalStep child = byLabel.get(pair[0]);
            GoalStep dep = byLabel.get(pair[1]);
            if (child != null && dep != null) child.addDependency(dep.getId());
        }
        return steps;
    }

    private static String extractJsonValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx);
        int start = json.indexOf("\"", colon) + 1;
        int end = json.indexOf("\"", start);
        if (start <= 0 || end <= start) return null;
        return json.substring(start, end);
    }

    private static List<String> extractJsonArray(String json, String key) {
        List<String> result = new ArrayList<>();
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return result;
        int colon = json.indexOf(":", idx);
        int start = json.indexOf("[", colon);
        int end = json.indexOf("]", start);
        if (start == -1 || end == -1) return result;
        String content = json.substring(start + 1, end).trim();
        if (content.isEmpty()) return result;
        for (String item : content.split(",")) {
            String v = item.trim().replace("\"", "");
            if (!v.isEmpty()) result.add(v);
        }
        return result;
    }

    private static List<GoalStep> fallbackPlan(Goal.GoalType goalType) {
        List<GoalStep> steps = new ArrayList<>();
        switch (goalType) {
            case GATHER_WOOD: {
                GoalStep s1 = new GoalStep("Navigate to forest area");
                GoalStep s2 = new GoalStep("Collect 64 oak logs");
                s2.addDependency(s1.getId());
                GoalStep s3 = new GoalStep("Return to base");
                s3.addDependency(s2.getId());
                steps.add(s1); steps.add(s2); steps.add(s3);
                break;
            }
            case GATHER_STONE: {
                GoalStep s1 = new GoalStep("Find stone outcrop or cave entrance");
                GoalStep s2 = new GoalStep("Mine 64 stone blocks");
                s2.addDependency(s1.getId());
                steps.add(s1); steps.add(s2);
                break;
            }
            case MINE_DIAMONDS: {
                GoalStep s1 = new GoalStep("Locate cave system");
                GoalStep s2 = new GoalStep("Descend to diamond level");
                s2.addDependency(s1.getId());
                GoalStep s3 = new GoalStep("Mine 10 diamond ore");
                s3.addDependency(s2.getId());
                steps.add(s1); steps.add(s2); steps.add(s3);
                break;
            }
            case HUNT_ANIMALS: {
                GoalStep s1 = new GoalStep("Locate animals in vicinity");
                GoalStep s2 = new GoalStep("Hunt and collect 32 food");
                s2.addDependency(s1.getId());
                steps.add(s1); steps.add(s2);
                break;
            }
            case FARM_CROPS: {
                GoalStep s1 = new GoalStep("Find farmable crop area");
                GoalStep s2 = new GoalStep("Harvest 32 wheat/crops");
                s2.addDependency(s1.getId());
                steps.add(s1); steps.add(s2);
                break;
            }
            case EXPLORE_AREA: {
                GoalStep s1 = new GoalStep("Explore for 60 seconds");
                steps.add(s1);
                break;
            }
            case BUILD_STRUCTURE: {
                GoalStep s1 = new GoalStep("Gather 32 oak logs");
                GoalStep s2 = new GoalStep("Build 5-block pillar");
                s2.addDependency(s1.getId());
                steps.add(s1); steps.add(s2);
                break;
            }
            case FOLLOW_PLAYER: {
                GoalStep s1 = new GoalStep("Find nearest player");
                GoalStep s2 = new GoalStep("Follow continuously");
                s2.addDependency(s1.getId());
                steps.add(s1); steps.add(s2);
                break;
            }
            default: {
                GoalStep s1 = new GoalStep("Wander and observe environment");
                steps.add(s1);
            }
        }
        return steps;
    }
}

package com.freddy.plugin.advanced;

import com.freddy.plugin.npc.GoalManager;
import org.bukkit.entity.Player;

/**
 * Advanced module holder.
 */
public class AdvancedFeatureManager {
    private final AdvancedPerception perception;
    private final DeterministicPlanner deterministicPlanner;
    private final WorkflowSafety workflowSafety;
    private final ReactiveGoalGenerator reactiveGoals;

    public AdvancedFeatureManager(Player npcEntity, GoalManager goalManager) {
        this.perception = new AdvancedPerception(npcEntity);
        this.deterministicPlanner = new DeterministicPlanner();
        this.workflowSafety = new WorkflowSafety();
        this.reactiveGoals = new ReactiveGoalGenerator(goalManager);
    }

    public boolean isEnabled() {
        return true;
    }

    public AdvancedPerception perception() {
        return perception;
    }

    public DeterministicPlanner deterministicPlanner() {
        return deterministicPlanner;
    }

    public WorkflowSafety workflowSafety() {
        return workflowSafety;
    }

    public void setNpcEntity(Player npcEntity) {
        perception.setNpc(npcEntity);
    }

    public void tickReactive() {
        if (com.freddy.plugin.FreddyPlugin.isDashboardControlActive()) {
            return;
        }
        AdvancedWorldState state = perception.observe();
        reactiveGoals.maybeGenerate(state);
    }
}

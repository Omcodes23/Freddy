package com.freddy.plugin.advanced;

import com.freddy.plugin.npc.Goal;
import com.freddy.plugin.npc.GoalManager;

/**
 * Optional reactive goal seeding logic.
 */
public class ReactiveGoalGenerator {
    private static final long MIN_REACTIVE_INTERVAL_MS = 30000L;

    private final GoalManager goalManager;
    private long lastReactiveAt = 0L;

    public ReactiveGoalGenerator(GoalManager goalManager) {
        this.goalManager = goalManager;
    }

    public void maybeGenerate(AdvancedWorldState state) {
        long now = System.currentTimeMillis();
        if ((now - lastReactiveAt) < MIN_REACTIVE_INTERVAL_MS) {
            return;
        }
        if (goalManager.getCurrentGoal() != null) {
            return;
        }

        if (state.threatNearby || state.lavaNearby || !state.standingOnSolidBlock) {
            Goal goal = new Goal(Goal.GoalType.BUILD_STRUCTURE, "Reactive shelter setup");
            goalManager.setGoal(goal);
            lastReactiveAt = now;
            return;
        }

        Goal fallback = new Goal(Goal.GoalType.EXPLORE_AREA, "Reactive exploration fallback");
        goalManager.setGoal(fallback);
        lastReactiveAt = now;
    }
}

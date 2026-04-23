package com.freddy.plugin.advanced;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Loop and retry safety helper.
 */
public class WorkflowSafety {
    private static final int MAX_HISTORY = 8;

    private final Deque<String> recentHashes = new ArrayDeque<>();
    private final Set<String> permanentlyFailedActions = new HashSet<>();
    private int replanAttempts = 0;

    public boolean isDuplicateWorkflow(String workflowHash) {
        return recentHashes.contains(workflowHash);
    }

    public void recordWorkflow(String workflowHash) {
        recentHashes.addLast(workflowHash);
        while (recentHashes.size() > MAX_HISTORY) {
            recentHashes.removeFirst();
        }
    }

    public void blacklistAction(String actionKey) {
        permanentlyFailedActions.add(actionKey);
    }

    public boolean isBlacklisted(String actionKey) {
        return permanentlyFailedActions.contains(actionKey);
    }

    public boolean canReplan(int maxAttempts) {
        return replanAttempts < Math.max(1, maxAttempts);
    }

    public void incrementReplan() {
        replanAttempts++;
    }

    public void reset() {
        recentHashes.clear();
        permanentlyFailedActions.clear();
        replanAttempts = 0;
    }
}

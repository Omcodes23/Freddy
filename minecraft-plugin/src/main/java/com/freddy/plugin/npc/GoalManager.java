package com.freddy.plugin.npc;

import java.util.*;

/**
 * Manages NPC goals and directives
 */
public class GoalManager {
    private Goal currentGoal;
    private Queue<Goal> goalQueue = new LinkedList<>();
    private List<Goal> completedGoals = new ArrayList<>();
    
    /**
     * Set active goal
     */
    public void setGoal(Goal goal) {
        if (currentGoal != null && currentGoal.getStatus() == Goal.GoalStatus.IN_PROGRESS) {
            goalQueue.add(currentGoal);
        }
        
        this.currentGoal = goal;
        goal.setStatus(Goal.GoalStatus.IN_PROGRESS);
    }
    
    /**
     * Get current active goal
     */
    public Goal getCurrentGoal() {
        return currentGoal;
    }
    
    /**
     * Mark current goal as completed
     */
    public void completeCurrentGoal() {
        if (currentGoal != null) {
            currentGoal.setStatus(Goal.GoalStatus.COMPLETED);
            completedGoals.add(currentGoal);
            moveToNextGoal();
        }
    }
    
    /**
     * Mark current goal as failed
     */
    public void failCurrentGoal(String reason) {
        if (currentGoal != null) {
            currentGoal.setStatus(Goal.GoalStatus.FAILED);
            currentGoal.setParameter("failReason", reason);
            completedGoals.add(currentGoal);
            moveToNextGoal();
        }
    }
    
    /**
     * Move to next queued goal
     */
    private void moveToNextGoal() {
        if (!goalQueue.isEmpty()) {
            Goal nextGoal = goalQueue.poll();
            setGoal(nextGoal);
        } else {
            currentGoal = null;
        }
    }
    
    /**
     * Queue a goal to execute after current goal
     */
    public void queueGoal(Goal goal) {
        goal.setStatus(Goal.GoalStatus.PENDING);
        goalQueue.add(goal);
    }
    
    /**
     * Get all active and queued goals
     */
    public List<Goal> getAllGoals() {
        List<Goal> all = new ArrayList<>();
        if (currentGoal != null) {
            all.add(currentGoal);
        }
        all.addAll(goalQueue);
        return all;
    }
    
    /**
     * Get completed goals
     */
    public List<Goal> getCompletedGoals() {
        return new ArrayList<>(completedGoals);
    }
    
    /**
     * Clear all goals
     */
    public void clearAllGoals() {
        currentGoal = null;
        goalQueue.clear();
    }
}

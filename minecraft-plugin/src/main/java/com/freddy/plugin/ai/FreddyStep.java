package com.freddy.plugin.ai;

/**
 * FreddyStep - Single command in a workflow
 * 
 * Represents one Freddy command to be executed.
 * Designed to be JSON-serializable for LLM output.
 */
public class FreddyStep {

    /**
     * Command type (FREDDY_MINE, FREDDY_KILL, FREDDY_CRAFT)
     */
    public String command;

    /**
     * Target material or entity type
     * Examples: "OAK_LOG", "COW", "CRAFTING_TABLE"
     */
    public String target;

    /**
     * Quantity to obtain or craft
     */
    public int quantity;

    /**
     * Default constructor for JSON deserialization
     */
    public FreddyStep() {
    }

    /**
     * Constructor for manual creation
     */
    public FreddyStep(String command, String target, int quantity) {
        this.command = command;
        this.target = target;
        this.quantity = quantity;
    }

    /**
     * Get command as enum
     */
    public FreddyWorkflowAction getCommandEnum() {
        try {
            return FreddyWorkflowAction.valueOf(command.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Validate step
     */
    public boolean isValid() {
        return command != null &&
                target != null &&
                quantity > 0 &&
                getCommandEnum() != null;
    }

    @Override
    public String toString() {
        return String.format("%s %s x%d", command, target, quantity);
    }
}

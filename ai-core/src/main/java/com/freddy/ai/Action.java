package com.freddy.ai;

/**
 * Represents an action Freddy can perform.
 * Parsed from LLM responses and executed by the movement system.
 */
public abstract class Action {
    
    public final ActionType type;
    
    protected Action(ActionType type) {
        this.type = type;
    }
    
    public enum ActionType {
        FOLLOW_PLAYER, WALK_TO, IDLE, LOOK_AT, RESPOND, WANDER,
        MINE_BLOCK, PLACE_BLOCK, ATTACK_ENTITY, EAT_FOOD, PICKUP_ITEM
    }
    
    // Concrete implementations
    
    /**
     * Follow a specific player
     */
    public static class FollowPlayer extends Action {
        public final String playerName;
        
        public FollowPlayer(String playerName) {
            super(ActionType.FOLLOW_PLAYER);
            this.playerName = playerName;
        }
        
        @Override
        public String toString() {
            return "FOLLOW(" + playerName + ")";
        }
    }
    
    /**
     * Walk to specific coordinates
     */
    public static class WalkTo extends Action {
        public final double x, z;
        
        public WalkTo(double x, double z) {
            super(ActionType.WALK_TO);
            this.x = x;
            this.z = z;
        }
        
        @Override
        public String toString() {
            return "WALK_TO(" + String.format("%.1f", x) + ", " + String.format("%.1f", z) + ")";
        }
    }
    
    /**
     * Stand still and observe
     */
    public static class Idle extends Action {
        public Idle() {
            super(ActionType.IDLE);
        }
        
        @Override
        public String toString() {
            return "IDLE";
        }
    }
    
    /**
     * Look at a specific target (player or entity)
     */
    public static class LookAt extends Action {
        public final String target;
        
        public LookAt(String target) {
            super(ActionType.LOOK_AT);
            this.target = target;
        }
        
        @Override
        public String toString() {
            return "LOOK_AT(" + target + ")";
        }
    }
    
    /**
     * Send a chat message
     */
    public static class Respond extends Action {
        public final String message;
        
        public Respond(String message) {
            super(ActionType.RESPOND);
            this.message = message;
        }
        
        @Override
        public String toString() {
            return "RESPOND: " + message;
        }
    }
    
    /**
     * Wander randomly nearby
     */
    public static class Wander extends Action {
        public Wander() {
            super(ActionType.WANDER);
        }
        
        @Override
        public String toString() {
            return "WANDER";
        }
    }
    
    /**
     * Mine a block at a specific location
     */
    public static class MineBlock extends Action {
        public final String blockType;
        public final double x, y, z;
        
        public MineBlock(String blockType, double x, double y, double z) {
            super(ActionType.MINE_BLOCK);
            this.blockType = blockType;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public String toString() {
            return String.format("MINE_BLOCK(%s at %.1f, %.1f, %.1f)", blockType, x, y, z);
        }
    }
    
    /**
     * Place a block at a specific location
     */
    public static class PlaceBlock extends Action {
        public final String blockType;
        public final double x, y, z;
        
        public PlaceBlock(String blockType, double x, double y, double z) {
            super(ActionType.PLACE_BLOCK);
            this.blockType = blockType;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public String toString() {
            return String.format("PLACE_BLOCK(%s at %.1f, %.1f, %.1f)", blockType, x, y, z);
        }
    }
    
    /**
     * Attack a nearby entity
     */
    public static class AttackEntity extends Action {
        public final String entityName;
        
        public AttackEntity(String entityName) {
            super(ActionType.ATTACK_ENTITY);
            this.entityName = entityName;
        }
        
        @Override
        public String toString() {
            return "ATTACK(" + entityName + ")";
        }
    }
    
    /**
     * Eat food from inventory
     */
    public static class EatFood extends Action {
        public EatFood() {
            super(ActionType.EAT_FOOD);
        }
        
        @Override
        public String toString() {
            return "EAT_FOOD";
        }
    }
    
    /**
     * Pick up nearby item
     */
    public static class PickupItem extends Action {
        public final String itemName;
        
        public PickupItem(String itemName) {
            super(ActionType.PICKUP_ITEM);
            this.itemName = itemName;
        }
        
        @Override
        public String toString() {
            return "PICKUP(" + itemName + ")";
        }
    }
}

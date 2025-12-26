package com.freddy.plugin.npc;

/**
 * Represents an action the NPC can perform
 */
public abstract class NPCAction {
    protected String type;
    
    public NPCAction(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
    
    public abstract void execute(NPCController controller);
    
    // ===== Concrete Actions =====
    
    public static class MineBlock extends NPCAction {
        public int x, y, z;
        
        public MineBlock(int x, int y, int z) {
            super("MINE_BLOCK");
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public void execute(NPCController controller) {
            controller.mineBlock(x, y, z);
        }
    }
    
    public static class PlaceBlock extends NPCAction {
        public int x, y, z;
        public org.bukkit.Material material;
        
        public PlaceBlock(int x, int y, int z, org.bukkit.Material material) {
            super("PLACE_BLOCK");
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }
        
        @Override
        public void execute(NPCController controller) {
            controller.placeBlock(x, y, z, material);
        }
    }
    
    public static class WalkTo extends NPCAction {
        public double x, y, z;
        
        public WalkTo(double x, double y, double z) {
            super("WALK_TO");
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public void execute(NPCController controller) {
            controller.walkTo(x, y, z);
        }
    }
    
    public static class EatFood extends NPCAction {
        public EatFood() {
            super("EAT_FOOD");
        }
        
        @Override
        public void execute(NPCController controller) {
            controller.eatFood();
        }
    }
    
    public static class PickupItems extends NPCAction {
        public PickupItems() {
            super("PICKUP_ITEMS");
        }
        
        @Override
        public void execute(NPCController controller) {
            controller.pickupNearbyItems();
        }
    }
}

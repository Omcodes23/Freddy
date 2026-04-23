package com.freddy.plugin.ai.planning;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * PlanNode - Node in the dependency graph
 * 
 * Represents a single item in the crafting/gathering plan.
 * Forms a tree structure where each node depends on its children.
 * 
 * Example for IRON_SWORD:
 * PlanNode(IRON_SWORD, 1)
 * ├─ PlanNode(IRON_INGOT, 2)
 * │ ├─ PlanNode(RAW_IRON, 2)
 * │ │ └─ PlanNode(IRON_ORE, 2)
 * │ └─ PlanNode(FURNACE, 1)
 * └─ PlanNode(STICK, 1)
 * └─ PlanNode(OAK_PLANKS, 2)
 * └─ PlanNode(OAK_LOG, 1)
 */
public class PlanNode {

    public final Material item;
    public final int quantity;
    public final List<PlanNode> dependencies;

    // Node type for execution ordering
    public final NodeType type;

    public PlanNode(Material item, int quantity, NodeType type) {
        this.item = item;
        this.quantity = quantity;
        this.type = type;
        this.dependencies = new ArrayList<>();
    }

    /**
     * Add a dependency to this node
     */
    public void addDependency(PlanNode node) {
        dependencies.add(node);
    }

    /**
     * Check if this node has dependencies
     */
    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    /**
     * Check if this is a leaf node (primitive/gatherable)
     */
    public boolean isPrimitive() {
        return type == NodeType.PRIMITIVE;
    }

    /**
     * Get all primitive (leaf) nodes in this tree
     */
    public List<PlanNode> getPrimitives() {
        List<PlanNode> primitives = new ArrayList<>();
        collectPrimitives(this, primitives);
        return primitives;
    }

    private void collectPrimitives(PlanNode node, List<PlanNode> primitives) {
        if (node.isPrimitive()) {
            primitives.add(node);
        } else {
            for (PlanNode dep : node.dependencies) {
                collectPrimitives(dep, primitives);
            }
        }
    }

    /**
     * Get visualization string for this tree
     */
    public String visualize() {
        StringBuilder sb = new StringBuilder();
        visualize(this, "", true, sb);
        return sb.toString();
    }

    private void visualize(PlanNode node, String prefix, boolean isLast, StringBuilder sb) {
        sb.append(prefix);
        sb.append(isLast ? "└─ " : "├─ ");
        sb.append(String.format("%s x%d [%s]", node.item.name(), node.quantity, node.type));
        sb.append("\n");

        String newPrefix = prefix + (isLast ? "    " : "│   ");
        for (int i = 0; i < node.dependencies.size(); i++) {
            boolean last = (i == node.dependencies.size() - 1);
            visualize(node.dependencies.get(i), newPrefix, last, sb);
        }
    }

    @Override
    public String toString() {
        return String.format("PlanNode{%s x%d, %s, deps=%d}",
                item.name(), quantity, type, dependencies.size());
    }

    /**
     * NodeType - Classification of plan nodes
     */
    public enum NodeType {
        PRIMITIVE, // Must be gathered from world (ores, logs, etc.)
        SMELT, // Requires smelting
        CRAFT // Requires crafting
    }
}

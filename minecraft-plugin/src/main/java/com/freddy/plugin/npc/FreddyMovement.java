package com.freddy.plugin.npc;

import com.freddy.plugin.FreddyPlugin;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class FreddyMovement {

    private final NPC freddy;
    private final Plugin plugin;
    private Player followTarget;
    private BukkitTask followTask;
    private BukkitTask miningTask;

    public FreddyMovement(NPC freddy, Plugin plugin) {
        this.freddy = freddy;
        this.plugin = plugin;
    }

    public void comeHere(Player player) {
        stopFollow();
        Navigator nav = freddy.getNavigator();
        nav.setTarget(player.getLocation());
    }

    public void follow(Player player) {
        stopFollow();
        this.followTarget = player;

        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (followTarget == null || !followTarget.isOnline()) {
                    stopFollow();
                    return;
                }

                if (!freddy.isSpawned()) {
                    stopFollow();
                    return;
                }

                Location npcLoc = freddy.getEntity().getLocation();
                Location playerLoc = followTarget.getLocation();
                double distance = npcLoc.distance(playerLoc);

                if (distance < 2.5) {
                    freddy.getNavigator().cancelNavigation();
                    return;
                }

                freddy.getNavigator().setTarget(playerLoc);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop() {
        stopFollow();
        freddy.getNavigator().cancelNavigation();
        if (miningTask != null) {
            miningTask.cancel();
            miningTask = null;
        }
    }

    private void stopFollow() {
        followTarget = null;
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }

    public void startIdleBehavior() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!freddy.isSpawned()) return;
                if (followTarget != null) return;

                Location base = freddy.getEntity().getLocation();
                Location wander = base.clone().add(
                        random(-4, 4), 0, random(-4, 4)
                );
                freddy.getNavigator().setTarget(wander);
            }
        }.runTaskTimer(plugin, 100L, 200L);
    }

    private double random(double min, double max) {
        return min + (Math.random() * (max - min));
    }

    public void mine(Material material) {
        if (material == null || !freddy.isSpawned() || freddy.getEntity() == null) {
            return;
        }

        Location origin = freddy.getEntity().getLocation();
        Block nearest = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = 12;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block candidate = origin.getWorld().getBlockAt(
                        origin.getBlockX() + x,
                        origin.getBlockY() + y,
                        origin.getBlockZ() + z
                    );
                    if (candidate.getType() != material) continue;

                    double distance = candidate.getLocation().distance(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearest = candidate;
                    }
                }
            }
        }

        if (nearest == null) {
            Bukkit.getLogger().warning("[NPC] No nearby block found to mine: " + material.name());
            return;
        }

        if (FreddyPlugin.getAIBrainLoop() != null && FreddyPlugin.getAIBrainLoop().getNpcController() != null) {
            FreddyPlugin.getAIBrainLoop().getNpcController().queueAction(
                new NPCAction.MineBlock(nearest.getX(), nearest.getY(), nearest.getZ())
            );
            return;
        }

        freddy.getNavigator().setTarget(nearest.getLocation().add(0.5, 0.0, 0.5));
    }

    public void mineWithLimit(Material material, int quantity, Runnable onBlockMined) {
        if (material == null || quantity <= 0) return;

        if (miningTask != null) {
            miningTask.cancel();
            miningTask = null;
        }

        final int[] mined = {0};
        miningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (mined[0] >= quantity) {
                if (miningTask != null) {
                    miningTask.cancel();
                    miningTask = null;
                }
                return;
            }

            mine(material);
            mined[0]++;

            if (onBlockMined != null) {
                onBlockMined.run();
            }
        }, 0L, 20L);
    }
}

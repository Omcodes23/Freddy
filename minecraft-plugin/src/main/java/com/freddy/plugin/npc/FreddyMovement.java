//package com.freddy.plugin.npc;
//
//import net.citizensnpcs.api.ai.Navigator;
//import net.citizensnpcs.api.npc.NPC;
//import org.bukkit.Bukkit;
//import org.bukkit.Location;
//import org.bukkit.entity.Player;
//import org.bukkit.plugin.Plugin;
//import org.bukkit.scheduler.BukkitTask;
//
//public class FreddyMovement {
//
//    private final NPC freddy;
//    private final Plugin plugin;
//    private BukkitTask followTask;
//
//    private static final double STOP_DISTANCE = 2.5;
//
//    public FreddyMovement(NPC freddy, Plugin plugin) {
//        this.freddy = freddy;
//        this.plugin = plugin;
//    }
//
//    private Navigator navigator() {
//        Navigator nav = freddy.getNavigator();
//        nav.getDefaultParameters()
//                .speedModifier(1.1f)
//                .range(25)
//                .avoidWater(true);
//        return nav;
//    }
//
//    // 🟢 COME HERE (ONCE)
//    public void comeHere(Player player) {
//        if (!freddy.isSpawned()) return;
//
//        Location target = stopNear(player);
//        navigator().setTarget(target);
//    }
//
//    // 🔁 FOLLOW (SAFE)
//    public void follow(Player player) {
//        stop();
//
//        followTask = Bukkit.getScheduler().runTaskTimer(
//                plugin,
//                () -> {
//                    if (!player.isOnline() || !freddy.isSpawned()) {
//                        stop();
//                        return;
//                    }
//
//                    double distance = freddy.getEntity()
//                            .getLocation()
//                            .distance(player.getLocation());
//
//                    if (distance > STOP_DISTANCE) {
//                        navigator().setTarget(stopNear(player));
//                    } else {
//                        freddy.getNavigator().cancelNavigation();
//                        freddy.faceLocation(player.getLocation());
//                    }
//                },
//                0L,
//                20L
//        );
//    }
//
//    // 🛑 STOP
//    public void stop() {
//        if (followTask != null) {
//            followTask.cancel();
//            followTask = null;
//        }
//        freddy.getNavigator().cancelNavigation();
//    }
//
//    // 🧠 Calculate safe stop position
//    private Location stopNear(Player player) {
//        Location p = player.getLocation();
//        return p.clone().add(
//                p.getDirection().multiply(-STOP_DISTANCE)
//        );
//    }
//}
//
//public void wanderRandomly() {
//    if (!freddy.isSpawned()) return;
//
//    Location base = freddy.getEntity().getLocation();
//    Location target = base.clone().add(
//            (Math.random() * 10) - 5,
//            0,
//            (Math.random() * 10) - 5
//    );
//
//    navigator().setTarget(target);
//}

package com.freddy.plugin.npc;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class FreddyMovement {

    private final NPC freddy;
    private final Plugin plugin;
    private Player followTarget;

    public FreddyMovement(NPC freddy, Plugin plugin) {
        this.freddy = freddy;
        this.plugin = plugin;
    }

    /* =========================
       COMMAND-DRIVEN ACTIONS
       ========================= */

    public void comeHere(Player player) {
        Navigator nav = freddy.getNavigator();
        nav.setTarget(player.getLocation());
    }

    public void follow(Player player) {
        this.followTarget = player;

        new BukkitRunnable() {
            @Override
            public void run() {

                if (followTarget == null || !followTarget.isOnline()) {
                    cancel();
                    return;
                }

                if (!freddy.isSpawned()) {
                    cancel();
                    return;
                }

                Location npcLoc = freddy.getEntity().getLocation();
                Location playerLoc = followTarget.getLocation();

                double distance = npcLoc.distance(playerLoc);

                // ✅ STOP before hitting the player
                if (distance < 2.5) {
                    freddy.getNavigator().cancelNavigation();
                    return;
                }

                freddy.getNavigator().setTarget(playerLoc);
            }
        }.runTaskTimer(plugin, 0L, 20L); // every 1 second
    }

    public void stop() {
        followTarget = null;
        freddy.getNavigator().cancelNavigation();
    }

    /* =========================
       AUTONOMOUS IDLE LOOP
       ========================= */

    public void startIdleBehavior() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!freddy.isSpawned()) return;

                // Don’t wander if following someone
                if (followTarget != null) return;

                Location base = freddy.getEntity().getLocation();
                Location wander = base.clone().add(
                        random(-4, 4),
                        0,
                        random(-4, 4)
                );

                freddy.getNavigator().setTarget(wander);
            }
        }.runTaskTimer(plugin, 100L, 200L); // idle wander
    }

    private double random(double min, double max) {
        return min + (Math.random() * (max - min));
    }
}

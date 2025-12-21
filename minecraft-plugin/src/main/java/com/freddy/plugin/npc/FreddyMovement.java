package com.freddy.plugin.npc;

import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class FreddyMovement {

    private final NPC freddy;
    private final Plugin plugin;
    private BukkitTask followTask;

    private static final double STOP_DISTANCE = 2.5;

    public FreddyMovement(NPC freddy, Plugin plugin) {
        this.freddy = freddy;
        this.plugin = plugin;
    }

    private Navigator navigator() {
        Navigator nav = freddy.getNavigator();
        nav.getDefaultParameters()
                .speedModifier(1.1f)
                .range(25)
                .avoidWater(true);
        return nav;
    }

    // 🟢 COME HERE (ONCE)
    public void comeHere(Player player) {
        if (!freddy.isSpawned()) return;

        Location target = stopNear(player);
        navigator().setTarget(target);
    }

    // 🔁 FOLLOW (SAFE)
    public void follow(Player player) {
        stop();

        followTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!player.isOnline() || !freddy.isSpawned()) {
                        stop();
                        return;
                    }

                    double distance = freddy.getEntity()
                            .getLocation()
                            .distance(player.getLocation());

                    if (distance > STOP_DISTANCE) {
                        navigator().setTarget(stopNear(player));
                    } else {
                        freddy.getNavigator().cancelNavigation();
                        freddy.faceLocation(player.getLocation());
                    }
                },
                0L,
                20L
        );
    }

    // 🛑 STOP
    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        freddy.getNavigator().cancelNavigation();
    }

    // 🧠 Calculate safe stop position
    private Location stopNear(Player player) {
        Location p = player.getLocation();
        return p.clone().add(
                p.getDirection().multiply(-STOP_DISTANCE)
        );
    }
}

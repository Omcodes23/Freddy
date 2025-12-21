package com.freddy.plugin.listener;

import com.freddy.llm.LLMClient;
import com.freddy.plugin.npc.FreddyMovement;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.stream.StreamSupport;

public class PlayerChatListener implements Listener {

    private NPC getFreddy() {
        return StreamSupport.stream(
                        CitizensAPI.getNPCRegistry().spliterator(), false
                ).filter(npc -> npc.getName().equalsIgnoreCase("Freddy"))
                .findFirst()
                .orElse(null);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String msg = event.getMessage().toLowerCase();
        var player = event.getPlayer();

        if (!msg.contains("freddy")) return;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("FreddyAI");
        NPC freddy = getFreddy();
        if (freddy == null) return;

        FreddyMovement movement = new FreddyMovement(freddy, plugin);

        // 🔹 MOVEMENT (MAIN THREAD)
        Bukkit.getScheduler().runTask(plugin, () -> {

            if (msg.contains("come here")) {
                movement.comeHere(player);
            }

            else if (msg.contains("follow me")) {
                movement.follow(player);
            }

            else if (msg.contains("stop")) {
                movement.stop();
            }
        });

        // 🔹 CHAT (ASYNC)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String reply = LLMClient.ask(event.getMessage());
            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> player.sendMessage("§a[Freddy] §f" + reply)
            );
        });
    }
}

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

    private FreddyMovement movement;

    private NPC getFreddy() {
        return StreamSupport.stream(
                        CitizensAPI.getNPCRegistry().spliterator(), false
                ).filter(npc -> npc.getName().equalsIgnoreCase("Freddy"))
                .findFirst()
                .orElse(null);
    }

    private FreddyMovement getOrCreateMovement(NPC freddy, Plugin plugin) {
        if (movement == null && freddy != null) {
            movement = new FreddyMovement(freddy, plugin);
        }
        return movement;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String msg = event.getMessage().toLowerCase();
        var player = event.getPlayer();

        if (!msg.contains("freddy")) return;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("FreddyAI");
        NPC freddy = getFreddy();
        if (freddy == null) return;

        FreddyMovement mov = getOrCreateMovement(freddy, plugin);
        if (mov == null) return;

        // Movement commands (MAIN THREAD)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.contains("come here")) {
                mov.comeHere(player);
            } else if (msg.contains("follow me")) {
                mov.follow(player);
            } else if (msg.contains("stop")) {
                mov.stop();
            }
        });

        // Chat response (ASYNC)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String reply = LLMClient.ask(event.getMessage());
            if (reply != null && !reply.isBlank()) {
                Bukkit.getScheduler().runTask(
                    plugin,
                    () -> player.sendMessage("§a[Freddy] §f" + reply)
                );
            } else {
                Bukkit.getScheduler().runTask(
                    plugin,
                    () -> player.sendMessage("§a[Freddy] §f*seems lost in thought*")
                );
            }
        });
    }
}

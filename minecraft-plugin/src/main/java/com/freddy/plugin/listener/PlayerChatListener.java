package com.freddy.plugin.listener;

import com.freddy.llm.LLMClient;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class PlayerChatListener implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String msg = event.getMessage();
        var player = event.getPlayer();

        if (!msg.toLowerCase().contains("freddy")) return;

        Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("FreddyAI"),
                () -> {
                    String reply = LLMClient.ask(msg);

                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("FreddyAI"),
                            () -> player.sendMessage("§a[Freddy] §f" + reply)
                    );
                }
        );
    }
}

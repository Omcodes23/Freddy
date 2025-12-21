package com.freddy.plugin;

import com.freddy.ai.AgentBrain;
import com.freddy.plugin.listener.PlayerChatListener;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.stream.StreamSupport;

public class FreddyPlugin extends JavaPlugin {

    private static NPC freddy;
    private static AgentBrain brain;

    @Override
    public void onEnable() {
        getLogger().info("FreddyAI enabled, waiting for Citizens...");

        // ✅ FIXED: pass NPC name
//        brain = new AgentBrain("Freddy");

        getServer().getPluginManager()
                .registerEvents(new PlayerChatListener(), this);

        // Delay Citizens access (Citizens loads after plugins)
        Bukkit.getScheduler().runTaskLater(this, this::loadFreddyNPC, 20L);
    }

    private void loadFreddyNPC() {
        freddy = StreamSupport.stream(
                        CitizensAPI.getNPCRegistry().spliterator(),
                        false
                )
                .filter(npc -> npc.getName().equalsIgnoreCase("Freddy"))
                .findFirst()
                .orElse(null);

        if (freddy == null) {
            getLogger().severe("Freddy NPC not found! Create it using /npc create Freddy");
        } else {
            getLogger().info("Freddy NPC linked successfully!");
        }
    }

    public static NPC getFreddy() {
        return freddy;
    }
}

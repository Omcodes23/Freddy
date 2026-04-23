package com.freddy.plugin.commands;

import com.freddy.plugin.FreddyPlugin;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Paper-native dev tools command set that mirrors practical Dev Tools Unlocker functionality.
 */
public class DevToolsCommand implements CommandExecutor, TabCompleter {

    private static volatile boolean verboseDebug = false;
    private final JavaPlugin plugin;

    private static final List<String> ARMOR_TRIM_TEMPLATES = List.of(
        "SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE",
        "DUNE_ARMOR_TRIM_SMITHING_TEMPLATE",
        "COAST_ARMOR_TRIM_SMITHING_TEMPLATE",
        "WILD_ARMOR_TRIM_SMITHING_TEMPLATE",
        "WARD_ARMOR_TRIM_SMITHING_TEMPLATE",
        "EYE_ARMOR_TRIM_SMITHING_TEMPLATE",
        "VEX_ARMOR_TRIM_SMITHING_TEMPLATE",
        "TIDE_ARMOR_TRIM_SMITHING_TEMPLATE",
        "SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE",
        "RIB_ARMOR_TRIM_SMITHING_TEMPLATE",
        "SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE",
        "WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE",
        "SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE",
        "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE",
        "RAISER_ARMOR_TRIM_SMITHING_TEMPLATE",
        "HOST_ARMOR_TRIM_SMITHING_TEMPLATE",
        "BOLT_ARMOR_TRIM_SMITHING_TEMPLATE",
        "FLOW_ARMOR_TRIM_SMITHING_TEMPLATE"
    );

    public DevToolsCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by players.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "debugpath" -> handleDebugPath(player);
            case "debugmobspawning" -> handleDebugMobSpawning(player, args);
            case "serverpack" -> handleServerPack(player);
            case "debugconfig" -> handleDebugConfig(player, args);
            case "raid" -> handleRaid(player, args);
            case "spawn_armor_trims" -> handleSpawnArmorTrims(player);
            case "test" -> handleTest(player);
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown devtools command: " + sub);
                sendHelp(player);
            }
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "Dev Tools (Paper) Commands:");
        player.sendMessage(ChatColor.YELLOW + "/devtools debugpath " + ChatColor.GRAY + "- Show Freddy navigation target/state");
        player.sendMessage(ChatColor.YELLOW + "/devtools debugmobspawning [radius] " + ChatColor.GRAY + "- Show mob category counts");
        player.sendMessage(ChatColor.YELLOW + "/devtools serverpack " + ChatColor.GRAY + "- Show server resource-pack config");
        player.sendMessage(ChatColor.YELLOW + "/devtools debugconfig [on|off|status] " + ChatColor.GRAY + "- Toggle plugin debug flag");
        player.sendMessage(ChatColor.YELLOW + "/devtools raid [status|start|stop] " + ChatColor.GRAY + "- Raid tools near your location");
        player.sendMessage(ChatColor.YELLOW + "/devtools spawn_armor_trims " + ChatColor.GRAY + "- Give all trim smithing templates");
        player.sendMessage(ChatColor.YELLOW + "/devtools test " + ChatColor.GRAY + "- Quick command sanity test");
    }

    private void handleDebugPath(Player player) {
        NPC freddy = FreddyPlugin.getFreddy();
        if (freddy == null || freddy.getEntity() == null) {
            player.sendMessage(ChatColor.RED + "Freddy NPC is not available.");
            return;
        }

        Location freddyLoc = freddy.getEntity().getLocation();
        var navigator = freddy.getNavigator();
        boolean navigating = navigator.isNavigating();

        player.sendMessage(ChatColor.AQUA + "Freddy Path Debug:");
        player.sendMessage(ChatColor.GRAY + "NPC Location: " + formatLoc(freddyLoc));
        player.sendMessage(ChatColor.GRAY + "Navigating: " + navigating);

        if (navigator.getTargetAsLocation() != null) {
            Location target = navigator.getTargetAsLocation();
            player.sendMessage(ChatColor.GRAY + "Target: " + formatLoc(target));
            player.sendMessage(ChatColor.GRAY + "Distance: " + String.format(Locale.US, "%.2f", freddyLoc.distance(target)));
        } else {
            player.sendMessage(ChatColor.GRAY + "Target: none");
        }
    }

    private void handleDebugMobSpawning(Player player, String[] args) {
        int radius = 64;
        if (args.length >= 2) {
            try {
                radius = Math.max(8, Math.min(256, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }

        Location center = player.getLocation();
        List<Entity> nearby = new ArrayList<>(center.getWorld().getNearbyEntities(center, radius, radius, radius));
        List<LivingEntity> living = nearby.stream()
            .filter(e -> e instanceof LivingEntity)
            .map(e -> (LivingEntity) e)
            .collect(Collectors.toList());

        int monsters = (int) living.stream().filter(e -> e instanceof Monster).count();
        int mobs = (int) living.stream().filter(e -> e instanceof Mob).count();
        int players = (int) living.stream().filter(e -> e instanceof Player).count();
        int passive = Math.max(0, mobs - monsters);

        Map<String, Long> topTypes = living.stream()
            .collect(Collectors.groupingBy(e -> e.getType().name(), HashMap::new, Collectors.counting()));

        List<Map.Entry<String, Long>> sorted = topTypes.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .limit(8)
            .toList();

        player.sendMessage(ChatColor.AQUA + "Mob Spawn Debug (r=" + radius + "):");
        player.sendMessage(ChatColor.GRAY + "Total Living: " + living.size() + " | Hostile: " + monsters + " | Passive: " + passive + " | Players: " + players);
        for (Map.Entry<String, Long> entry : sorted) {
            player.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + entry.getKey() + ChatColor.WHITE + ": " + entry.getValue());
        }
    }

    private void handleServerPack(Player player) {
        String url = Bukkit.getResourcePack();
        if (url == null || url.isBlank()) {
            player.sendMessage(ChatColor.YELLOW + "No server resource-pack URL is configured.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "Server Resource Pack:");
        player.sendMessage(ChatColor.GRAY + "URL: " + url);
        String hash = Bukkit.getResourcePackHash();
        if (hash != null && !hash.isBlank()) {
            player.sendMessage(ChatColor.GRAY + "Hash: " + hash);
        }
        player.sendMessage(ChatColor.GRAY + "Required: " + Bukkit.isResourcePackRequired());
    }

    private void handleDebugConfig(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            player.sendMessage(ChatColor.GRAY + "Verbose debug is " + (verboseDebug ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("on")) {
            verboseDebug = true;
            player.sendMessage(ChatColor.GREEN + "Verbose debug enabled.");
            plugin.getLogger().info("[DevTools] Verbose debug enabled by " + player.getName());
        } else if (mode.equals("off")) {
            verboseDebug = false;
            player.sendMessage(ChatColor.YELLOW + "Verbose debug disabled.");
            plugin.getLogger().info("[DevTools] Verbose debug disabled by " + player.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /devtools debugconfig [on|off|status]");
        }
    }

    private void handleRaid(Player player, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        PotionEffectType raidOmen = PotionEffectType.getByName("RAID_OMEN");
        if (raidOmen == null) {
            raidOmen = PotionEffectType.getByName("BAD_OMEN");
        }

        if (raidOmen == null) {
            player.sendMessage(ChatColor.RED + "This server API does not expose raid omen effect types.");
            return;
        }

        PotionEffect active = player.getPotionEffect(raidOmen);

        switch (action) {
            case "status" -> {
                if (active == null) {
                    player.sendMessage(ChatColor.YELLOW + "No raid omen effect currently active on you.");
                } else {
                    player.sendMessage(ChatColor.AQUA + "Raid Omen Status:");
                    player.sendMessage(ChatColor.GRAY + "Amplifier: " + (active.getAmplifier() + 1));
                    player.sendMessage(ChatColor.GRAY + "Duration: " + (active.getDuration() / 20) + "s");
                    player.sendMessage(ChatColor.GRAY + "Enter a village to trigger a raid.");
                }
            }
            case "start" -> {
                player.addPotionEffect(new PotionEffect(raidOmen, 20 * 60, 1, false, true, true));
                player.sendMessage(ChatColor.GREEN + "Raid omen applied. Enter a village area to trigger a raid.");
            }
            case "stop" -> {
                if (active == null) {
                    player.sendMessage(ChatColor.YELLOW + "No raid omen effect to clear.");
                } else {
                    player.removePotionEffect(raidOmen);
                    player.sendMessage(ChatColor.GREEN + "Raid omen cleared.");
                }
            }
            default -> player.sendMessage(ChatColor.RED + "Usage: /devtools raid [status|start|stop]");
        }
    }

    private void handleSpawnArmorTrims(Player player) {
        int given = 0;
        for (String matName : ARMOR_TRIM_TEMPLATES) {
            Material material = Material.matchMaterial(matName);
            if (material != null) {
                player.getInventory().addItem(new ItemStack(material, 1));
                given++;
            }
        }

        if (given == 0) {
            player.sendMessage(ChatColor.RED + "No armor trim template materials found in this server version.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Given " + given + " armor trim smithing templates.");
        }
    }

    private void handleTest(Player player) {
        player.sendMessage(ChatColor.GREEN + "DevTools bridge is active on Paper " + Bukkit.getBukkitVersion());
        if (verboseDebug) {
            plugin.getLogger().info("[DevTools] test command run by " + player.getName() + " at " + formatLoc(player.getLocation()));
        }
    }

    private String formatLoc(Location loc) {
        return String.format(Locale.US, "%s (%.1f, %.1f, %.1f)",
            loc.getWorld() == null ? "world" : loc.getWorld().getName(),
            loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("help", "debugpath", "debugmobspawning", "serverpack", "debugconfig", "raid", "spawn_armor_trims", "test"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debugconfig")) {
            return partial(args[1], List.of("on", "off", "status"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("raid")) {
            return partial(args[1], List.of("status", "start", "stop"));
        }

        return List.of();
    }

    private List<String> partial(String token, List<String> values) {
        String lower = token.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(v -> v.startsWith(lower))
            .collect(Collectors.toList());
    }
}

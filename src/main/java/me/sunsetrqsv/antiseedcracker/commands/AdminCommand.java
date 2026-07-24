package me.sunsetrqsv.antiseedcracker.commands;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.scheduler.FoliaSchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    /** Read-only subcommands (status/stats/info). Granted to ops by default. */
    private static final String PERM_STATUS = "antiseedcracker.admin.status";
    /** State-changing subcommands (reload/toggle). Granted to ops by default. */
    private static final String PERM_MANAGE = "antiseedcracker.admin.manage";

    private static final Component PREFIX =
            Component.text("[", NamedTextColor.DARK_GRAY)
                     .append(Component.text("ASC", NamedTextColor.AQUA))
                     .append(Component.text("] ", NamedTextColor.DARK_GRAY));

    private static final List<String> STATUS_SUBCOMMANDS = Arrays.asList("status", "stats", "info", "help");
    private static final List<String> MANAGE_SUBCOMMANDS = Arrays.asList("reload", "toggle");

    /** feature name -> config.yml path, for {@code /asc toggle}. Order defines tab-complete order. */
    private static final Map<String, String> TOGGLE_PATHS = buildTogglePaths();

    private static Map<String, String> buildTogglePaths() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("login",       "seed_obfuscation.intercept_login");
        m.put("respawn",     "seed_obfuscation.intercept_respawn");
        m.put("rotation",    "seed_rotation.enabled");
        m.put("endspikes",   "structure_protection.end_spikes.enabled");
        m.put("endcities",   "structure_protection.end_cities.enabled");
        m.put("locate",      "structure_protection.spoof_locate_command.enabled");
        m.put("integrity",   "seed_integrity_monitor.enabled");
        m.put("slime",       "slime_chunk_obfuscation.enabled");
        m.put("recon",       "structure_recon_monitor.enabled");
        m.put("treasuremap", "treasure_map_protection.enabled");
        m.put("database",    "database.enabled");
        m.put("probing",     "command_probing_monitor.enabled");
        return Collections.unmodifiableMap(m);
    }

    private final AntiSeedCrackerPlugin plugin;

    public AdminCommand(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> runIfPermitted(sender, PERM_MANAGE, () -> handleReload(sender));
            case "toggle" -> runIfPermitted(sender, PERM_MANAGE, () -> handleToggle(sender, args));
            case "status" -> runIfPermitted(sender, PERM_STATUS, () -> handleStatus(sender));
            case "stats"  -> runIfPermitted(sender, PERM_STATUS, () -> handleStats(sender));
            case "info"   -> runIfPermitted(sender, PERM_STATUS, () -> handleInfo(sender));
            case "help"   -> runIfPermitted(sender, PERM_STATUS, () -> handleHelp(sender));
            default       -> handleHelp(sender);
        }
        return true;
    }

    private void runIfPermitted(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(msg(Component.text(
                    "You do not have permission to use this command.", NamedTextColor.RED)));
            return;
        }
        action.run();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(PERM_STATUS)) options.addAll(STATUS_SUBCOMMANDS);
            if (sender.hasPermission(PERM_MANAGE)) options.addAll(MANAGE_SUBCOMMANDS);
            return options.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle") && sender.hasPermission(PERM_MANAGE)) {
            String partial = args[1].toLowerCase();
            return TOGGLE_PATHS.keySet().stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle") && sender.hasPermission(PERM_MANAGE)) {
            String partial = args[2].toLowerCase();
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage(msg(Component.text("Reloading AntiSeedCracker…", NamedTextColor.YELLOW)));
        try {
            plugin.reload();
            sender.sendMessage(msg(Component.text("Reload successful.", NamedTextColor.GREEN)));
        } catch (Exception e) {
            sender.sendMessage(msg(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED)));
            plugin.getLogger().severe("[AntiSeedCracker] Reload exception: " + e.getMessage());
        }
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg(Component.text("Usage: ", NamedTextColor.YELLOW)
                    .append(Component.text("/asc toggle <feature> [true|false]", NamedTextColor.WHITE))));
            sender.sendMessage(msg(Component.text("Features: ", NamedTextColor.GRAY)
                    .append(Component.text(String.join(", ", TOGGLE_PATHS.keySet()), NamedTextColor.WHITE))));
            return;
        }

        String feature = args[1].toLowerCase();
        String path = TOGGLE_PATHS.get(feature);
        if (path == null) {
            sender.sendMessage(msg(Component.text("Unknown feature '" + feature + "'. Valid: ", NamedTextColor.RED)
                    .append(Component.text(String.join(", ", TOGGLE_PATHS.keySet()), NamedTextColor.WHITE))));
            return;
        }

        boolean newValue;
        if (args.length >= 3) {
            if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
                sender.sendMessage(msg(Component.text("Value must be 'true' or 'false'.", NamedTextColor.RED)));
                return;
            }
            newValue = Boolean.parseBoolean(args[2]);
        } else {
            newValue = !plugin.getConfig().getBoolean(path, true);
        }

        plugin.getConfig().set(path, newValue);
        plugin.saveConfig();
        try {
            plugin.reload();
            sender.sendMessage(msg(Component.text(feature + " ", NamedTextColor.AQUA)
                    .append(Component.text("set to ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(newValue),
                            newValue ? NamedTextColor.GREEN : NamedTextColor.RED))));
        } catch (Exception e) {
            sender.sendMessage(msg(Component.text(
                    "Config updated but reload failed: " + e.getMessage(), NamedTextColor.RED)));
            plugin.getLogger().severe("[AntiSeedCracker] Reload exception after toggle: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        boolean loginIntercepted   = plugin.getPluginConfig().isInterceptLogin();
        boolean respawnIntercepted = plugin.getPluginConfig().isInterceptRespawn();
        boolean rotationEnabled    = plugin.getPluginConfig().isSeedRotationEnabled();
        boolean spikesEnabled      = plugin.getPluginConfig().isEndSpikesEnabled();
        boolean citiesEnabled      = plugin.getPluginConfig().isEndCitiesEnabled();
        boolean eyeEnabled         = plugin.getPluginConfig().isEyeOfEnderEnabled();
        boolean locateEnabled      = plugin.getPluginConfig().isSpoofLocateEnabled();
        boolean integrityEnabled   = plugin.getPluginConfig().isSeedIntegrityMonitorEnabled();
        boolean slimeObfEnabled    = plugin.getPluginConfig().isSlimeChunkObfuscationEnabled();
        boolean reconEnabled       = plugin.getPluginConfig().isStructureReconMonitorEnabled();
        boolean probingEnabled     = plugin.getPluginConfig().isCommandProbingMonitorEnabled();
        boolean mapProtEnabled     = plugin.getPluginConfig().isTreasureMapProtectionEnabled();
        boolean dbEnabled          = plugin.getDatabaseManager() != null;
        int     trackedSeeds       = plugin.getSeedManager().trackedCount();

        sender.sendMessage(Component.empty());
        sender.sendMessage(msg(Component.text("━━ AntiSeedCracker v"
                + plugin.getPluginMeta().getVersion() + " by SunsetRQ7_ ━━",
                NamedTextColor.GOLD)));
        sender.sendMessage(statusLine("Login Packet Intercept",   loginIntercepted));
        sender.sendMessage(statusLine("Respawn Packet Intercept", respawnIntercepted));
        sender.sendMessage(statusLine("Seed Rotation",            rotationEnabled));
        sender.sendMessage(statusLine("End Spike Protection",     spikesEnabled));
        sender.sendMessage(statusLine("End City Protection",      citiesEnabled));
        sender.sendMessage(statusLine("Eye of Ender Redirect",    eyeEnabled));
        sender.sendMessage(statusLine("/locate Spoof",            locateEnabled));
        sender.sendMessage(statusLine("Seed Integrity Monitor",   integrityEnabled));
        sender.sendMessage(statusLine("Slime Chunk Obfuscation",  slimeObfEnabled));
        sender.sendMessage(statusLine("Structure Recon Monitor",  reconEnabled));
        sender.sendMessage(statusLine("Command Probing Monitor",  probingEnabled));
        sender.sendMessage(statusLine("Treasure Map Scramble",    mapProtEnabled));
        sender.sendMessage(statusLine("Database / Audit Log",     dbEnabled));
        sender.sendMessage(msg(
                Component.text("Tracked players: ", NamedTextColor.AQUA)
                         .append(Component.text(String.valueOf(trackedSeeds), NamedTextColor.WHITE))));
        sender.sendMessage(Component.empty());
    }

    private void handleStats(CommandSender sender) {
        me.sunsetrqsv.antiseedcracker.database.DatabaseManager db =
                plugin.getDatabaseManager();
        if (db == null) {
            sender.sendMessage(msg(Component.text(
                    "Database is disabled or failed to initialise.", NamedTextColor.RED)));
            return;
        }

        if (sender instanceof org.bukkit.entity.Player) {
            // Real players: hop off-thread so reading the log files never taxes a tick.
            sender.sendMessage(msg(Component.text("Loading stats...", NamedTextColor.YELLOW)));
            FoliaSchedulerUtil.runAsync(plugin, () -> {
                java.util.Map<String, Long> stats = db.getStats();
                FoliaSchedulerUtil.runGlobal(plugin, () -> sendStats(sender, stats));
            });
        } else {
            // Console/RCON commands are dispatched and captured synchronously by the
            // server; deferring the reply to a later tick here would let it arrive after
            // the RCON round-trip already closed and bleed into whatever command runs
            // next over the same connection instead of this one's response.
            sendStats(sender, db.getStats());
        }
    }

    private void sendStats(CommandSender sender, java.util.Map<String, Long> stats) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(msg(Component.text(
                "━━ Protection Statistics ━━", NamedTextColor.GOLD)));
        if (stats.isEmpty()) {
            sender.sendMessage(msg(Component.text(
                    "No events recorded yet.", NamedTextColor.GRAY)));
        } else {
            for (java.util.Map.Entry<String, Long> e : stats.entrySet()) {
                String label = e.getKey().replace('_', ' ').toLowerCase();
                label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
                sender.sendMessage(
                        Component.text("  ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(label + ": ", NamedTextColor.GRAY))
                        .append(Component.text(String.valueOf(e.getValue()),
                                NamedTextColor.WHITE)));
            }
        }
        sender.sendMessage(Component.empty());
    }

    private void handleInfo(CommandSender sender) {
        me.sunsetrqsv.antiseedcracker.util.UpdateChecker uc = plugin.getUpdateChecker();
        sender.sendMessage(Component.empty());
        sender.sendMessage(msg(Component.text(
                "━━ AntiSeedCracker Info ━━", NamedTextColor.GOLD)));
        sender.sendMessage(msg(Component.text("Version:  ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getPluginMeta().getVersion(),
                        NamedTextColor.WHITE))));
        sender.sendMessage(msg(Component.text("Author:   ", NamedTextColor.GRAY)
                .append(Component.text("SunsetRQ7_", NamedTextColor.AQUA))));
        sender.sendMessage(msg(Component.text("Platform: ", NamedTextColor.GRAY)
                .append(Component.text(
                        me.sunsetrqsv.antiseedcracker.util.PlatformUtil.name(),
                        NamedTextColor.WHITE))));
        if (uc != null) {
            if (uc.isUpToDate()) {
                sender.sendMessage(msg(Component.text(
                        "Update:   ✔ Up to date", NamedTextColor.GREEN)));
            } else if (uc.getLatestVersion() != null) {
                sender.sendMessage(msg(Component.text(
                        "Update:   ✘ v" + uc.getLatestVersion()
                        + " available on Modrinth!", NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(msg(Component.text(
                        "Update:   not yet checked", NamedTextColor.GRAY)));
            }
        }
        sender.sendMessage(Component.empty());
    }

    private void handleHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(msg(Component.text("━━ AntiSeedCracker Commands ━━", NamedTextColor.GOLD)));
        if (sender.hasPermission(PERM_STATUS)) {
            sender.sendMessage(helpLine("/asc status",  "Show which protection layers are active"));
            sender.sendMessage(helpLine("/asc stats",   "Show audit log event counts"));
            sender.sendMessage(helpLine("/asc info",    "Show version, platform, and update status"));
        }
        if (sender.hasPermission(PERM_MANAGE)) {
            sender.sendMessage(helpLine("/asc reload",  "Reload config and restart all protection tasks"));
            sender.sendMessage(helpLine("/asc toggle <feature> [true|false]",
                    "Flip (or set) one feature live, persisted to config.yml"));
        }
        sender.sendMessage(Component.empty());
    }

    private static Component helpLine(String usage, String description) {
        return Component.text("  " + usage + " ", NamedTextColor.WHITE)
                .append(Component.text("— " + description, NamedTextColor.GRAY));
    }

    private static Component msg(Component body) {
        return PREFIX.append(body);
    }

    private static Component statusLine(String label, boolean enabled) {
        Component status = enabled
                ? Component.text("✔ enabled",  NamedTextColor.GREEN)
                : Component.text("✘ disabled", NamedTextColor.RED);
        return Component.text("  ")
                .append(Component.text(label, NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(status);
    }
}

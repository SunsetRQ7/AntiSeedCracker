package me.sunsetrqsv.antiseedcracker.listeners;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.database.DatabaseManager;
import me.sunsetrqsv.antiseedcracker.util.CommandUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandProtectionListener implements Listener {

    private final AntiSeedCrackerPlugin plugin;
    private final Map<UUID, Deque<Long>> probingTimestamps = new ConcurrentHashMap<>();

    public CommandProtectionListener(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        if (!isBlocked(raw)) return;

        Player player = event.getPlayer();
        event.setCancelled(true);
        player.sendMessage(plugin.getPluginConfig().getMessageSeedBlockedPlayer());
        plugin.getLogger().warning(
                "[AntiSeedCracker] Seed command blocked for player "
                + player.getName() + ": " + raw);

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null && plugin.getPluginConfig().isDatabaseLogEvents()) {
            db.logEvent(
                    DatabaseManager.EventType.SEED_COMMAND_BLOCKED,
                    player.getUniqueId().toString(),
                    player.getName(),
                    player.getWorld().getName(),
                    raw);
        }

        recordProbingAttempt(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onServerCommand(ServerCommandEvent event) {
        String raw = event.getCommand().trim();
        if (!isBlocked(raw)) return;

        event.setCancelled(true);
        event.getSender().sendMessage(plugin.getPluginConfig().getMessageSeedBlockedConsole());
        plugin.getLogger().warning(
                "[AntiSeedCracker] Console seed command blocked: " + raw);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        String raw = event.getCommand().trim();
        if (!isBlocked(raw)) return;

        event.setCancelled(true);
        event.getSender().sendMessage(plugin.getPluginConfig().getMessageSeedBlockedConsole());
        plugin.getLogger().warning(
                "[AntiSeedCracker] RCON seed command blocked: " + raw);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        java.util.List<String> extra = plugin.getPluginConfig().getExtraBlockedCommands();
        event.getCompletions().removeIf(c -> {
            String lower = c.toLowerCase();
            if (lower.startsWith("/")) lower = lower.substring(1);
            int colon = lower.indexOf(':');
            if (colon >= 0) lower = lower.substring(colon + 1);
            return lower.equals("seed")
                    || lower.equals("getseed")
                    || lower.equals("worldseed")
                    || extra.contains(lower);
        });
    }

    /**
     * Checks the literal command AND, if it's an {@code /execute ... run ...} chain,
     * the command it ultimately dispatches to — see {@link CommandUtil} for why the
     * unwrap is required (otherwise {@code /execute run seed} sails right past this).
     */
    private boolean isBlocked(String raw) {
        String normalized = CommandUtil.normalizeRoot(raw);
        if (isSeedCommandRoot(normalized) || isExtraBlockedRoot(normalized)) return true;

        if (CommandUtil.startsWithWord(normalized, "execute")) {
            String effective = CommandUtil.resolveExecuteTarget(normalized);
            if (effective != null) {
                return isSeedCommandRoot(effective) || isExtraBlockedRoot(effective);
            }
        }
        return false;
    }

    private static boolean isSeedCommandRoot(String normalized) {
        return normalized.equals("seed")
                || normalized.startsWith("seed ")
                || normalized.equals("getseed")
                || normalized.startsWith("getseed ")
                || normalized.equals("worldseed")
                || normalized.startsWith("worldseed ");
    }

    private boolean isExtraBlockedRoot(String normalized) {
        String root = normalized.split("\\s+", 2)[0];
        return plugin.getPluginConfig().getExtraBlockedCommands().contains(root);
    }

    /**
     * Logs (never blocks — matching {@link StructureReconMonitor}'s non-punishing
     * design) when a player racks up several blocked seed-command attempts in a short
     * window: a pattern consistent with a script/macro trying many command variants
     * back to back to probe for a bypass, rather than one curious manual /seed.
     */
    private void recordProbingAttempt(Player player) {
        if (!plugin.getPluginConfig().isCommandProbingMonitorEnabled()) return;

        int  windowSeconds = plugin.getPluginConfig().getCommandProbingWindowSeconds();
        int  threshold     = plugin.getPluginConfig().getCommandProbingThreshold();
        long now           = System.currentTimeMillis();
        long windowStart   = now - (windowSeconds * 1000L);

        Deque<Long> timestamps = probingTimestamps.computeIfAbsent(
                player.getUniqueId(), k -> new ArrayDeque<>());
        boolean flagged;
        synchronized (timestamps) {
            timestamps.addLast(now);
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            flagged = timestamps.size() >= threshold;
            if (flagged) timestamps.clear();
        }
        if (!flagged) return;

        plugin.getLogger().warning("[AntiSeedCracker] Command-probing pattern detected for "
                + player.getName() + " - triggered " + threshold + "+ blocked seed commands "
                + "within " + windowSeconds + "s. This does not block the player (per design); "
                + "review manually if bypass-scripting is suspected.");

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null && plugin.getPluginConfig().isDatabaseLogEvents()) {
            db.logEvent(DatabaseManager.EventType.COMMAND_PROBING_FLAGGED,
                    player.getUniqueId().toString(),
                    player.getName(),
                    player.getWorld().getName(),
                    "Rapid repeated blocked-command attempts");
        }
    }

    public void removePlayer(UUID uuid) {
        probingTimestamps.remove(uuid);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}

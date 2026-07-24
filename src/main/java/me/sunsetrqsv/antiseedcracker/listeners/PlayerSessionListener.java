package me.sunsetrqsv.antiseedcracker.listeners;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.util.CommandUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.regex.Pattern;

public final class PlayerSessionListener implements Listener {

    private static final Pattern LOCATE_PATTERN =
            Pattern.compile("^/?(?:minecraft:)?locate(?:\\s+(\\w+))?(?:\\s+([\\w:]+))?\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private final AntiSeedCrackerPlugin plugin;

    public PlayerSessionListener(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        initPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getSeedManager().removePlayer(event.getPlayer().getUniqueId());
        if (plugin.getStructureReconMonitor() != null) {
            plugin.getStructureReconMonitor().removePlayer(event.getPlayer().getUniqueId());
        }
        if (plugin.getCommandProtectionListener() != null) {
            plugin.getCommandProtectionListener().removePlayer(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.getSeedIntegrityMonitor().forgetWorld(event.getWorld().getUID());
        if (plugin.getStructureReconMonitor() != null) {
            plugin.getStructureReconMonitor().forgetWorld(event.getWorld().getUID());
        }
    }

    public void initPlayer(Player player) {
        plugin.getSeedManager().assignSeed(player);

        // Only assign on first sight of this player. initPlayer() also re-runs for every
        // already-online player on /asc reload (and /asc toggle, which reloads internally) -
        // unconditionally reassigning here would silently move an online player's fake
        // stronghold on every unrelated config change, defeating the very answer-consistency
        // /locate and Eye of Ender redirection depend on to not tip off a suspicious player.
        if (!plugin.getSeedManager().hasFakeStronghold(player.getUniqueId())) {
            plugin.getSeedManager().assignFakeStronghold(
                    player,
                    plugin.getPluginConfig().getFakeStrongholdMinDist(),
                    plugin.getPluginConfig().getFakeStrongholdMaxDist()
            );
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getPluginConfig().isSpoofLocateEnabled()) return;

        String raw          = event.getMessage().trim();
        String normalized   = CommandUtil.normalizeRoot(raw);
        String locateCommand = resolveLocateCommand(normalized);
        if (locateCommand == null) return;

        event.setCancelled(true);

        Player player       = event.getPlayer();
        String structureArg = extractStructureArg(locateCommand);
        int[]  fakeCoords   = buildFakeLocateCoords(player, structureArg);
        int    fakeX        = fakeCoords[0];
        int    fakeZ        = fakeCoords[1];

        double dx   = fakeX - player.getLocation().getX();
        double dz   = fakeZ - player.getLocation().getZ();
        int    dist = (int) Math.sqrt(dx * dx + dz * dz);

        String displayName = formatStructureName(structureArg);
        player.sendMessage(
                Component.text("The nearest " + displayName + " is at ", NamedTextColor.WHITE)
                        .append(Component.text("[" + fakeX + ", ~, " + fakeZ + "]", NamedTextColor.GREEN))
                        .append(Component.text(" (approximately ", NamedTextColor.WHITE))
                        .append(Component.text(String.valueOf(dist), NamedTextColor.GREEN))
                        .append(Component.text(" blocks away)", NamedTextColor.WHITE)));
    }

    /**
     * Returns the normalized {@code locate ...} command to spoof, checking both the
     * literal command and — since vanilla's {@code /execute} re-dispatches its trailing
     * command without re-firing this event — any {@code /execute ... run locate ...}
     * wrapping. Returns {@code null} if this isn't a locate command at all.
     */
    private String resolveLocateCommand(String normalized) {
        if (LOCATE_PATTERN.matcher("/" + normalized).matches()) {
            return normalized;
        }
        if (CommandUtil.startsWithWord(normalized, "execute")) {
            String effective = CommandUtil.resolveExecuteTarget(normalized);
            if (effective != null && LOCATE_PATTERN.matcher("/" + effective).matches()) {
                return effective;
            }
        }
        return null;
    }

    private String extractStructureArg(String command) {
        String[] tokens = command.replaceFirst("^/", "").split("\\s+");
        int startIdx = 1;
        if (tokens.length > startIdx) {
            String t1 = tokens[startIdx].toLowerCase();
            if (t1.equals("structure") || t1.equals("biome")) startIdx = 2;
        }
        if (tokens.length > startIdx) return tokens[startIdx].toLowerCase();
        return "minecraft:stronghold";
    }

    private int[] buildFakeLocateCoords(Player player, String structureArg) {
        String key = structureArg.contains(":")
                ? structureArg.substring(structureArg.indexOf(':') + 1)
                : structureArg;

        if (key.equals("stronghold")) {
            int[] fakeStronghold = plugin.getSeedManager().getFakeStronghold(player.getUniqueId());
            if (fakeStronghold != null) return fakeStronghold;
        }
        int maxOffset = plugin.getPluginConfig().getSpoofLocateMaxOffset();
        int minOffset = maxOffset / 4;
        return plugin.getSeedManager()
                .getOrAssignFakeStructureLocation(player, key, minOffset, maxOffset);
    }

    private String formatStructureName(String id) {
        String   name  = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}

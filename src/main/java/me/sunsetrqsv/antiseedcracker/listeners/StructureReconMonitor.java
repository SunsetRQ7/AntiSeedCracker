package me.sunsetrqsv.antiseedcracker.listeners;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.database.DatabaseManager;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureReconMonitor implements Listener {

    private final AntiSeedCrackerPlugin plugin;
    private final Set<Structure> trackedStructures;

    private final Map<UUID, Set<Long>>          visitedChunks       = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>>        visitTimestamps     = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Boolean>> chunkQualifiesCache = new ConcurrentHashMap<>();

    public StructureReconMonitor(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
        this.trackedStructures = resolveTrackedStructures(
                plugin, plugin.getPluginConfig().getStructureReconTrackedStructures());
    }

    private static Set<Structure> resolveTrackedStructures(AntiSeedCrackerPlugin plugin, List<String> names) {
        Set<Structure> set = new HashSet<>();
        for (String name : names) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            if (key.contains(":")) key = key.substring(key.indexOf(':') + 1);
            Structure structure = Registry.STRUCTURE.get(NamespacedKey.minecraft(key));
            if (structure == null) {
                plugin.getLogger().warning("[AntiSeedCracker] structure_recon_monitor: unknown "
                        + "structure '" + name + "' in tracked_structures, ignoring.");
                continue;
            }
            set.add(structure);
        }
        return set;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getPluginConfig().isStructureReconMonitorEnabled()) return;

        int fromChunkX = event.getFrom().getBlockX() >> 4;
        int fromChunkZ = event.getFrom().getBlockZ() >> 4;
        int toChunkX   = event.getTo().getBlockX() >> 4;
        int toChunkZ   = event.getTo().getBlockZ() >> 4;
        if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) return;

        UUID worldUid = event.getTo().getWorld().getUID();
        long chunkKey = packChunkKey(toChunkX, toChunkZ);

        Map<Long, Boolean> worldCache = chunkQualifiesCache.computeIfAbsent(
                worldUid, k -> new ConcurrentHashMap<>());
        Boolean qualifies = worldCache.get(chunkKey);
        if (qualifies == null) {
            Chunk chunk = event.getTo().getChunk();
            Collection<GeneratedStructure> structures;
            try {
                structures = chunk.getStructures();
            } catch (Exception e) {
                return;
            }
            qualifies = false;
            for (GeneratedStructure s : structures) {
                if (trackedStructures.contains(s.getStructure())) {
                    qualifies = true;
                    break;
                }
            }
            worldCache.put(chunkKey, qualifies);
        }
        if (!qualifies) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Set<Long> visited = visitedChunks.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (!visited.add(chunkKey)) return;

        int  windowSeconds = plugin.getPluginConfig().getStructureReconWindowSeconds();
        int  threshold     = plugin.getPluginConfig().getStructureReconThreshold();
        long now           = System.currentTimeMillis();
        long windowStart   = now - (windowSeconds * 1000L);

        Deque<Long> timestamps = visitTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= threshold) {
                timestamps.clear();
                flagPlayer(player);
            }
        }
    }

    /**
     * Evicts the chunk-qualifies cache entry when its chunk unloads. Without this the
     * cache — one boolean per chunk any player has ever passed through — grows without
     * bound for the entire server uptime; tying it to chunk lifetime instead bounds it
     * to "currently loaded chunks," which is what actually matters for lookup cost.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Map<Long, Boolean> worldCache = chunkQualifiesCache.get(event.getWorld().getUID());
        if (worldCache == null) return;
        worldCache.remove(packChunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }

    private static long packChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private void flagPlayer(Player player) {
        plugin.getLogger().warning("[AntiSeedCracker] Structure reconnaissance pattern detected for "
                + player.getName() + " - visited multiple seed-relevant structures in a short "
                + "window. This does not block the player (per design); review manually if "
                + "seed-cracking tool use is suspected.");

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null && plugin.getPluginConfig().isDatabaseLogEvents()) {
            db.logEvent(DatabaseManager.EventType.STRUCTURE_RECON_FLAGGED,
                    player.getUniqueId().toString(),
                    player.getName(),
                    player.getWorld().getName(),
                    "Rapid multi-structure visitation pattern");
        }
    }

    public void removePlayer(UUID uuid) {
        visitedChunks.remove(uuid);
        visitTimestamps.remove(uuid);
    }

    public void forgetWorld(UUID worldUid) {
        chunkQualifiesCache.remove(worldUid);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}

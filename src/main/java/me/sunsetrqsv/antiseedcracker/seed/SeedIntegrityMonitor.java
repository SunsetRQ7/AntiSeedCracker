package me.sunsetrqsv.antiseedcracker.seed;

import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class SeedIntegrityMonitor {

    private final Map<UUID, Long> firstObservedSeed = new ConcurrentHashMap<>();
    private final Logger logger;

    public SeedIntegrityMonitor(Logger logger) {
        this.logger = logger;
    }

    public void check(Iterable<World> worlds) {
        for (World world : worlds) {
            long current = world.getSeed();
            Long baseline = firstObservedSeed.putIfAbsent(world.getUID(), current);
            if (baseline != null && baseline != current) {
                logger.warning("[AntiSeedCracker] Seed integrity check FAILED for world '"
                        + world.getName() + "' - the generation seed changed after the server "
                        + "started. This plugin never modifies it; another plugin or external "
                        + "tool is altering world generation state. Investigate immediately, as "
                        + "this can corrupt newly generated chunks.");
                firstObservedSeed.put(world.getUID(), current);
            }
        }
    }

    public void forgetWorld(UUID worldUID) {
        firstObservedSeed.remove(worldUID);
    }
}

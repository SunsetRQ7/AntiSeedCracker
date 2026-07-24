package me.sunsetrqsv.antiseedcracker.tasks;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import me.sunsetrqsv.antiseedcracker.scheduler.CancelableTask;
import me.sunsetrqsv.antiseedcracker.scheduler.FoliaSchedulerUtil;

public final class SeedBroadcastTask implements Runnable {

    public static final long PERIOD_MS = 15_000L;

    private final AntiSeedCrackerPlugin plugin;
    private volatile CancelableTask handle;

    public SeedBroadcastTask(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (handle != null) {
            handle.cancel();
        }
        handle = FoliaSchedulerUtil.scheduleAsyncRepeating(
                plugin, this, PERIOD_MS, PERIOD_MS);
    }

    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    @Override
    public void run() {
        if (plugin.getPluginConfig().isSeedIntegrityMonitorEnabled()) {
            plugin.getSeedIntegrityMonitor().check(plugin.getServer().getWorlds());
        }

        plugin.getSeedManager().ensureAllPlayersHaveSeeds(
                plugin.getServer().getOnlinePlayers());
    }
}

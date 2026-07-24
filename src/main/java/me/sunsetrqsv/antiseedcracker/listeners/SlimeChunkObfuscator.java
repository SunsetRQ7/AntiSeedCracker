package me.sunsetrqsv.antiseedcracker.listeners;

import me.sunsetrqsv.antiseedcracker.AntiSeedCrackerPlugin;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class SlimeChunkObfuscator implements Listener {

    private final AntiSeedCrackerPlugin plugin;

    public SlimeChunkObfuscator(AntiSeedCrackerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSlimeSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.SLIME) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!plugin.getPluginConfig().getSlimeChunkObfuscationWorlds()
                .contains(event.getEntity().getWorld().getName())) return;

        event.setCancelled(true);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}

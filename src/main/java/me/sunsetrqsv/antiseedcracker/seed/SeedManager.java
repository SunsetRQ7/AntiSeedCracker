package me.sunsetrqsv.antiseedcracker.seed;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SeedManager {

    private final ConcurrentHashMap<UUID, Long>   playerSeeds    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, long[]> fakeStrongholds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, int[]>> fakeStructureLocations =
            new ConcurrentHashMap<>();

    private final SecureRandom rng = new SecureRandom();

    public long assignSeed(Player player) {
        long seed = generateSeed();
        playerSeeds.put(player.getUniqueId(), seed);
        return seed;
    }

    public long getFakeSeed(Player player) {
        return playerSeeds.computeIfAbsent(player.getUniqueId(), k -> generateSeed());
    }

    public void removePlayer(UUID uuid) {
        playerSeeds.remove(uuid);
        fakeStrongholds.remove(uuid);
        fakeStructureLocations.remove(uuid);
    }

    public int rotateAll() {
        int count = 0;
        for (UUID uuid : playerSeeds.keySet()) {
            playerSeeds.put(uuid, generateSeed());
            count++;
        }
        return count;
    }

    public void ensureAllPlayersHaveSeeds(Collection<? extends Player> players) {
        for (Player player : players) {
            playerSeeds.computeIfAbsent(player.getUniqueId(), k -> generateSeed());
        }
    }

    /**
     * Assigns a fake stronghold (x, y, z) to the player, replacing any previous one.
     * The y is a plausible stronghold-band height so {@link org.bukkit.entity.EnderSignal}
     * can be given a full 3D target location, not just a horizontal bearing.
     */
    public int[] assignFakeStronghold(Player player, int minDist, int maxDist) {
        double angle  = rng.nextDouble() * 2.0 * Math.PI;
        int    dist   = minDist + rng.nextInt(Math.max(1, maxDist - minDist + 1));
        int    x      = (int) Math.round(Math.cos(angle) * dist);
        int    z      = (int) Math.round(Math.sin(angle) * dist);

        int minY = player.getWorld().getMinHeight() + 8;
        int maxY = Math.max(minY + 1, Math.min(player.getWorld().getMaxHeight() - 8, minY + 72));
        int y    = minY + rng.nextInt(maxY - minY);

        fakeStrongholds.put(player.getUniqueId(), new long[]{x, y, z});
        return new int[]{x, z};
    }

    /** Horizontal (x, z) of the player's fake stronghold, or {@code null} if none assigned yet. */
    public int[] getFakeStronghold(UUID uuid) {
        long[] packed = fakeStrongholds.get(uuid);
        if (packed == null) return null;
        return new int[]{(int) packed[0], (int) packed[2]};
    }

    /**
     * Y of the player's fake stronghold, for continuous Eye of Ender homing (see
     * {@code EyeOfEnderProtector}). Returns {@code fallback} if none has been assigned yet.
     */
    public int getFakeStrongholdY(UUID uuid, int fallback) {
        long[] packed = fakeStrongholds.get(uuid);
        return packed != null ? (int) packed[1] : fallback;
    }

    /** True if this player already has a fake stronghold assigned. */
    public boolean hasFakeStronghold(UUID uuid) {
        return fakeStrongholds.containsKey(uuid);
    }

    /**
     * Returns a stable fake location for the given player + structure key, generating
     * and caching one on first use. Without this, repeated {@code /locate} calls for the
     * same non-stronghold structure would each roll a brand-new random answer — an
     * inconsistency a suspicious player could notice simply by re-running the command
     * in place, immediately tipping them off that the result is fabricated.
     */
    public int[] getOrAssignFakeStructureLocation(Player player, String structureKey,
                                                   int minOffset, int maxOffset) {
        ConcurrentHashMap<String, int[]> perPlayer = fakeStructureLocations
                .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        return perPlayer.computeIfAbsent(structureKey, k -> {
            double angle = rng.nextDouble() * 2.0 * Math.PI;
            int    dist  = minOffset + rng.nextInt(Math.max(1, maxOffset - minOffset + 1));
            int    baseX = (int) player.getLocation().getX();
            int    baseZ = (int) player.getLocation().getZ();
            int    x     = ((baseX + (int) Math.round(Math.cos(angle) * dist)) >> 4) << 4;
            int    z     = ((baseZ + (int) Math.round(Math.sin(angle) * dist)) >> 4) << 4;
            return new int[]{x, z};
        });
    }

    public int trackedCount() {
        return playerSeeds.size();
    }

    private long generateSeed() {
        return rng.nextLong();
    }
}

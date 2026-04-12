package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ConcurrentHashMap;

public class RedstoneThrottler {

    // Use ConcurrentHashMap for safety if any async chunk-loading path fires NeighborNotifyEvent
    private static final ConcurrentHashMap<Long, Integer> updatesPerChunk = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> lastLogTimePerChunk = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> peakCounts = new ConcurrentHashMap<>();
    private static final long LOG_COOLDOWN_MS = 10_000L;

    // Dimension-aware chunk key: pack dimension hash into upper bits
    private static long dimChunkKey(World world, int chunkX, int chunkZ) {
        // Use dimension registry name hash in upper 32 bits, chunk long in lower 32
        int dimHash = world.dimension().location().hashCode();
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        return ((long) dimHash << 32) ^ chunkKey;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!ServerConfig.ENABLE_REDSTONE_THROTTLE.get()) {
            return;
        }

        World world = (World) event.getWorld();
        if (world.isClientSide()) {
            return;
        }

        BlockPos pos = event.getPos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long key = dimChunkKey(world, chunkX, chunkZ);

        int count = updatesPerChunk.merge(key, 1, Integer::sum);
        int threshold = ServerConfig.REDSTONE_UPDATES_PER_CHUNK_PER_TICK.get();

        // Track peak for more informative logging
        peakCounts.merge(key, count, Integer::max);

        if (count > threshold) {
            event.setCanceled(true);

            if (count == threshold + 1) {
                long now = System.currentTimeMillis();
                Long lastLog = lastLogTimePerChunk.get(key);
                if (lastLog == null || now - lastLog >= LOG_COOLDOWN_MS) {
                    lastLogTimePerChunk.put(key, now);
                    int peak = peakCounts.getOrDefault(key, count);
                    ThaumicWards.LOGGER.warn(
                            "Redstone throttle: chunk [{}, {}] in {} exceeded {} updates/tick (peak: {})",
                            chunkX, chunkZ, world.dimension().location(), threshold, peak);
                }
            }
        }
    }

    /**
     * Called every tick to reset per-tick counters.
     * Also prunes stale log time entries to prevent memory leak
     * when players explore many chunks.
     */
    public static void resetCounts() {
        updatesPerChunk.clear();
        peakCounts.clear();

        // Prune stale log entries older than 60 seconds to prevent unbounded growth
        if (!lastLogTimePerChunk.isEmpty()) {
            long now = System.currentTimeMillis();
            lastLogTimePerChunk.entrySet().removeIf(e -> now - e.getValue() > 60_000L);
        }
    }

    public static int getActiveChunkCount() {
        return updatesPerChunk.size();
    }

    public static void reset() {
        updatesPerChunk.clear();
        lastLogTimePerChunk.clear();
        peakCounts.clear();
    }
}

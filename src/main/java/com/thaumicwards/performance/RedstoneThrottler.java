package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;

public class RedstoneThrottler {

    private static final HashMap<Long, Integer> updatesPerChunk = new HashMap<>();
    private static final HashMap<Long, Long> lastLogTimePerChunk = new HashMap<>();
    private static final long LOG_COOLDOWN_MS = 10_000L;

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
        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);

        int count = updatesPerChunk.merge(chunkKey, 1, Integer::sum);
        int threshold = ServerConfig.REDSTONE_UPDATES_PER_CHUNK_PER_TICK.get();

        if (count > threshold) {
            event.setCanceled(true);

            if (count == threshold + 1) {
                long now = System.currentTimeMillis();
                Long lastLog = lastLogTimePerChunk.get(chunkKey);
                if (lastLog == null || now - lastLog >= LOG_COOLDOWN_MS) {
                    lastLogTimePerChunk.put(chunkKey, now);
                    ThaumicWards.LOGGER.warn(
                            "Redstone throttle: chunk [{}, {}] exceeded {} updates/tick",
                            pos.getX() >> 4, pos.getZ() >> 4, threshold);
                }
            }
        }
    }

    public static void resetCounts() {
        updatesPerChunk.clear();
    }

    public static int getActiveChunkCount() {
        return updatesPerChunk.size();
    }

    public static void reset() {
        updatesPerChunk.clear();
        lastLogTimePerChunk.clear();
    }
}

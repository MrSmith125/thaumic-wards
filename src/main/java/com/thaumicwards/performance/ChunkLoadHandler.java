package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class ChunkLoadHandler {

    private static final AtomicInteger loadsThisTick = new AtomicInteger(0);
    private static long lastTickTime = 0;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!ServerConfig.ENABLE_CHUNK_LOAD_OPTIMIZATION.get()) {
            return;
        }

        if (event.getWorld() == null || event.getWorld().isClientSide()) {
            return;
        }

        // Reset counter each tick
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTickTime > 50) { // New tick (~20 TPS = 50ms)
            loadsThisTick.set(0);
            lastTickTime = currentTime;
        }

        int maxLoads = ServerConfig.MAX_CHUNK_LOADS_PER_TICK.get();
        int currentLoads = loadsThisTick.incrementAndGet();

        if (currentLoads > maxLoads) {
            // Log excessive loading for diagnostics
            if (currentLoads == maxLoads + 1) {
                ChunkPos pos = event.getChunk().getPos();
                ThaumicWards.LOGGER.debug("Chunk load throttle active: {} loads this tick (max: {}), chunk [{}, {}]",
                        currentLoads, maxLoads, pos.x, pos.z);
            }
        }
    }

    public static int getLoadsThisTick() {
        return loadsThisTick.get();
    }

    public static void resetCounter() {
        loadsThisTick.set(0);
    }
}

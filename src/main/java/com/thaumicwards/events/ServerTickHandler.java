package com.thaumicwards.events;

import com.thaumicwards.performance.ChunkLoadHandler;
import com.thaumicwards.performance.TickRateManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        if (!(event.world instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) event.world;

        // Update tick rate manager for distant chunk tracking
        TickRateManager.tick(world);

        // Reset chunk load counter each tick
        ChunkLoadHandler.resetCounter();

        // PregenManager.tick() will be called here once implemented
    }
}

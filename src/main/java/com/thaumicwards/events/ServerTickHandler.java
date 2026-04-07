package com.thaumicwards.events;

import com.thaumicwards.performance.ChunkLoadHandler;
import com.thaumicwards.performance.TickRateManager;
import com.thaumicwards.pregen.PregenManager;
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

        // Run chunk pre-generation if active
        PregenManager.tick(world);
    }
}

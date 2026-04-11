package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.entity.passive.AmbientEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EntityTickHandler {

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!ServerConfig.ENABLE_CHUNK_LOAD_OPTIMIZATION.get()) {
            return;
        }

        LivingEntity entity = event.getEntityLiving();

        // Never skip player ticks
        if (entity instanceof PlayerEntity) {
            return;
        }

        // Only apply on server side
        if (entity.level.isClientSide) {
            return;
        }

        int chunkX = ((int) entity.getX()) >> 4;
        int chunkZ = ((int) entity.getZ()) >> 4;

        if (TickRateManager.isDistantChunk(chunkX, chunkZ)) {
            int interval = ServerConfig.DISTANT_CHUNK_TICK_INTERVAL.get();

            // Animals and ambient mobs can tolerate much larger skip intervals
            if (entity instanceof AnimalEntity || entity instanceof AmbientEntity) {
                interval *= 3;
            }

            if (entity.tickCount % interval != 0) {
                // Cancel the entire tick event — prevents AI, sensing, goal evaluation
                event.setCanceled(true);
            }
        }
    }
}

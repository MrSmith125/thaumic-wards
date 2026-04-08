package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
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
            if (entity.tickCount % interval != 0) {
                // Freeze entity AI and movement for this tick
                entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
                if (entity instanceof MobEntity) {
                    ((MobEntity) entity).getNavigation().stop();
                }
            }
        }
    }
}

package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.AmbientEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Proactively reduces mob spawn rates under TPS pressure.
 * Works with the AdaptiveThrottler levels to deny a percentage
 * of spawn attempts before they create entities.
 *
 * This is complementary to EntityCleanup (which removes excess entities after the fact)
 * and InControl spawn.json (which sets hard caps). This handler reduces the RATE
 * at which entities are added, smoothing the load curve.
 */
public class SpawnThrottler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ServerConfig.ADAPTIVE_THROTTLE_ENABLED.get()) return;

        World world = event.getEntity().level;
        if (world.isClientSide()) return;

        // Don't throttle when at NONE level
        if (AdaptiveThrottler.getCurrentLevel() == AdaptiveThrottler.ThrottleLevel.NONE) return;

        LivingEntity entity = event.getEntityLiving();

        // Never throttle bosses or special entities
        if (!(entity instanceof IMob) && !(entity instanceof AnimalEntity) && !(entity instanceof AmbientEntity)) {
            return; // Skip non-standard living entities (NPCs, villagers, etc.)
        }

        boolean isPassive = entity instanceof AnimalEntity || entity instanceof AmbientEntity;
        double cancelChance = AdaptiveThrottler.getSpawnCancelChance(isPassive);

        if (cancelChance > 0 && ThreadLocalRandom.current().nextDouble() < cancelChance) {
            event.setResult(Event.Result.DENY);
        }
    }
}

package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityCleanup {

    private static int tickCounter = 0;
    private static boolean warningBroadcast = false;
    private static final int WARNING_ADVANCE_TICKS = 600;

    @SubscribeEvent
    public static void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.world instanceof ServerWorld)) return;
        if (((ServerWorld) event.world).dimension() != World.OVERWORLD) return;
        if (!ServerConfig.ENTITY_CLEANUP_ENABLED.get()) { tickCounter = 0; return; }

        int interval = ServerConfig.ENTITY_CLEANUP_INTERVAL_TICKS.get();
        tickCounter++;

        if (!warningBroadcast && tickCounter >= interval - WARNING_ADVANCE_TICKS) {
            warningBroadcast = true;
            ServerWorld world = (ServerWorld) event.world;
            if (world.getServer() != null) {
                world.getServer().getPlayerList().getPlayers().forEach(p ->
                    p.displayClientMessage(new StringTextComponent("[Thaumic Wards] ")
                        .withStyle(TextFormatting.DARK_PURPLE)
                        .append(new StringTextComponent("Entity cleanup in 30 seconds.")
                            .withStyle(TextFormatting.YELLOW)), false));
            }
        }

        if (tickCounter >= interval) {
            tickCounter = 0;
            warningBroadcast = false;
            runCleanup(((ServerWorld) event.world).getServer());
        }
    }

    public static CleanupResult runCleanup(MinecraftServer server) {
        if (server == null) return new CleanupResult(0, 0, 0);
        int removedItems = 0, removedXp = 0, total = 0;
        Map<String, Integer> counts = new HashMap<>();
        int itemAge = ServerConfig.ENTITY_CLEANUP_ITEM_AGE_TICKS.get();
        int xpAge = ServerConfig.ENTITY_CLEANUP_XP_AGE_TICKS.get();

        for (ServerWorld world : server.getAllLevels()) {
            List<Entity> snapshot = new ArrayList<>();
            for (Entity e : world.getAllEntities()) snapshot.add(e);

            for (Entity entity : snapshot) {
                if (!entity.isAlive()) continue;
                String type = entity.getType().getRegistryName() != null
                        ? entity.getType().getRegistryName().toString() : entity.getClass().getSimpleName();
                counts.merge(type, 1, Integer::sum);
                total++;

                if (ServerConfig.ENTITY_CLEANUP_ITEMS_ENABLED.get() && entity instanceof ItemEntity) {
                    if (entity.tickCount >= itemAge) { entity.remove(); removedItems++; }
                } else if (ServerConfig.ENTITY_CLEANUP_XP_ENABLED.get() && entity instanceof ExperienceOrbEntity) {
                    if (entity.tickCount >= xpAge) { entity.remove(); removedXp++; }
                }
            }
        }

        int warn = ServerConfig.ENTITY_CLEANUP_WARN_THRESHOLD.get();
        counts.entrySet().stream().filter(e -> e.getValue() > warn).forEach(e ->
            ThaumicWards.LOGGER.warn("[EntityCleanup] '{}' has {} instances (threshold: {})",
                    e.getKey(), e.getValue(), warn));

        if (removedItems > 0 || removedXp > 0) {
            ThaumicWards.LOGGER.info("[EntityCleanup] Removed {} items, {} XP orbs. Total: {}",
                    removedItems, removedXp, total);
        }
        return new CleanupResult(removedItems, removedXp, total);
    }

    public static void reset() { tickCounter = 0; warningBroadcast = false; }

    public static class CleanupResult {
        public final int removedItems, removedXpOrbs, totalScanned;
        public CleanupResult(int items, int xp, int total) {
            this.removedItems = items; this.removedXpOrbs = xp; this.totalScanned = total;
        }
    }
}

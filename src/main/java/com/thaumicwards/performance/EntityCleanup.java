package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AmbientEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.stream.Collectors;

public class EntityCleanup {

    private static int tickCounter = 0;
    private static int hardCapCounter = 0;
    private static boolean warningBroadcast = false;
    private static final int WARNING_ADVANCE_TICKS = 600;

    @SubscribeEvent
    public static void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.world instanceof ServerWorld)) return;
        if (((ServerWorld) event.world).dimension() != World.OVERWORLD) return;

        // Hard cap enforcement runs on its own faster interval
        if (ServerConfig.ENTITY_HARD_CAPS_ENABLED.get()) {
            hardCapCounter++;
            int hardCapInterval = ServerConfig.ENTITY_HARD_CAP_CHECK_INTERVAL.get();
            if (hardCapCounter >= hardCapInterval) {
                hardCapCounter = 0;
                enforceHardCaps(((ServerWorld) event.world).getServer());
            }
        }

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

    /**
     * Enforces hard entity caps per type and totals.
     * Removes oldest entities first (lowest tickCount) to minimise impact on freshly-spawned mobs.
     */
    public static int enforceHardCaps(MinecraftServer server) {
        if (server == null) return 0;
        int totalRemoved = 0;

        boolean capsEnabled = ServerConfig.ENTITY_HARD_CAPS_ENABLED.get();
        if (!capsEnabled) return 0;

        int capTotal      = ServerConfig.ENTITY_CAP_TOTAL.get();
        int capPerType    = ServerConfig.ENTITY_CAP_PER_TYPE.get();
        int capPassive    = ServerConfig.ENTITY_CAP_PASSIVE_MOBS.get();
        int capHostile    = ServerConfig.ENTITY_CAP_HOSTILE_MOBS.get();
        int capMinecarts  = ServerConfig.ENTITY_CAP_MINECARTS.get();

        // Collect all live, non-player entities across all dimensions
        List<Entity> allEntities = new ArrayList<>();
        Map<String, List<Entity>> byType = new LinkedHashMap<>();
        List<Entity> passiveList  = new ArrayList<>();
        List<Entity> hostileList  = new ArrayList<>();
        List<Entity> minecartList = new ArrayList<>();

        for (ServerWorld world : server.getAllLevels()) {
            for (Entity e : world.getAllEntities()) {
                if (!e.isAlive()) continue;
                if (e instanceof PlayerEntity) continue;

                allEntities.add(e);

                String typeKey = e.getType().getRegistryName() != null
                        ? e.getType().getRegistryName().toString()
                        : e.getClass().getSimpleName();
                byType.computeIfAbsent(typeKey, k -> new ArrayList<>()).add(e);

                if (e instanceof AbstractMinecartEntity) {
                    minecartList.add(e);
                } else if (e instanceof AnimalEntity || e instanceof AmbientEntity) {
                    passiveList.add(e);
                } else if (e instanceof IMob) {
                    hostileList.add(e);
                }
            }
        }

        // --- Per-type caps ---
        if (capPerType > 0) {
            for (Map.Entry<String, List<Entity>> entry : byType.entrySet()) {
                List<Entity> list = entry.getValue();
                if (list.size() > capPerType) {
                    int excess = list.size() - capPerType;
                    // Sort oldest (highest tickCount) first — they are farthest from player activity
                    list.sort(Comparator.comparingInt(e -> -e.tickCount));
                    for (int i = 0; i < excess; i++) {
                        Entity e = list.get(i);
                        if (e.isAlive()) { e.remove(); totalRemoved++; }
                    }
                    ThaumicWards.LOGGER.info("[EntityCleanup] Hard cap: removed {} excess '{}' (cap={})",
                            excess, entry.getKey(), capPerType);
                }
            }
        }

        // --- Passive mob cap ---
        if (capPassive > 0 && passiveList.size() > capPassive) {
            int excess = passiveList.size() - capPassive;
            passiveList.sort(Comparator.comparingInt(e -> -e.tickCount));
            for (int i = 0; i < excess; i++) {
                Entity e = passiveList.get(i);
                if (e.isAlive()) { e.remove(); totalRemoved++; }
            }
            ThaumicWards.LOGGER.info("[EntityCleanup] Hard cap: removed {} excess passive mobs (cap={})",
                    excess, capPassive);
        }

        // --- Hostile mob cap ---
        if (capHostile > 0 && hostileList.size() > capHostile) {
            int excess = hostileList.size() - capHostile;
            hostileList.sort(Comparator.comparingInt(e -> -e.tickCount));
            for (int i = 0; i < excess; i++) {
                Entity e = hostileList.get(i);
                if (e.isAlive()) { e.remove(); totalRemoved++; }
            }
            ThaumicWards.LOGGER.info("[EntityCleanup] Hard cap: removed {} excess hostile mobs (cap={})",
                    excess, capHostile);
        }

        // --- Minecart cap ---
        if (capMinecarts > 0 && minecartList.size() > capMinecarts) {
            int excess = minecartList.size() - capMinecarts;
            minecartList.sort(Comparator.comparingInt(e -> -e.tickCount));
            for (int i = 0; i < excess; i++) {
                Entity e = minecartList.get(i);
                if (e.isAlive()) { e.remove(); totalRemoved++; }
            }
            ThaumicWards.LOGGER.info("[EntityCleanup] Hard cap: removed {} excess minecarts (cap={})",
                    excess, capMinecarts);
        }

        // --- Global total cap (last resort after per-category enforcement) ---
        if (capTotal > 0) {
            // Recount live entities after previous removals
            int liveCount = 0;
            List<Entity> remaining = new ArrayList<>();
            for (ServerWorld world : server.getAllLevels()) {
                for (Entity e : world.getAllEntities()) {
                    if (!e.isAlive() || e instanceof PlayerEntity) continue;
                    liveCount++;
                    remaining.add(e);
                }
            }
            if (liveCount > capTotal) {
                int excess = liveCount - capTotal;
                // Remove oldest non-player entities first
                remaining.sort(Comparator.comparingInt(e -> -e.tickCount));
                for (int i = 0; i < excess && i < remaining.size(); i++) {
                    Entity e = remaining.get(i);
                    if (e.isAlive()) { e.remove(); totalRemoved++; }
                }
                ThaumicWards.LOGGER.warn("[EntityCleanup] TOTAL CAP TRIGGERED: removed {} entities (cap={}, was={})",
                        excess, capTotal, liveCount);
            }
        }

        if (totalRemoved > 0) {
            ThaumicWards.LOGGER.info("[EntityCleanup] Hard cap pass complete: {} entities removed", totalRemoved);
        }
        return totalRemoved;
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

    public static void reset() { tickCounter = 0; hardCapCounter = 0; warningBroadcast = false; }

    public static class CleanupResult {
        public final int removedItems, removedXpOrbs, totalScanned;
        public CleanupResult(int items, int xp, int total) {
            this.removedItems = items; this.removedXpOrbs = xp; this.totalScanned = total;
        }
    }
}

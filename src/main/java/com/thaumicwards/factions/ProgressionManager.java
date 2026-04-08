package com.thaumicwards.factions;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the hybrid rank progression system.
 * Awards Arcane Power for playtime and faction kills, auto-promotes
 * players through INITIATE → ACOLYTE → WARLOCK.
 */
public class ProgressionManager {

    private static final Map<UUID, PlayerProgressionData> playerData = new HashMap<>();
    private static ServerWorld storageWorld = null;

    public static void init(ServerWorld overworld) {
        storageWorld = overworld;
        ProgressionSavedData.get(overworld); // Triggers load
        ThaumicWards.LOGGER.info("ProgressionManager initialized with {} player records.", playerData.size());
    }

    /**
     * Called every 1200 ticks (1 minute) from ServerTickHandler.
     * Awards playtime points to all online faction members and checks for rank-ups.
     */
    public static void tick(ServerWorld world) {
        for (ServerPlayerEntity player : world.players()) {
            UUID playerId = player.getUUID();

            // Only award points to faction members
            if (!FactionManager.isPlayerInFaction(playerId)) continue;

            PlayerProgressionData data = getOrCreate(playerId);
            FactionRank oldEarned = data.getEarnedRank();

            data.addPlaytimePoints();

            FactionRank newEarned = data.getEarnedRank();

            // Check for auto-rank-up
            if (newEarned.getLevel() > oldEarned.getLevel()) {
                tryAutoPromote(player, newEarned);
            }
        }
        markDirty();
    }

    /**
     * Called when a player kills an enemy faction member.
     */
    public static void onFactionKill(UUID killerId, UUID victimId, ServerPlayerEntity killer) {
        if (!FactionManager.isPlayerInFaction(killerId)) return;

        PlayerProgressionData data = getOrCreate(killerId);
        FactionRank oldEarned = data.getEarnedRank();

        data.addKillPoints();

        FactionRank newEarned = data.getEarnedRank();

        // Check for auto-rank-up
        if (newEarned.getLevel() > oldEarned.getLevel() && killer != null) {
            tryAutoPromote(killer, newEarned);
        }
        markDirty();
    }

    /**
     * Attempts to auto-promote a player to their earned rank.
     * Only promotes if their current faction rank is below the earned rank
     * and the earned rank is auto-earnable (up to WARLOCK).
     */
    private static void tryAutoPromote(ServerPlayerEntity player, FactionRank earnedRank) {
        Faction faction = FactionManager.getPlayerFaction(player.getUUID());
        if (faction == null) return;

        FactionRank currentRank = faction.getRank(player.getUUID());
        if (currentRank == null) return;

        // Don't auto-demote leaders or archmages
        if (currentRank == FactionRank.LEADER || currentRank == FactionRank.ARCHMAGE) return;

        // Only auto-promote up to WARLOCK
        if (earnedRank.getLevel() > FactionRank.WARLOCK.getLevel()) {
            earnedRank = FactionRank.WARLOCK;
        }

        if (earnedRank.getLevel() > currentRank.getLevel()) {
            faction.setRank(player.getUUID(), earnedRank);

            // Notify the player
            player.displayClientMessage(new StringTextComponent(
                    String.format("Your arcane power surges! You have ascended to %s!", earnedRank.getDisplayName()))
                    .withStyle(earnedRank.getColor(), TextFormatting.BOLD), false);

            player.displayClientMessage(new StringTextComponent(
                    String.format("You can now claim up to %d personal territories.", earnedRank.getMaxPersonalClaims()))
                    .withStyle(TextFormatting.GRAY), false);

            ThaumicWards.LOGGER.info("Player {} auto-promoted to {} (Arcane Power: {})",
                    player.getName().getString(), earnedRank.getDisplayName(),
                    getOrCreate(player.getUUID()).getArcanePower());

            markDirty();
        }
    }

    // --- Data Access ---

    public static PlayerProgressionData getOrCreate(UUID playerId) {
        return playerData.computeIfAbsent(playerId, PlayerProgressionData::new);
    }

    public static PlayerProgressionData getData(UUID playerId) {
        return playerData.get(playerId);
    }

    public static Map<UUID, PlayerProgressionData> getAllData() {
        return playerData;
    }

    // --- Persistence ---

    public static void loadPlayerData(PlayerProgressionData data) {
        playerData.put(data.getPlayerId(), data);
    }

    private static void markDirty() {
        if (storageWorld != null) {
            ProgressionSavedData.get(storageWorld).setDirty();
        }
    }

    public static void reset() {
        playerData.clear();
        storageWorld = null;
    }
}

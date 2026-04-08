package com.thaumicwards.factions;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.nbt.CompoundNBT;

import java.util.UUID;

/**
 * Tracks per-player "Arcane Power" for the hybrid rank progression system.
 * Arcane Power is earned through playtime and faction kills.
 */
public class PlayerProgressionData {

    private final UUID playerId;
    private long arcanePower;
    private int factionKills;
    private long playtimeMinutes;

    public PlayerProgressionData(UUID playerId) {
        this.playerId = playerId;
        this.arcanePower = 0;
        this.factionKills = 0;
        this.playtimeMinutes = 0;
    }

    private PlayerProgressionData(UUID playerId, long arcanePower, int factionKills, long playtimeMinutes) {
        this.playerId = playerId;
        this.arcanePower = arcanePower;
        this.factionKills = factionKills;
        this.playtimeMinutes = playtimeMinutes;
    }

    // --- Getters ---

    public UUID getPlayerId() { return playerId; }
    public long getArcanePower() { return arcanePower; }
    public int getFactionKills() { return factionKills; }
    public long getPlaytimeMinutes() { return playtimeMinutes; }

    // --- Point Accumulation ---

    /**
     * Awards playtime points. Called every minute for online faction members.
     */
    public void addPlaytimePoints() {
        playtimeMinutes++;
        arcanePower += ServerConfig.ARCANE_POWER_PER_MINUTE.get();
    }

    /**
     * Awards faction kill points.
     */
    public void addKillPoints() {
        factionKills++;
        arcanePower += ServerConfig.ARCANE_POWER_PER_KILL.get();
    }

    /**
     * Returns the highest rank this player has earned through auto-progression.
     * Only considers INITIATE, ACOLYTE, and WARLOCK (auto-earnable ranks).
     * ARCHMAGE requires manual leader promotion, LEADER requires OP assignment.
     */
    public FactionRank getEarnedRank() {
        long warlockThreshold = ServerConfig.WARLOCK_THRESHOLD.get();
        long acolyteThreshold = ServerConfig.ACOLYTE_THRESHOLD.get();

        if (arcanePower >= warlockThreshold) {
            return FactionRank.WARLOCK;
        } else if (arcanePower >= acolyteThreshold) {
            return FactionRank.ACOLYTE;
        }
        return FactionRank.INITIATE;
    }

    /**
     * Returns the arcane power needed for the next auto-rank.
     * Returns -1 if at max auto-rank (WARLOCK).
     */
    public long getPointsToNextRank() {
        FactionRank earned = getEarnedRank();
        if (earned == FactionRank.WARLOCK) return -1; // Max auto-rank

        long acolyteThreshold = ServerConfig.ACOLYTE_THRESHOLD.get();
        long warlockThreshold = ServerConfig.WARLOCK_THRESHOLD.get();

        if (earned == FactionRank.INITIATE) {
            return acolyteThreshold - arcanePower;
        } else { // ACOLYTE
            return warlockThreshold - arcanePower;
        }
    }

    /**
     * Returns progress percentage to next auto-rank (0-100).
     */
    public int getProgressPercent() {
        FactionRank earned = getEarnedRank();
        if (earned == FactionRank.WARLOCK) return 100;

        long acolyteThreshold = ServerConfig.ACOLYTE_THRESHOLD.get();
        long warlockThreshold = ServerConfig.WARLOCK_THRESHOLD.get();

        if (earned == FactionRank.INITIATE) {
            return (int) (arcanePower * 100 / acolyteThreshold);
        } else { // ACOLYTE
            long progress = arcanePower - acolyteThreshold;
            long range = warlockThreshold - acolyteThreshold;
            return (int) (progress * 100 / range);
        }
    }

    // --- Serialization ---

    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putUUID("playerId", playerId);
        nbt.putLong("arcanePower", arcanePower);
        nbt.putInt("factionKills", factionKills);
        nbt.putLong("playtimeMinutes", playtimeMinutes);
        return nbt;
    }

    public static PlayerProgressionData deserializeNBT(CompoundNBT nbt) {
        UUID playerId = nbt.getUUID("playerId");
        long arcanePower = nbt.getLong("arcanePower");
        int factionKills = nbt.getInt("factionKills");
        long playtimeMinutes = nbt.getLong("playtimeMinutes");
        return new PlayerProgressionData(playerId, arcanePower, factionKills, playtimeMinutes);
    }
}

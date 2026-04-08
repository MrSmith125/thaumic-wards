package com.thaumicwards.factions;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks faction vs faction kill statistics for the rivalry system.
 */
public class FactionKillTracker {

    private static final Map<UUID, Integer> playerKills = new HashMap<>();
    private static final Map<UUID, Integer> playerDeaths = new HashMap<>();
    private static final Map<String, Integer> factionTotalKills = new HashMap<>(); // keyed by faction stringId

    private static ServerWorld storageWorld = null;

    public static void init(ServerWorld overworld) {
        storageWorld = overworld;
        FactionKillSavedData.get(overworld); // Triggers load
        ThaumicWards.LOGGER.info("FactionKillTracker initialized.");
    }

    /**
     * Records a faction kill. Increments killer's kills, victim's deaths,
     * the killer's faction total, and awards progression points.
     */
    public static void recordKill(UUID killerId, UUID victimId, net.minecraft.entity.player.ServerPlayerEntity killer) {
        // Increment player kill/death counts
        playerKills.merge(killerId, 1, Integer::sum);
        playerDeaths.merge(victimId, 1, Integer::sum);

        // Increment faction total
        Faction killerFaction = FactionManager.getPlayerFaction(killerId);
        if (killerFaction != null) {
            factionTotalKills.merge(killerFaction.getStringId(), 1, Integer::sum);
        }

        // Award progression points
        ProgressionManager.onFactionKill(killerId, victimId, killer);

        markDirty();
    }

    // --- Queries ---

    public static int getPlayerKills(UUID playerId) {
        return playerKills.getOrDefault(playerId, 0);
    }

    public static int getPlayerDeaths(UUID playerId) {
        return playerDeaths.getOrDefault(playerId, 0);
    }

    public static int getFactionTotalKills(String factionStringId) {
        return factionTotalKills.getOrDefault(factionStringId, 0);
    }

    /**
     * Returns the top killers for a faction, sorted by kills descending.
     */
    public static List<Map.Entry<UUID, Integer>> getTopKillers(String factionStringId, int count) {
        Faction faction = FactionManager.getFactionByStringId(factionStringId);
        if (faction == null) return Collections.emptyList();

        return playerKills.entrySet().stream()
                .filter(e -> faction.isMember(e.getKey()))
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Returns the faction string ID that is currently winning (more total kills).
     * Returns null if tied.
     */
    public static String getWinningFaction() {
        int mysticKills = getFactionTotalKills(Faction.MYSTICS_STRING_ID);
        int crimsonKills = getFactionTotalKills(Faction.CRIMSONS_STRING_ID);

        if (mysticKills > crimsonKills) return Faction.MYSTICS_STRING_ID;
        if (crimsonKills > mysticKills) return Faction.CRIMSONS_STRING_ID;
        return null; // Tied
    }

    // --- Serialization ---

    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();

        // Player kills
        ListNBT killsList = new ListNBT();
        playerKills.forEach((uuid, kills) -> {
            CompoundNBT entry = new CompoundNBT();
            entry.putUUID("uuid", uuid);
            entry.putInt("kills", kills);
            entry.putInt("deaths", playerDeaths.getOrDefault(uuid, 0));
            killsList.add(entry);
        });
        // Also save deaths for players who have deaths but no kills
        playerDeaths.forEach((uuid, deaths) -> {
            if (!playerKills.containsKey(uuid)) {
                CompoundNBT entry = new CompoundNBT();
                entry.putUUID("uuid", uuid);
                entry.putInt("kills", 0);
                entry.putInt("deaths", deaths);
                killsList.add(entry);
            }
        });
        nbt.put("playerStats", killsList);

        // Faction totals
        CompoundNBT factionTotals = new CompoundNBT();
        factionTotalKills.forEach(factionTotals::putInt);
        nbt.put("factionTotals", factionTotals);

        return nbt;
    }

    public static void deserializeNBT(CompoundNBT nbt) {
        // Player stats
        ListNBT killsList = nbt.getList("playerStats", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < killsList.size(); i++) {
            CompoundNBT entry = killsList.getCompound(i);
            UUID uuid = entry.getUUID("uuid");
            int kills = entry.getInt("kills");
            int deaths = entry.getInt("deaths");
            if (kills > 0) playerKills.put(uuid, kills);
            if (deaths > 0) playerDeaths.put(uuid, deaths);
        }

        // Faction totals
        if (nbt.contains("factionTotals")) {
            CompoundNBT factionTotals = nbt.getCompound("factionTotals");
            for (String key : factionTotals.getAllKeys()) {
                factionTotalKills.put(key, factionTotals.getInt(key));
            }
        }
    }

    // --- Persistence ---

    private static void markDirty() {
        if (storageWorld != null) {
            FactionKillSavedData.get(storageWorld).setDirty();
        }
    }

    public static void reset() {
        playerKills.clear();
        playerDeaths.clear();
        factionTotalKills.clear();
        storageWorld = null;
    }
}

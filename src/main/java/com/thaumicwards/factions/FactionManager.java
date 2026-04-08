package com.thaumicwards.factions;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

public class FactionManager {

    private static final Map<UUID, Faction> factionsById = new HashMap<>();
    private static final Map<String, UUID> factionsByStringId = new HashMap<>(); // "mystics" -> UUID
    private static final Map<UUID, UUID> playerToFaction = new HashMap<>(); // player -> faction

    private static ServerWorld storageWorld = null;

    public static void init(ServerWorld overworld) {
        storageWorld = overworld;
        FactionSavedData.get(overworld); // Triggers load

        // Ensure both hardcoded factions always exist
        ensureFactionExists(Faction.MYSTICS_STRING_ID, Faction.MYSTICS_ID);
        ensureFactionExists(Faction.CRIMSONS_STRING_ID, Faction.CRIMSONS_ID);

        ThaumicWards.LOGGER.info("FactionManager initialized — Mystics: {} members, Crimsons: {} members.",
                getMystics().getMemberCount(), getCrimsons().getMemberCount());
    }

    private static void ensureFactionExists(String stringId, UUID expectedId) {
        if (!factionsByStringId.containsKey(stringId)) {
            Faction faction;
            if (Faction.MYSTICS_STRING_ID.equals(stringId)) {
                faction = Faction.createMystics();
            } else {
                faction = Faction.createCrimsons();
            }
            factionsById.put(faction.getFactionId(), faction);
            factionsByStringId.put(stringId, faction.getFactionId());
            markDirty();
            ThaumicWards.LOGGER.info("Created hardcoded faction: {}", faction.getName());
        }
    }

    // --- Join System ---

    /**
     * Auto-assigns a player to the faction with fewer members.
     * Returns the faction joined, or null if already in a faction.
     */
    public static Faction joinFaction(UUID playerId, String playerName) {
        if (playerToFaction.containsKey(playerId)) {
            return null; // Already in a faction
        }

        Faction mystics = getMystics();
        Faction crimsons = getCrimsons();

        // Pick the smaller faction, or Mystics if tied
        Faction target;
        if (mystics.getMemberCount() <= crimsons.getMemberCount()) {
            target = mystics;
        } else {
            target = crimsons;
        }

        if (target.addMember(playerId, playerName)) {
            playerToFaction.put(playerId, target.getFactionId());
            markDirty();
            return target;
        }
        return null;
    }

    // --- Leader Management (OP only) ---

    /**
     * Sets a player as a Leader of their faction. Player must already be a member.
     */
    public static boolean setLeader(UUID playerId) {
        UUID factionId = playerToFaction.get(playerId);
        if (factionId == null) return false;

        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        boolean result = faction.addLeader(playerId);
        if (result) markDirty();
        return result;
    }

    /**
     * Removes Leader status from a player (demotes them back to Warlock).
     */
    public static boolean removeLeader(UUID playerId) {
        UUID factionId = playerToFaction.get(playerId);
        if (factionId == null) return false;

        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        boolean result = faction.removeLeader(playerId);
        if (result) markDirty();
        return result;
    }

    // --- Member Management ---

    public static boolean kickMember(UUID factionId, UUID kickerId, UUID targetId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        FactionRank kickerRank = faction.getRank(kickerId);
        if (kickerRank == null || !kickerRank.canKick()) return false;

        FactionRank targetRank = faction.getRank(targetId);
        if (targetRank == null) return false;

        // Leaders can't kick other leaders
        if (targetRank == FactionRank.LEADER) return false;

        faction.removeMember(targetId);
        playerToFaction.remove(targetId);
        markDirty();
        return true;
    }

    public static boolean leaveFaction(UUID playerId) {
        UUID factionId = playerToFaction.get(playerId);
        if (factionId == null) return false;

        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        faction.removeMember(playerId);
        playerToFaction.remove(playerId);
        markDirty();
        return true;
    }

    /**
     * Leader promotes a WARLOCK to ARCHMAGE.
     */
    public static boolean promoteMember(UUID factionId, UUID promoterId, UUID targetId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        FactionRank promoterRank = faction.getRank(promoterId);
        if (promoterRank == null || !promoterRank.canPromote()) return false;

        boolean result = faction.promoteToArchmage(targetId);
        if (result) markDirty();
        return result;
    }

    /**
     * Leader demotes an ARCHMAGE to WARLOCK.
     */
    public static boolean demoteMember(UUID factionId, UUID demoterId, UUID targetId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        FactionRank demoterRank = faction.getRank(demoterId);
        if (demoterRank == null || !demoterRank.canDemote()) return false;

        boolean result = faction.demoteFromArchmage(targetId);
        if (result) markDirty();
        return result;
    }

    // --- Queries ---

    public static Faction getFaction(UUID factionId) {
        return factionsById.get(factionId);
    }

    public static Faction getFactionByStringId(String stringId) {
        UUID id = factionsByStringId.get(stringId);
        return id != null ? factionsById.get(id) : null;
    }

    public static Faction getFactionByName(String name) {
        // Search by display name for backwards compat
        for (Faction faction : factionsById.values()) {
            if (faction.getName().equalsIgnoreCase(name) || faction.getStringId().equalsIgnoreCase(name)) {
                return faction;
            }
        }
        return null;
    }

    public static UUID getPlayerFactionId(UUID playerId) {
        return playerToFaction.get(playerId);
    }

    public static Faction getPlayerFaction(UUID playerId) {
        UUID factionId = playerToFaction.get(playerId);
        return factionId != null ? factionsById.get(factionId) : null;
    }

    public static boolean isPlayerInFaction(UUID playerId) {
        return playerToFaction.containsKey(playerId);
    }

    public static Collection<Faction> getAllFactions() {
        return Collections.unmodifiableCollection(factionsById.values());
    }

    public static Map<UUID, Faction> getFactionsMap() {
        return factionsById;
    }

    // --- Two-faction helpers ---

    public static Faction getMystics() {
        UUID id = factionsByStringId.get(Faction.MYSTICS_STRING_ID);
        return id != null ? factionsById.get(id) : null;
    }

    public static Faction getCrimsons() {
        UUID id = factionsByStringId.get(Faction.CRIMSONS_STRING_ID);
        return id != null ? factionsById.get(id) : null;
    }

    /**
     * Returns the opposing faction for a given faction ID.
     */
    public static Faction getOpposingFaction(UUID factionId) {
        Faction mystics = getMystics();
        Faction crimsons = getCrimsons();
        if (mystics != null && mystics.getFactionId().equals(factionId)) return crimsons;
        if (crimsons != null && crimsons.getFactionId().equals(factionId)) return mystics;
        return null;
    }

    /**
     * Returns true if two players are in opposing factions.
     */
    public static boolean areEnemies(UUID player1, UUID player2) {
        UUID faction1 = playerToFaction.get(player1);
        UUID faction2 = playerToFaction.get(player2);
        if (faction1 == null || faction2 == null) return false;
        return !faction1.equals(faction2);
    }

    /**
     * Returns true if two players are in the same faction.
     */
    public static boolean areAllies(UUID player1, UUID player2) {
        UUID faction1 = playerToFaction.get(player1);
        UUID faction2 = playerToFaction.get(player2);
        if (faction1 == null || faction2 == null) return false;
        return faction1.equals(faction2);
    }

    // --- Integration with ClaimManager ---

    public static boolean canPlayerInteractInFactionClaim(UUID factionId, UUID playerId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;
        return faction.isMember(playerId);
    }

    // --- Persistence ---

    private static void markDirty() {
        if (storageWorld != null) {
            FactionSavedData.get(storageWorld).setDirty();
        }
    }

    public static void reset() {
        factionsById.clear();
        factionsByStringId.clear();
        playerToFaction.clear();
        storageWorld = null;
    }

    /**
     * Called by FactionSavedData during load. Only loads factions with valid stringIds.
     */
    public static void loadFaction(Faction faction) {
        String stringId = faction.getStringId();
        // Only load the two hardcoded factions — discard any old arbitrary factions
        if (!Faction.MYSTICS_STRING_ID.equals(stringId) && !Faction.CRIMSONS_STRING_ID.equals(stringId)) {
            ThaumicWards.LOGGER.warn("Discarding old faction '{}' during migration to two-faction system.",
                    faction.getName());
            return;
        }
        factionsById.put(faction.getFactionId(), faction);
        factionsByStringId.put(stringId, faction.getFactionId());
        faction.getMembers().keySet().forEach(uuid -> playerToFaction.put(uuid, faction.getFactionId()));
    }
}

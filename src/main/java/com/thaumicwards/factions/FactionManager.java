package com.thaumicwards.factions;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

public class FactionManager {

    private static final Map<UUID, Faction> factionsById = new HashMap<>();
    private static final Map<String, UUID> factionsByName = new HashMap<>(); // lowercase
    private static final Map<UUID, UUID> playerToFaction = new HashMap<>(); // player -> faction
    private static final Map<UUID, UUID> pendingInvites = new HashMap<>(); // player -> faction
    private static final Map<UUID, Long> inviteTimestamps = new HashMap<>();

    private static ServerWorld storageWorld = null;
    private static final long INVITE_EXPIRE_MS = 120_000; // 2 minutes

    public static void init(ServerWorld overworld) {
        storageWorld = overworld;
        FactionSavedData.get(overworld); // Triggers load
        ThaumicWards.LOGGER.info("FactionManager initialized with {} factions.", factionsById.size());
    }

    // --- Create / Disband ---

    public static Faction createFaction(String name, UUID archonId, String archonName) {
        if (name.length() > ServerConfig.MAX_FACTION_NAME_LENGTH.get()) {
            return null;
        }
        if (factionsByName.containsKey(name.toLowerCase())) {
            return null; // Name taken
        }
        if (playerToFaction.containsKey(archonId)) {
            return null; // Already in a faction
        }

        UUID factionId = UUID.randomUUID();
        Faction faction = new Faction(factionId, name, archonId, archonName);

        factionsById.put(factionId, faction);
        factionsByName.put(name.toLowerCase(), factionId);
        playerToFaction.put(archonId, factionId);
        markDirty();

        return faction;
    }

    public static boolean disbandFaction(UUID factionId, UUID requesterId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;
        if (!faction.getArchonId().equals(requesterId)) return false;

        // Remove all member associations
        faction.getMembers().keySet().forEach(playerToFaction::remove);

        // Remove pending invites for this faction
        pendingInvites.entrySet().removeIf(e -> e.getValue().equals(factionId));

        factionsById.remove(factionId);
        factionsByName.remove(faction.getName().toLowerCase());
        markDirty();

        return true;
    }

    // --- Invites ---

    public static boolean invitePlayer(UUID factionId, UUID inviterId, UUID inviteeId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        FactionRank inviterRank = faction.getRank(inviterId);
        if (inviterRank == null || !inviterRank.canInvite()) return false;

        if (playerToFaction.containsKey(inviteeId)) return false; // Already in a faction
        if (pendingInvites.containsKey(inviteeId)) return false; // Already invited somewhere

        pendingInvites.put(inviteeId, factionId);
        inviteTimestamps.put(inviteeId, System.currentTimeMillis());
        return true;
    }

    public static Faction acceptInvite(UUID playerId, String playerName) {
        UUID factionId = pendingInvites.get(playerId);
        if (factionId == null) return null;

        // Check expiry
        Long timestamp = inviteTimestamps.get(playerId);
        if (timestamp != null && System.currentTimeMillis() - timestamp > INVITE_EXPIRE_MS) {
            pendingInvites.remove(playerId);
            inviteTimestamps.remove(playerId);
            return null;
        }

        Faction faction = factionsById.get(factionId);
        if (faction == null) {
            pendingInvites.remove(playerId);
            return null;
        }

        if (!faction.addMember(playerId, playerName)) {
            return null; // Faction full
        }

        playerToFaction.put(playerId, factionId);
        pendingInvites.remove(playerId);
        inviteTimestamps.remove(playerId);
        markDirty();

        return faction;
    }

    // --- Member Management ---

    public static boolean kickMember(UUID factionId, UUID kickerId, UUID targetId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        FactionRank kickerRank = faction.getRank(kickerId);
        if (kickerRank == null || !kickerRank.canKick()) return false;

        FactionRank targetRank = faction.getRank(targetId);
        if (targetRank == null) return false;
        if (targetRank.getLevel() >= kickerRank.getLevel()) return false; // Can't kick equal or higher

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

        // Archon can't leave unless they're the only member
        if (faction.getArchonId().equals(playerId)) {
            if (faction.getMemberCount() > 1) return false;
            // Solo archon leaving = disband
            return disbandFaction(factionId, playerId);
        }

        faction.removeMember(playerId);
        playerToFaction.remove(playerId);
        markDirty();
        return true;
    }

    public static boolean promoteMember(UUID factionId, UUID promoterId, UUID targetId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        FactionRank promoterRank = faction.getRank(promoterId);
        if (promoterRank == null || !promoterRank.canPromote()) return false;

        boolean result = faction.promote(targetId);
        if (result) markDirty();
        return result;
    }

    public static boolean demoteMember(UUID factionId, UUID demoterId, UUID targetId) {
        Faction faction = factionsById.get(factionId);
        if (faction == null) return false;

        FactionRank demoterRank = faction.getRank(demoterId);
        if (demoterRank == null || !demoterRank.canDemote()) return false;

        boolean result = faction.demote(targetId);
        if (result) markDirty();
        return result;
    }

    // --- Queries ---

    public static Faction getFaction(UUID factionId) {
        return factionsById.get(factionId);
    }

    public static Faction getFactionByName(String name) {
        UUID id = factionsByName.get(name.toLowerCase());
        return id != null ? factionsById.get(id) : null;
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

    public static boolean hasPendingInvite(UUID playerId) {
        return pendingInvites.containsKey(playerId);
    }

    public static Collection<Faction> getAllFactions() {
        return Collections.unmodifiableCollection(factionsById.values());
    }

    public static Map<UUID, Faction> getFactionsMap() {
        return factionsById;
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
        factionsByName.clear();
        playerToFaction.clear();
        pendingInvites.clear();
        inviteTimestamps.clear();
        storageWorld = null;
    }

    // Called by FactionSavedData during load
    public static void loadFaction(Faction faction) {
        factionsById.put(faction.getFactionId(), faction);
        factionsByName.put(faction.getName().toLowerCase(), faction.getFactionId());
        faction.getMembers().keySet().forEach(uuid -> playerToFaction.put(uuid, faction.getFactionId()));
    }
}

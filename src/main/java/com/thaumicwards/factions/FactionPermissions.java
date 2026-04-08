package com.thaumicwards.factions;

import java.util.UUID;

public class FactionPermissions {

    public static boolean canInvite(UUID factionId, UUID playerId) {
        Faction faction = FactionManager.getFaction(factionId);
        if (faction == null) return false;
        FactionRank rank = faction.getRank(playerId);
        return rank != null && rank.canInvite();
    }

    public static boolean canKick(UUID factionId, UUID playerId) {
        Faction faction = FactionManager.getFaction(factionId);
        if (faction == null) return false;
        FactionRank rank = faction.getRank(playerId);
        return rank != null && rank.canKick();
    }

    public static boolean canClaimGuild(UUID factionId, UUID playerId) {
        Faction faction = FactionManager.getFaction(factionId);
        if (faction == null) return false;
        FactionRank rank = faction.getRank(playerId);
        return rank != null && rank.canExpandGuild();
    }

    public static boolean canPromote(UUID factionId, UUID playerId) {
        Faction faction = FactionManager.getFaction(factionId);
        if (faction == null) return false;
        FactionRank rank = faction.getRank(playerId);
        return rank != null && rank.canPromote();
    }

    public static boolean canDisband(UUID factionId, UUID playerId) {
        Faction faction = FactionManager.getFaction(factionId);
        if (faction == null) return false;
        FactionRank rank = faction.getRank(playerId);
        return rank != null && rank.canDisband();
    }

    public static boolean canPlaceNexus(UUID factionId, UUID playerId) {
        Faction faction = FactionManager.getFaction(factionId);
        if (faction == null) return false;
        FactionRank rank = faction.getRank(playerId);
        return rank != null && rank.canPlaceNexus();
    }
}

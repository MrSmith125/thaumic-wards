package com.thaumicwards.claims;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimManager {

    private static final Map<Long, ClaimData> claims = new HashMap<>();
    private static ServerWorld storageWorld = null;

    public static void init(ServerWorld overworld) {
        storageWorld = overworld;
        ClaimSavedData.get(overworld); // Triggers load
        ThaumicWards.LOGGER.info("ClaimManager initialized with {} claims.", claims.size());
    }

    public static ClaimResult claimChunk(ChunkPos pos, UUID playerUUID, String playerName,
                                          ClaimData.ClaimType type, UUID factionId) {
        long key = pos.toLong();

        // Check if already claimed
        if (claims.containsKey(key)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        // Check max claims for personal
        if (type == ClaimData.ClaimType.PERSONAL) {
            int maxClaims = ServerConfig.MAX_PERSONAL_CLAIMS.get();
            long currentClaims = getPlayerPersonalClaims(playerUUID).size();
            if (currentClaims >= maxClaims) {
                return ClaimResult.MAX_CLAIMS_REACHED;
            }
        }

        ClaimData claim = new ClaimData(pos, playerUUID, playerName, type, factionId);
        claims.put(key, claim);
        markDirty();

        return ClaimResult.SUCCESS;
    }

    public static boolean unclaimChunk(ChunkPos pos, UUID playerUUID) {
        long key = pos.toLong();
        ClaimData claim = claims.get(key);

        if (claim == null) {
            return false;
        }

        // Only owner or faction archon can unclaim
        if (!claim.getOwnerUUID().equals(playerUUID)) {
            return false;
        }

        claims.remove(key);
        markDirty();
        return true;
    }

    public static boolean forceUnclaim(ChunkPos pos) {
        long key = pos.toLong();
        if (claims.remove(key) != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public static ClaimData getClaimAt(ChunkPos pos) {
        return claims.get(pos.toLong());
    }

    public static boolean isChunkClaimed(ChunkPos pos) {
        return claims.containsKey(pos.toLong());
    }

    public static List<ClaimData> getPlayerPersonalClaims(UUID playerUUID) {
        return claims.values().stream()
                .filter(c -> c.isPersonal() && c.getOwnerUUID().equals(playerUUID))
                .collect(Collectors.toList());
    }

    public static List<ClaimData> getFactionClaims(UUID factionId) {
        return claims.values().stream()
                .filter(c -> c.isGuild() && factionId.equals(c.getFactionId()))
                .collect(Collectors.toList());
    }

    public static boolean canPlayerInteract(ChunkPos pos, UUID playerUUID) {
        ClaimData claim = getClaimAt(pos);
        if (claim == null) {
            return true; // Unclaimed
        }

        // Owner can always interact
        if (claim.getOwnerUUID().equals(playerUUID)) {
            return true;
        }

        // For guild claims, check faction membership
        if (claim.isGuild() && claim.getFactionId() != null) {
            // This will be integrated with FactionManager in Phase 6
            return false; // For now, non-owners can't interact
        }

        return false;
    }

    public static List<ClaimData> getClaimsNear(ChunkPos center, int radius) {
        List<ClaimData> nearby = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ClaimData claim = getClaimAt(new ChunkPos(center.x + dx, center.z + dz));
                if (claim != null) {
                    nearby.add(claim);
                }
            }
        }
        return nearby;
    }

    public static Map<Long, ClaimData> getAllClaims() {
        return claims;
    }

    private static void markDirty() {
        if (storageWorld != null) {
            ClaimSavedData.get(storageWorld).setDirty();
        }
    }

    public static void reset() {
        claims.clear();
        storageWorld = null;
    }

    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        MAX_CLAIMS_REACHED,
        NOT_ENOUGH_RANK,
        FAILED
    }
}

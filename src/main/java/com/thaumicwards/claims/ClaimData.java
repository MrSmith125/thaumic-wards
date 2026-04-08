package com.thaumicwards.claims;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

public class ClaimData {

    public enum ClaimType {
        PERSONAL,
        GUILD
    }

    private final ChunkPos chunkPos;
    private final UUID ownerUUID;
    private final String ownerName;
    private final ClaimType type;
    private final UUID factionId; // null for personal claims
    private final long claimedAt;

    public ClaimData(ChunkPos chunkPos, UUID ownerUUID, String ownerName, ClaimType type, UUID factionId) {
        this.chunkPos = chunkPos;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.type = type;
        this.factionId = factionId;
        this.claimedAt = System.currentTimeMillis();
    }

    private ClaimData(ChunkPos chunkPos, UUID ownerUUID, String ownerName, ClaimType type, UUID factionId, long claimedAt) {
        this.chunkPos = chunkPos;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.type = type;
        this.factionId = factionId;
        this.claimedAt = claimedAt;
    }

    public ChunkPos getChunkPos() { return chunkPos; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public ClaimType getType() { return type; }
    public UUID getFactionId() { return factionId; }
    public long getClaimedAt() { return claimedAt; }

    public boolean isPersonal() { return type == ClaimType.PERSONAL; }
    public boolean isGuild() { return type == ClaimType.GUILD; }

    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("chunkX", chunkPos.x);
        nbt.putInt("chunkZ", chunkPos.z);
        nbt.putUUID("owner", ownerUUID);
        nbt.putString("ownerName", ownerName);
        nbt.putString("type", type.name());
        if (factionId != null) {
            nbt.putUUID("factionId", factionId);
        }
        nbt.putLong("claimedAt", claimedAt);
        return nbt;
    }

    public static ClaimData deserializeNBT(CompoundNBT nbt) {
        ChunkPos pos = new ChunkPos(nbt.getInt("chunkX"), nbt.getInt("chunkZ"));
        UUID owner = nbt.getUUID("owner");
        String ownerName = nbt.getString("ownerName");
        ClaimType type = ClaimType.valueOf(nbt.getString("type"));
        UUID factionId = nbt.contains("factionId") ? nbt.getUUID("factionId") : null;
        long claimedAt = nbt.getLong("claimedAt");
        return new ClaimData(pos, owner, ownerName, type, factionId, claimedAt);
    }
}

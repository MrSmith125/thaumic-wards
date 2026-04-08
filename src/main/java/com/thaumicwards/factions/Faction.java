package com.thaumicwards.factions;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.*;

public class Faction {

    private final UUID factionId;
    private String name;
    private UUID archonId;
    private final Map<UUID, FactionRank> members;
    private final Map<UUID, String> memberNames;
    private BlockPos nexusPos;
    private final long createdAt;

    public Faction(UUID factionId, String name, UUID archonId, String archonName) {
        this.factionId = factionId;
        this.name = name;
        this.archonId = archonId;
        this.members = new HashMap<>();
        this.memberNames = new HashMap<>();
        this.members.put(archonId, FactionRank.ARCHON);
        this.memberNames.put(archonId, archonName);
        this.nexusPos = null;
        this.createdAt = System.currentTimeMillis();
    }

    private Faction(UUID factionId, String name, UUID archonId,
                    Map<UUID, FactionRank> members, Map<UUID, String> memberNames,
                    BlockPos nexusPos, long createdAt) {
        this.factionId = factionId;
        this.name = name;
        this.archonId = archonId;
        this.members = members;
        this.memberNames = memberNames;
        this.nexusPos = nexusPos;
        this.createdAt = createdAt;
    }

    // Getters
    public UUID getFactionId() { return factionId; }
    public String getName() { return name; }
    public UUID getArchonId() { return archonId; }
    public BlockPos getNexusPos() { return nexusPos; }
    public long getCreatedAt() { return createdAt; }
    public int getMemberCount() { return members.size(); }

    public Map<UUID, FactionRank> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public FactionRank getRank(UUID playerId) {
        return members.get(playerId);
    }

    public String getMemberName(UUID playerId) {
        return memberNames.getOrDefault(playerId, "Unknown");
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public void setNexusPos(BlockPos pos) {
        this.nexusPos = pos;
    }

    public int getMaxGuildClaims() {
        int base = ServerConfig.MAX_GUILD_HALL_CLAIMS.get();
        int perMember = ServerConfig.CLAIMS_PER_FACTION_MEMBER.get();
        return base + (members.size() * perMember);
    }

    // Member management
    public boolean addMember(UUID playerId, String playerName) {
        if (members.size() >= ServerConfig.MAX_FACTION_MEMBERS.get()) {
            return false;
        }
        members.put(playerId, FactionRank.APPRENTICE);
        memberNames.put(playerId, playerName);
        return true;
    }

    public boolean removeMember(UUID playerId) {
        if (playerId.equals(archonId)) {
            return false; // Can't remove the archon
        }
        members.remove(playerId);
        memberNames.remove(playerId);
        return true;
    }

    public boolean promote(UUID playerId) {
        FactionRank current = members.get(playerId);
        if (current == null || current == FactionRank.ARCHON) {
            return false;
        }
        // Can't promote to Archon (would need transfer)
        if (current == FactionRank.MASTER) {
            return false;
        }
        members.put(playerId, current.nextRank());
        return true;
    }

    public boolean demote(UUID playerId) {
        FactionRank current = members.get(playerId);
        if (current == null || current == FactionRank.APPRENTICE || current == FactionRank.ARCHON) {
            return false;
        }
        members.put(playerId, current.previousRank());
        return true;
    }

    // Serialization
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putUUID("factionId", factionId);
        nbt.putString("name", name);
        nbt.putUUID("archonId", archonId);
        nbt.putLong("createdAt", createdAt);

        if (nexusPos != null) {
            nbt.putInt("nexusX", nexusPos.getX());
            nbt.putInt("nexusY", nexusPos.getY());
            nbt.putInt("nexusZ", nexusPos.getZ());
        }

        ListNBT memberList = new ListNBT();
        members.forEach((uuid, rank) -> {
            CompoundNBT memberNbt = new CompoundNBT();
            memberNbt.putUUID("uuid", uuid);
            memberNbt.putString("rank", rank.name());
            memberNbt.putString("name", memberNames.getOrDefault(uuid, "Unknown"));
            memberList.add(memberNbt);
        });
        nbt.put("members", memberList);

        return nbt;
    }

    public static Faction deserializeNBT(CompoundNBT nbt) {
        UUID factionId = nbt.getUUID("factionId");
        String name = nbt.getString("name");
        UUID archonId = nbt.getUUID("archonId");
        long createdAt = nbt.getLong("createdAt");

        BlockPos nexusPos = null;
        if (nbt.contains("nexusX")) {
            nexusPos = new BlockPos(nbt.getInt("nexusX"), nbt.getInt("nexusY"), nbt.getInt("nexusZ"));
        }

        Map<UUID, FactionRank> members = new HashMap<>();
        Map<UUID, String> memberNames = new HashMap<>();
        ListNBT memberList = nbt.getList("members", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundNBT memberNbt = memberList.getCompound(i);
            UUID uuid = memberNbt.getUUID("uuid");
            FactionRank rank = FactionRank.valueOf(memberNbt.getString("rank"));
            String memberName = memberNbt.getString("name");
            members.put(uuid, rank);
            memberNames.put(uuid, memberName);
        }

        return new Faction(factionId, name, archonId, members, memberNames, nexusPos, createdAt);
    }
}

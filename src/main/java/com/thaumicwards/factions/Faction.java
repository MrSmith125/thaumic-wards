package com.thaumicwards.factions;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

import java.util.*;

public class Faction {

    // Deterministic UUIDs for the two hardcoded factions
    public static final UUID MYSTICS_ID = UUID.nameUUIDFromBytes("thaumic_wards_mystics".getBytes());
    public static final UUID CRIMSONS_ID = UUID.nameUUIDFromBytes("thaumic_wards_crimsons".getBytes());

    public static final String MYSTICS_STRING_ID = "mystics";
    public static final String CRIMSONS_STRING_ID = "crimsons";

    private final UUID factionId;
    private final String stringId; // "mystics" or "crimsons"
    private final String name;
    private final TextFormatting factionColor;
    private final Set<UUID> leaders;
    private final Map<UUID, FactionRank> members;
    private final Map<UUID, String> memberNames;
    private BlockPos nexusPos;
    private final long createdAt;

    /**
     * Creates one of the two hardcoded factions.
     */
    public Faction(String stringId, String displayName, TextFormatting color, UUID factionId) {
        this.factionId = factionId;
        this.stringId = stringId;
        this.name = displayName;
        this.factionColor = color;
        this.leaders = new HashSet<>();
        this.members = new HashMap<>();
        this.memberNames = new HashMap<>();
        this.nexusPos = null;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Full constructor for deserialization.
     */
    private Faction(UUID factionId, String stringId, String name, TextFormatting color,
                    Set<UUID> leaders, Map<UUID, FactionRank> members, Map<UUID, String> memberNames,
                    BlockPos nexusPos, long createdAt) {
        this.factionId = factionId;
        this.stringId = stringId;
        this.name = name;
        this.factionColor = color;
        this.leaders = leaders;
        this.members = members;
        this.memberNames = memberNames;
        this.nexusPos = nexusPos;
        this.createdAt = createdAt;
    }

    // --- Factory methods ---

    public static Faction createMystics() {
        return new Faction(MYSTICS_STRING_ID, "The Mystics", TextFormatting.BLUE, MYSTICS_ID);
    }

    public static Faction createCrimsons() {
        return new Faction(CRIMSONS_STRING_ID, "The Crimsons", TextFormatting.RED, CRIMSONS_ID);
    }

    // --- Getters ---

    public UUID getFactionId() { return factionId; }
    public String getStringId() { return stringId; }
    public String getName() { return name; }
    public TextFormatting getFactionColor() { return factionColor; }
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

    // --- Leader management ---

    public Set<UUID> getLeaders() {
        return Collections.unmodifiableSet(leaders);
    }

    public boolean isLeader(UUID playerId) {
        return leaders.contains(playerId);
    }

    public boolean addLeader(UUID playerId) {
        if (!members.containsKey(playerId)) return false;
        leaders.add(playerId);
        members.put(playerId, FactionRank.LEADER);
        return true;
    }

    public boolean removeLeader(UUID playerId) {
        if (!leaders.contains(playerId)) return false;
        leaders.remove(playerId);
        // Demote back to WARLOCK (highest auto-earnable rank) since we can't know their earned rank here
        // The ProgressionManager can re-evaluate their correct rank
        members.put(playerId, FactionRank.WARLOCK);
        return true;
    }

    public List<String> getLeaderNames() {
        List<String> names = new ArrayList<>();
        for (UUID leader : leaders) {
            names.add(memberNames.getOrDefault(leader, "Unknown"));
        }
        return names;
    }

    // --- Member management ---

    public boolean addMember(UUID playerId, String playerName) {
        // No hard cap for the two-faction system (or use a very high limit)
        if (members.size() >= 1000) {
            return false;
        }
        members.put(playerId, FactionRank.INITIATE);
        memberNames.put(playerId, playerName);
        return true;
    }

    public boolean removeMember(UUID playerId) {
        // Leaders can leave freely (other leaders remain)
        leaders.remove(playerId);
        members.remove(playerId);
        memberNames.remove(playerId);
        return true;
    }

    /**
     * Promote a member to ARCHMAGE (leader-initiated manual promotion).
     * Only works for WARLOCK -> ARCHMAGE transition.
     */
    public boolean promoteToArchmage(UUID playerId) {
        FactionRank current = members.get(playerId);
        if (current == null || current != FactionRank.WARLOCK) {
            return false;
        }
        members.put(playerId, FactionRank.ARCHMAGE);
        return true;
    }

    /**
     * Demote an ARCHMAGE back to WARLOCK (leader-initiated).
     */
    public boolean demoteFromArchmage(UUID playerId) {
        FactionRank current = members.get(playerId);
        if (current == null || current != FactionRank.ARCHMAGE) {
            return false;
        }
        members.put(playerId, FactionRank.WARLOCK);
        return true;
    }

    /**
     * Set a member's rank directly (used by progression system for auto-rank-up).
     */
    public void setRank(UUID playerId, FactionRank rank) {
        if (members.containsKey(playerId)) {
            members.put(playerId, rank);
        }
    }

    // --- Serialization ---

    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putUUID("factionId", factionId);
        nbt.putString("stringId", stringId);
        nbt.putString("name", name);
        nbt.putString("color", factionColor.getName());
        nbt.putLong("createdAt", createdAt);

        if (nexusPos != null) {
            nbt.putInt("nexusX", nexusPos.getX());
            nbt.putInt("nexusY", nexusPos.getY());
            nbt.putInt("nexusZ", nexusPos.getZ());
        }

        // Serialize leaders
        ListNBT leaderList = new ListNBT();
        for (UUID leader : leaders) {
            CompoundNBT leaderNbt = new CompoundNBT();
            leaderNbt.putUUID("uuid", leader);
            leaderList.add(leaderNbt);
        }
        nbt.put("leaders", leaderList);

        // Serialize members
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
        String stringId = nbt.getString("stringId");
        String name = nbt.getString("name");
        long createdAt = nbt.getLong("createdAt");

        // Determine color from stringId
        TextFormatting color;
        if (MYSTICS_STRING_ID.equals(stringId)) {
            color = TextFormatting.BLUE;
        } else if (CRIMSONS_STRING_ID.equals(stringId)) {
            color = TextFormatting.RED;
        } else {
            color = TextFormatting.WHITE; // Fallback
        }

        BlockPos nexusPos = null;
        if (nbt.contains("nexusX")) {
            nexusPos = new BlockPos(nbt.getInt("nexusX"), nbt.getInt("nexusY"), nbt.getInt("nexusZ"));
        }

        // Deserialize leaders
        Set<UUID> leaders = new HashSet<>();
        if (nbt.contains("leaders")) {
            ListNBT leaderList = nbt.getList("leaders", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < leaderList.size(); i++) {
                leaders.add(leaderList.getCompound(i).getUUID("uuid"));
            }
        }

        // Deserialize members with rank migration support
        Map<UUID, FactionRank> members = new HashMap<>();
        Map<UUID, String> memberNames = new HashMap<>();
        ListNBT memberList = nbt.getList("members", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundNBT memberNbt = memberList.getCompound(i);
            UUID uuid = memberNbt.getUUID("uuid");
            FactionRank rank = FactionRank.fromString(memberNbt.getString("rank"));
            if (rank == null) rank = FactionRank.INITIATE; // Fallback
            String memberName = memberNbt.getString("name");
            members.put(uuid, rank);
            memberNames.put(uuid, memberName);
        }

        return new Faction(factionId, stringId, name, color, leaders, members, memberNames, nexusPos, createdAt);
    }
}

package com.thaumicwards.factions;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Manages contested zones — special PvP areas where no claiming is allowed
 * and faction kills give bonus points.
 */
public class ContestedZoneManager {

    /**
     * Represents a contested zone defined by center + chunk radius.
     */
    public static class ContestedZone {
        public final int centerX;
        public final int centerZ;
        public final int radiusChunks;

        public ContestedZone(int centerX, int centerZ, int radiusChunks) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radiusChunks = radiusChunks;
        }

        public boolean containsChunk(ChunkPos pos) {
            int dx = Math.abs(pos.x - (centerX >> 4));
            int dz = Math.abs(pos.z - (centerZ >> 4));
            return dx <= radiusChunks && dz <= radiusChunks;
        }

        public boolean containsBlock(BlockPos pos) {
            return containsChunk(new ChunkPos(pos));
        }

        public String getKey() {
            return centerX + "," + centerZ;
        }
    }

    private static final Map<String, ContestedZone> zones = new LinkedHashMap<>();
    private static ServerWorld storageWorld = null;

    public static void init(ServerWorld overworld) {
        storageWorld = overworld;
        ContestedZoneSavedData.get(overworld); // Triggers load
        ThaumicWards.LOGGER.info("ContestedZoneManager initialized with {} zones.", zones.size());
    }

    // --- Zone Management ---

    public static boolean addZone(int centerX, int centerZ, int radiusChunks) {
        ContestedZone zone = new ContestedZone(centerX, centerZ, radiusChunks);
        String key = zone.getKey();
        if (zones.containsKey(key)) return false; // Already exists
        zones.put(key, zone);
        markDirty();
        return true;
    }

    public static boolean removeZone(int centerX, int centerZ) {
        String key = centerX + "," + centerZ;
        if (zones.remove(key) != null) {
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Returns true if the given chunk position is inside any contested zone.
     */
    public static boolean isInContestedZone(ChunkPos chunkPos) {
        for (ContestedZone zone : zones.values()) {
            if (zone.containsChunk(chunkPos)) return true;
        }
        return false;
    }

    /**
     * Returns true if the given block position is inside any contested zone.
     */
    public static boolean isInContestedZone(BlockPos blockPos) {
        return isInContestedZone(new ChunkPos(blockPos));
    }

    public static Collection<ContestedZone> getAllZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    // --- Serialization ---

    public static CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        ListNBT zoneList = new ListNBT();
        for (ContestedZone zone : zones.values()) {
            CompoundNBT zoneNbt = new CompoundNBT();
            zoneNbt.putInt("centerX", zone.centerX);
            zoneNbt.putInt("centerZ", zone.centerZ);
            zoneNbt.putInt("radius", zone.radiusChunks);
            zoneList.add(zoneNbt);
        }
        nbt.put("zones", zoneList);
        return nbt;
    }

    public static void deserializeNBT(CompoundNBT nbt) {
        ListNBT zoneList = nbt.getList("zones", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < zoneList.size(); i++) {
            CompoundNBT zoneNbt = zoneList.getCompound(i);
            int cx = zoneNbt.getInt("centerX");
            int cz = zoneNbt.getInt("centerZ");
            int radius = zoneNbt.getInt("radius");
            ContestedZone zone = new ContestedZone(cx, cz, radius);
            zones.put(zone.getKey(), zone);
        }
    }

    // --- Persistence ---

    private static void markDirty() {
        if (storageWorld != null) {
            ContestedZoneSavedData.get(storageWorld).setDirty();
        }
    }

    public static void reset() {
        zones.clear();
        storageWorld = null;
    }

    // --- SavedData ---

    public static class ContestedZoneSavedData extends WorldSavedData {
        private static final String DATA_NAME = ThaumicWards.MOD_ID + "_contested_zones";

        public ContestedZoneSavedData() {
            super(DATA_NAME);
        }

        public static ContestedZoneSavedData get(ServerWorld world) {
            ServerWorld overworld = world.getServer().overworld();
            return overworld.getDataStorage().computeIfAbsent(ContestedZoneSavedData::new, DATA_NAME);
        }

        @Override
        public void load(CompoundNBT nbt) {
            ContestedZoneManager.deserializeNBT(nbt);
        }

        @Override
        public CompoundNBT save(CompoundNBT nbt) {
            CompoundNBT data = ContestedZoneManager.serializeNBT();
            for (String key : data.getAllKeys()) {
                nbt.put(key, data.get(key));
            }
            return nbt;
        }
    }
}

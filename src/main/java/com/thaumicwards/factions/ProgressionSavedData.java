package com.thaumicwards.factions;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

public class ProgressionSavedData extends WorldSavedData {

    private static final String DATA_NAME = ThaumicWards.MOD_ID + "_progression";

    public ProgressionSavedData() {
        super(DATA_NAME);
    }

    public static ProgressionSavedData get(ServerWorld world) {
        ServerWorld overworld = world.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(ProgressionSavedData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        ListNBT playerList = nbt.getList("players", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundNBT playerNbt = playerList.getCompound(i);
            try {
                PlayerProgressionData data = PlayerProgressionData.deserializeNBT(playerNbt);
                ProgressionManager.loadPlayerData(data);
            } catch (Exception e) {
                ThaumicWards.LOGGER.warn("Failed to load progression data at index {}: {}", i, e.getMessage());
            }
        }
        ThaumicWards.LOGGER.info("Loaded {} player progression records.", ProgressionManager.getAllData().size());
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        ListNBT playerList = new ListNBT();
        ProgressionManager.getAllData().values().forEach(data -> playerList.add(data.serializeNBT()));
        nbt.put("players", playerList);
        return nbt;
    }
}

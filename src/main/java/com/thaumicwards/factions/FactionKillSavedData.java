package com.thaumicwards.factions;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

public class FactionKillSavedData extends WorldSavedData {

    private static final String DATA_NAME = ThaumicWards.MOD_ID + "_kills";

    public FactionKillSavedData() {
        super(DATA_NAME);
    }

    public static FactionKillSavedData get(ServerWorld world) {
        ServerWorld overworld = world.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FactionKillSavedData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        FactionKillTracker.deserializeNBT(nbt);
        ThaumicWards.LOGGER.info("Loaded faction kill tracker data.");
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        // FactionKillTracker uses a static singleton pattern, so we create a temp instance for serialization
        FactionKillTracker tracker = new FactionKillTracker();
        CompoundNBT data = tracker.serializeNBT();
        // Copy all keys from serialized data to the output nbt
        for (String key : data.getAllKeys()) {
            nbt.put(key, data.get(key));
        }
        return nbt;
    }
}

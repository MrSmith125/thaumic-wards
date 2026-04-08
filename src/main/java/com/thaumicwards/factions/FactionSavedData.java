package com.thaumicwards.factions;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

public class FactionSavedData extends WorldSavedData {

    private static final String DATA_NAME = ThaumicWards.MOD_ID + "_factions";

    public FactionSavedData() {
        super(DATA_NAME);
    }

    public static FactionSavedData get(ServerWorld world) {
        ServerWorld overworld = world.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FactionSavedData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        ListNBT factionList = nbt.getList("factions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < factionList.size(); i++) {
            CompoundNBT factionNbt = factionList.getCompound(i);
            try {
                // Check if this is a new-format faction (has stringId)
                if (!factionNbt.contains("stringId")) {
                    // Old format faction — skip it (migration: discard old arbitrary factions)
                    String oldName = factionNbt.getString("name");
                    ThaumicWards.LOGGER.warn("Skipping old-format faction '{}' during migration.", oldName);
                    continue;
                }

                Faction faction = Faction.deserializeNBT(factionNbt);
                FactionManager.loadFaction(faction);
            } catch (Exception e) {
                ThaumicWards.LOGGER.warn("Failed to load faction data at index {}: {}", i, e.getMessage());
            }
        }

        ThaumicWards.LOGGER.info("Loaded {} factions from save data.", FactionManager.getFactionsMap().size());
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        ListNBT factionList = new ListNBT();
        FactionManager.getFactionsMap().values().forEach(faction -> factionList.add(faction.serializeNBT()));
        nbt.put("factions", factionList);
        return nbt;
    }
}

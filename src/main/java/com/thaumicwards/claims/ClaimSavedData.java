package com.thaumicwards.claims;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

public class ClaimSavedData extends WorldSavedData {

    private static final String DATA_NAME = ThaumicWards.MOD_ID + "_claims";

    public ClaimSavedData() {
        super(DATA_NAME);
    }

    public static ClaimSavedData get(ServerWorld world) {
        ServerWorld overworld = world.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(ClaimSavedData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        ClaimManager.getAllClaims().clear();

        ListNBT claimList = nbt.getList("claims", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            CompoundNBT claimNbt = claimList.getCompound(i);
            try {
                ClaimData claim = ClaimData.deserializeNBT(claimNbt);
                ClaimManager.getAllClaims().put(claim.getChunkPos().toLong(), claim);
            } catch (Exception e) {
                ThaumicWards.LOGGER.warn("Failed to load claim data at index {}: {}", i, e.getMessage());
            }
        }

        ThaumicWards.LOGGER.info("Loaded {} claims from save data.", ClaimManager.getAllClaims().size());
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        ListNBT claimList = new ListNBT();
        ClaimManager.getAllClaims().values().forEach(claim -> claimList.add(claim.serializeNBT()));
        nbt.put("claims", claimList);
        return nbt;
    }
}

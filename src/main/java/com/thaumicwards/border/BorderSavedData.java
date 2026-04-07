package com.thaumicwards.border;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

public class BorderSavedData extends WorldSavedData {

    private static final String DATA_NAME = ThaumicWards.MOD_ID + "_border";

    public BorderSavedData() {
        super(DATA_NAME);
    }

    public static BorderSavedData get(ServerWorld world) {
        // Always use the overworld for storage
        ServerWorld overworld = world.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(BorderSavedData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        WorldBorderManager.reset();

        if (nbt.contains("overworld_border")) {
            CompoundNBT borderNbt = nbt.getCompound("overworld_border");
            BlockPos center = new BlockPos(
                    borderNbt.getInt("centerX"),
                    borderNbt.getInt("centerY"),
                    borderNbt.getInt("centerZ")
            );
            int radius = borderNbt.getInt("radius");
            // We store just the overworld border for simplicity; can be extended
            WorldBorderManager.getAllBorders().put(
                    net.minecraft.world.World.OVERWORLD,
                    new WorldBorderManager.BorderData(center, radius)
            );
        }

        if (nbt.contains("nether_border")) {
            CompoundNBT borderNbt = nbt.getCompound("nether_border");
            BlockPos center = new BlockPos(
                    borderNbt.getInt("centerX"),
                    borderNbt.getInt("centerY"),
                    borderNbt.getInt("centerZ")
            );
            int radius = borderNbt.getInt("radius");
            WorldBorderManager.getAllBorders().put(
                    net.minecraft.world.World.NETHER,
                    new WorldBorderManager.BorderData(center, radius)
            );
        }

        if (nbt.contains("end_border")) {
            CompoundNBT borderNbt = nbt.getCompound("end_border");
            BlockPos center = new BlockPos(
                    borderNbt.getInt("centerX"),
                    borderNbt.getInt("centerY"),
                    borderNbt.getInt("centerZ")
            );
            int radius = borderNbt.getInt("radius");
            WorldBorderManager.getAllBorders().put(
                    net.minecraft.world.World.END,
                    new WorldBorderManager.BorderData(center, radius)
            );
        }

        ThaumicWards.LOGGER.info("Loaded {} border(s) from save data.", WorldBorderManager.getAllBorders().size());
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        WorldBorderManager.getAllBorders().forEach((dim, data) -> {
            CompoundNBT borderNbt = new CompoundNBT();
            borderNbt.putInt("centerX", data.center.getX());
            borderNbt.putInt("centerY", data.center.getY());
            borderNbt.putInt("centerZ", data.center.getZ());
            borderNbt.putInt("radius", data.radius);

            if (dim == net.minecraft.world.World.OVERWORLD) {
                nbt.put("overworld_border", borderNbt);
            } else if (dim == net.minecraft.world.World.NETHER) {
                nbt.put("nether_border", borderNbt);
            } else if (dim == net.minecraft.world.World.END) {
                nbt.put("end_border", borderNbt);
            }
        });
        return nbt;
    }
}

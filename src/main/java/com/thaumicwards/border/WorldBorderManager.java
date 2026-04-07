package com.thaumicwards.border;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.HashMap;
import java.util.Map;

public class WorldBorderManager {

    private static final Map<RegistryKey<World>, BorderData> borders = new HashMap<>();

    public static void setBorder(ServerWorld world, BlockPos center, int radius) {
        borders.put(world.dimension(), new BorderData(center, radius));
        BorderSavedData.get(world).setDirty();
    }

    public static void removeBorder(ServerWorld world) {
        borders.remove(world.dimension());
        BorderSavedData.get(world).setDirty();
    }

    public static BorderData getBorder(RegistryKey<World> dimension) {
        return borders.get(dimension);
    }

    public static boolean hasBorder(RegistryKey<World> dimension) {
        return borders.containsKey(dimension);
    }

    public static boolean isOutsideBorder(RegistryKey<World> dimension, BlockPos pos) {
        BorderData border = borders.get(dimension);
        if (border == null) {
            return false;
        }
        int dx = Math.abs(pos.getX() - border.center.getX());
        int dz = Math.abs(pos.getZ() - border.center.getZ());
        return dx > border.radius || dz > border.radius;
    }

    public static double distanceToBorder(RegistryKey<World> dimension, BlockPos pos) {
        BorderData border = borders.get(dimension);
        if (border == null) {
            return Double.MAX_VALUE;
        }
        int dx = Math.abs(pos.getX() - border.center.getX());
        int dz = Math.abs(pos.getZ() - border.center.getZ());
        int maxDist = Math.max(dx, dz);
        return border.radius - maxDist;
    }

    public static Map<RegistryKey<World>, BorderData> getAllBorders() {
        return borders;
    }

    public static void reset() {
        borders.clear();
    }

    public static class BorderData {
        public final BlockPos center;
        public final int radius;

        public BorderData(BlockPos center, int radius) {
            this.center = center;
            this.radius = radius;
        }
    }
}

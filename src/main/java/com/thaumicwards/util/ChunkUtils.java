package com.thaumicwards.util;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class ChunkUtils {

    /**
     * Creates an iterator that yields chunk positions in a spiral pattern
     * outward from the center. This ensures chunks nearest to the center
     * are generated first.
     */
    public static Iterator<ChunkPos> spiralIterator(ChunkPos center, int radius) {
        return new Iterator<ChunkPos>() {
            private int x = 0;
            private int z = 0;
            private int dx = 0;
            private int dz = -1;
            private int maxI = (2 * radius + 1) * (2 * radius + 1);
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < maxI;
            }

            @Override
            public ChunkPos next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                ChunkPos result = new ChunkPos(center.x + x, center.z + z);
                i++;

                // Spiral logic
                if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                    int temp = dx;
                    dx = -dz;
                    dz = temp;
                }
                x += dx;
                z += dz;

                return result;
            }
        };
    }

    /**
     * Returns all chunk positions in a rectangle defined by two corners.
     */
    public static List<ChunkPos> getRectangleChunks(ChunkPos corner1, ChunkPos corner2) {
        int minX = Math.min(corner1.x, corner2.x);
        int maxX = Math.max(corner1.x, corner2.x);
        int minZ = Math.min(corner1.z, corner2.z);
        int maxZ = Math.max(corner1.z, corner2.z);

        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    /**
     * Checks if a chunk is within the given distance of any online player.
     */
    public static boolean isChunkNearAnyPlayer(ServerWorld world, ChunkPos pos, int threshold) {
        for (ServerPlayerEntity player : world.players()) {
            int playerChunkX = ((int) player.getX()) >> 4;
            int playerChunkZ = ((int) player.getZ()) >> 4;
            int dx = pos.x - playerChunkX;
            int dz = pos.z - playerChunkZ;
            if (dx * dx + dz * dz <= threshold * threshold) {
                return true;
            }
        }
        return false;
    }

    public static int chunkDistanceSq(ChunkPos a, ChunkPos b) {
        int dx = a.x - b.x;
        int dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}

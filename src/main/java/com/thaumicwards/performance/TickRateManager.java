package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TickRateManager {

    // We track which chunks are near players. Any chunk NOT in this set is "distant".
    private static final Set<Long> nearPlayerChunks = new HashSet<>();
    private static int recalcCounter = 0;

    public static void tick(ServerWorld world) {
        recalcCounter++;
        if (recalcCounter >= 100) {
            recalcCounter = 0;
            recalculate(world);
        }
    }

    private static void recalculate(ServerWorld world) {
        nearPlayerChunks.clear();
        int threshold = ServerConfig.DISTANT_CHUNK_THRESHOLD.get();
        List<ServerPlayerEntity> players = world.players();

        if (players.isEmpty()) {
            return;
        }

        for (ServerPlayerEntity player : players) {
            int playerChunkX = ((int) player.getX()) >> 4;
            int playerChunkZ = ((int) player.getZ()) >> 4;
            for (int dx = -threshold; dx <= threshold; dx++) {
                for (int dz = -threshold; dz <= threshold; dz++) {
                    if (dx * dx + dz * dz <= threshold * threshold) {
                        nearPlayerChunks.add(ChunkPos.asLong(playerChunkX + dx, playerChunkZ + dz));
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given chunk is NOT near any player.
     */
    public static boolean isDistantChunk(ChunkPos pos) {
        return !nearPlayerChunks.contains(pos.toLong());
    }

    public static boolean isDistantChunk(int chunkX, int chunkZ) {
        return !nearPlayerChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static void reset() {
        nearPlayerChunks.clear();
        recalcCounter = 0;
    }
}

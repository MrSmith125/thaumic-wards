package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TickRateManager {

    private static final Set<Long> distantChunks = new HashSet<>();
    private static int recalcCounter = 0;

    public static void tick(ServerWorld world) {
        recalcCounter++;
        if (recalcCounter >= 100) {
            recalcCounter = 0;
            recalculate(world);
        }
    }

    private static void recalculate(ServerWorld world) {
        distantChunks.clear();
        int threshold = ServerConfig.DISTANT_CHUNK_THRESHOLD.get();
        List<ServerPlayerEntity> players = world.players();

        if (players.isEmpty()) {
            return;
        }

        // Get all loaded chunk positions and check distance from players
        world.getChunkSource().chunkMap.getChunks().forEach(chunkHolder -> {
            ChunkPos pos = chunkHolder.getPos();
            boolean isDistant = true;

            for (ServerPlayerEntity player : players) {
                int playerChunkX = ((int) player.getX()) >> 4;
                int playerChunkZ = ((int) player.getZ()) >> 4;
                int dx = pos.x - playerChunkX;
                int dz = pos.z - playerChunkZ;

                if (dx * dx + dz * dz <= threshold * threshold) {
                    isDistant = false;
                    break;
                }
            }

            if (isDistant) {
                distantChunks.add(pos.toLong());
            }
        });
    }

    public static boolean isDistantChunk(ChunkPos pos) {
        return distantChunks.contains(pos.toLong());
    }

    public static boolean isDistantChunk(int chunkX, int chunkZ) {
        return distantChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static void reset() {
        distantChunks.clear();
        recalcCounter = 0;
    }
}

package com.thaumicwards.pregen;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import com.thaumicwards.network.ModNetwork;
import com.thaumicwards.network.PregenProgressPacket;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.UUID;

public class PregenManager {

    private static PregenTask activeTask = null;
    private static ServerWorld activeWorld = null;
    private static UUID initiatorUUID = null;
    private static int tickCounter = 0;
    private static long lastProgressUpdate = 0;

    public static boolean startPregen(ServerWorld world, ChunkPos center, int radius, UUID initiator) {
        if (activeTask != null && activeTask.isRunning()) {
            return false; // Already running
        }

        int maxRadius = ServerConfig.MAX_PREGEN_RADIUS.get();
        if (radius > maxRadius) {
            radius = maxRadius;
        }

        activeTask = new PregenTask(center, radius);
        activeWorld = world;
        initiatorUUID = initiator;
        tickCounter = 0;
        lastProgressUpdate = System.currentTimeMillis();

        ThaumicWards.LOGGER.info("Pre-generation started: center [{}, {}], radius {} ({} total chunks)",
                center.x, center.z, radius, activeTask.getTotalChunks());

        return true;
    }

    public static void stopPregen() {
        if (activeTask != null) {
            activeTask.stop();
            ThaumicWards.LOGGER.info("Pre-generation stopped at {}% ({}/{} chunks)",
                    activeTask.getPercentComplete(),
                    activeTask.getCompletedChunks(),
                    activeTask.getTotalChunks());
            activeTask = null;
            activeWorld = null;
            initiatorUUID = null;
        }
    }

    public static void tick(ServerWorld world) {
        if (activeTask == null || !activeTask.isRunning() || activeWorld != world) {
            return;
        }

        int chunksPerTick = ServerConfig.CHUNKS_PER_TICK.get();

        for (int i = 0; i < chunksPerTick && activeTask.hasNext(); i++) {
            ChunkPos pos = activeTask.pollNext();
            if (pos != null) {
                try {
                    // Force full chunk generation
                    world.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
                    activeTask.incrementCompleted();
                } catch (Exception e) {
                    ThaumicWards.LOGGER.warn("Failed to generate chunk [{}, {}]: {}", pos.x, pos.z, e.getMessage());
                }
            }
        }

        // Send progress updates every 5 seconds or every 50 chunks
        long now = System.currentTimeMillis();
        if (now - lastProgressUpdate >= 5000 || activeTask.getCompletedChunks() % 50 == 0) {
            lastProgressUpdate = now;
            sendProgressUpdate();
        }

        // Check completion
        if (!activeTask.hasNext()) {
            ThaumicWards.LOGGER.info("Pre-generation complete! {} chunks generated.", activeTask.getTotalChunks());
            sendProgressUpdate();
            activeTask = null;
            activeWorld = null;
            initiatorUUID = null;
        }
    }

    private static void sendProgressUpdate() {
        if (activeTask == null) return;

        boolean finished = !activeTask.hasNext();
        PregenProgressPacket packet = new PregenProgressPacket(
                activeTask.getCompletedChunks(),
                activeTask.getTotalChunks(),
                finished
        );

        // Send to all ops, or at least to the initiator
        if (initiatorUUID != null && activeWorld != null) {
            ServerPlayerEntity player = activeWorld.getServer().getPlayerList().getPlayer(initiatorUUID);
            if (player != null) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    public static boolean isRunning() {
        return activeTask != null && activeTask.isRunning();
    }

    public static PregenTask getActiveTask() {
        return activeTask;
    }
}

package com.thaumicwards.network;

import net.minecraft.client.Minecraft;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Random;

/**
 * Client-side packet handling. All methods in this class are only called on the client.
 * This class must NOT be referenced from server-side code paths to avoid ClassNotFoundException
 * on dedicated servers. The packet handlers call these methods via lambda, which is safe because
 * the lambdas are only executed client-side (via enqueueWork on the client network context).
 */
public class ClientPacketHandler {

    private static final Random rand = new Random();

    public static void handlePregenProgress(int completedChunks, int totalChunks, boolean finished) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;

        if (finished) {
            mc.gui.setOverlayMessage(
                    new StringTextComponent("Arcane survey complete! All terrain charted.")
                            .withStyle(TextFormatting.GREEN), false);
        } else {
            int percent = totalChunks > 0 ? (completedChunks * 100 / totalChunks) : 0;
            mc.gui.setOverlayMessage(
                    new StringTextComponent(String.format("Arcane surveying... %d%% (%d/%d chunks)",
                            percent, completedChunks, totalChunks))
                            .withStyle(TextFormatting.LIGHT_PURPLE), false);
        }
    }

    public static void handleBorderParticles(BlockPos center, int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double playerY = mc.player.getY();
        int cx = center.getX();
        int cz = center.getZ();

        // Render particles along the nearest border edges within 32 blocks
        spawnEdgeParticles(mc, playerX, playerY, playerZ, cx - radius, cz - radius, cx + radius, cz - radius);
        spawnEdgeParticles(mc, playerX, playerY, playerZ, cx - radius, cz + radius, cx + radius, cz + radius);
        spawnEdgeParticles(mc, playerX, playerY, playerZ, cx - radius, cz - radius, cx - radius, cz + radius);
        spawnEdgeParticles(mc, playerX, playerY, playerZ, cx + radius, cz - radius, cx + radius, cz + radius);
    }

    private static void spawnEdgeParticles(Minecraft mc, double playerX, double playerY, double playerZ,
                                           int x1, int z1, int x2, int z2) {
        for (int i = 0; i < 20; i++) {
            double t = rand.nextDouble();
            double x = x1 + (x2 - x1) * t;
            double z = z1 + (z2 - z1) * t;

            if (Math.abs(x - playerX) > 32 || Math.abs(z - playerZ) > 32) continue;

            double y = playerY + rand.nextDouble() * 6 - 3;

            mc.level.addParticle(ParticleTypes.ENCHANT, x, y, z,
                    rand.nextGaussian() * 0.05, rand.nextGaussian() * 0.05, rand.nextGaussian() * 0.05);

            if (rand.nextInt(3) == 0) {
                mc.level.addParticle(ParticleTypes.PORTAL, x, y, z,
                        rand.nextGaussian() * 0.1, rand.nextGaussian() * 0.1, rand.nextGaussian() * 0.1);
            }
        }
    }

    public static void handleClaimBoundary(List<ChunkPos> claimedChunks, List<Boolean> isGuild, List<String> factionIds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double playerY = mc.player.getY();

        for (int i = 0; i < claimedChunks.size(); i++) {
            ChunkPos chunk = claimedChunks.get(i);
            boolean guild = isGuild.get(i);
            String factionId = i < factionIds.size() ? factionIds.get(i) : "";
            spawnBoundaryParticles(mc, chunk, guild, factionId, playerY);
        }
    }

    private static void spawnBoundaryParticles(Minecraft mc, ChunkPos chunk, boolean guild, String factionId, double playerY) {
        int baseX = chunk.getMinBlockX();
        int baseZ = chunk.getMinBlockZ();

        for (int edge = 0; edge < 4; edge++) {
            for (int j = 0; j < 4; j++) {
                double t = rand.nextDouble() * 16;
                double x, z;

                switch (edge) {
                    case 0: x = baseX + t; z = baseZ; break;
                    case 1: x = baseX + t; z = baseZ + 16; break;
                    case 2: x = baseX; z = baseZ + t; break;
                    case 3: x = baseX + 16; z = baseZ + t; break;
                    default: continue;
                }

                double y = playerY + rand.nextDouble() * 4 - 2;

                if (guild) {
                    // Faction-colored particles for guild claims
                    if ("crimsons".equals(factionId)) {
                        mc.level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
                    } else if ("mystics".equals(factionId)) {
                        mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.01, 0);
                    } else {
                        mc.level.addParticle(ParticleTypes.WITCH, x, y, z, 0, 0.02, 0);
                    }
                } else {
                    mc.level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0, 0.05, 0);
                }
            }
        }
    }
}

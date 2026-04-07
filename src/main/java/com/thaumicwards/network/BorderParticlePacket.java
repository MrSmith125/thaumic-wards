package com.thaumicwards.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Random;
import java.util.function.Supplier;

public class BorderParticlePacket {

    private final BlockPos center;
    private final int radius;

    public BorderParticlePacket(BlockPos center, int radius) {
        this.center = center;
        this.radius = radius;
    }

    public static void encode(BorderParticlePacket packet, PacketBuffer buf) {
        buf.writeBlockPos(packet.center);
        buf.writeInt(packet.radius);
    }

    public static BorderParticlePacket decode(PacketBuffer buf) {
        return new BorderParticlePacket(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(BorderParticlePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            Random rand = new Random();
            double playerX = mc.player.getX();
            double playerZ = mc.player.getZ();

            // Render particles along the nearest border edges within 32 blocks
            int cx = packet.center.getX();
            int cz = packet.center.getZ();
            int r = packet.radius;

            // North edge (z = cz - r)
            spawnEdgeParticles(mc, playerX, playerZ, cx - r, cz - r, cx + r, cz - r, rand);
            // South edge (z = cz + r)
            spawnEdgeParticles(mc, playerX, playerZ, cx - r, cz + r, cx + r, cz + r, rand);
            // West edge (x = cx - r)
            spawnEdgeParticles(mc, playerX, playerZ, cx - r, cz - r, cx - r, cz + r, rand);
            // East edge (x = cx + r)
            spawnEdgeParticles(mc, playerX, playerZ, cx + r, cz - r, cx + r, cz + r, rand);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void spawnEdgeParticles(Minecraft mc, double playerX, double playerZ,
                                           int x1, int z1, int x2, int z2, Random rand) {
        double playerY = mc.player.getY();

        // Only render nearby portions of the edge
        for (int i = 0; i < 20; i++) {
            double t = rand.nextDouble();
            double x = x1 + (x2 - x1) * t;
            double z = z1 + (z2 - z1) * t;

            if (Math.abs(x - playerX) > 32 || Math.abs(z - playerZ) > 32) {
                continue;
            }

            double y = playerY + rand.nextDouble() * 6 - 3;

            mc.level.addParticle(ParticleTypes.ENCHANT, x, y, z,
                    rand.nextGaussian() * 0.05,
                    rand.nextGaussian() * 0.05,
                    rand.nextGaussian() * 0.05);

            // Add portal particles for extra magic effect
            if (rand.nextInt(3) == 0) {
                mc.level.addParticle(ParticleTypes.PORTAL, x, y, z,
                        rand.nextGaussian() * 0.1,
                        rand.nextGaussian() * 0.1,
                        rand.nextGaussian() * 0.1);
            }
        }
    }
}

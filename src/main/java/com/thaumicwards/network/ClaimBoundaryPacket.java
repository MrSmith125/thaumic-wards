package com.thaumicwards.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class ClaimBoundaryPacket {

    private final List<ChunkPos> claimedChunks;
    private final List<Boolean> isGuild; // true if guild claim, false if personal

    public ClaimBoundaryPacket(List<ChunkPos> claimedChunks, List<Boolean> isGuild) {
        this.claimedChunks = claimedChunks;
        this.isGuild = isGuild;
    }

    public static void encode(ClaimBoundaryPacket packet, PacketBuffer buf) {
        buf.writeInt(packet.claimedChunks.size());
        for (int i = 0; i < packet.claimedChunks.size(); i++) {
            buf.writeInt(packet.claimedChunks.get(i).x);
            buf.writeInt(packet.claimedChunks.get(i).z);
            buf.writeBoolean(packet.isGuild.get(i));
        }
    }

    public static ClaimBoundaryPacket decode(PacketBuffer buf) {
        int size = buf.readInt();
        List<ChunkPos> chunks = new ArrayList<>();
        List<Boolean> guild = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            chunks.add(new ChunkPos(buf.readInt(), buf.readInt()));
            guild.add(buf.readBoolean());
        }
        return new ClaimBoundaryPacket(chunks, guild);
    }

    public static void handle(ClaimBoundaryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            Random rand = new Random();

            for (int i = 0; i < packet.claimedChunks.size(); i++) {
                ChunkPos chunk = packet.claimedChunks.get(i);
                boolean guild = packet.isGuild.get(i);

                // Spawn particles at chunk boundaries
                spawnBoundaryParticles(mc, chunk, guild, rand);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void spawnBoundaryParticles(Minecraft mc, ChunkPos chunk, boolean guild, Random rand) {
        double playerY = mc.player.getY();
        int baseX = chunk.getMinBlockX();
        int baseZ = chunk.getMinBlockZ();

        // Render particles along all 4 edges of the chunk
        for (int edge = 0; edge < 4; edge++) {
            for (int j = 0; j < 4; j++) {
                double t = rand.nextDouble() * 16;
                double x, z;

                switch (edge) {
                    case 0: x = baseX + t; z = baseZ; break;          // North
                    case 1: x = baseX + t; z = baseZ + 16; break;     // South
                    case 2: x = baseX; z = baseZ + t; break;          // West
                    case 3: x = baseX + 16; z = baseZ + t; break;     // East
                    default: continue;
                }

                double y = playerY + rand.nextDouble() * 4 - 2;

                // Guild claims get witch particles (purple), personal get enchant
                if (guild) {
                    mc.level.addParticle(ParticleTypes.WITCH, x, y, z, 0, 0.02, 0);
                } else {
                    mc.level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0, 0.05, 0);
                }
            }
        }
    }
}

package com.thaumicwards.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;

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
            ClientPacketHandler.handleBorderParticles(packet.center, packet.radius);
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.thaumicwards.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PregenProgressPacket {

    private final int completedChunks;
    private final int totalChunks;
    private final boolean finished;

    public PregenProgressPacket(int completedChunks, int totalChunks, boolean finished) {
        this.completedChunks = completedChunks;
        this.totalChunks = totalChunks;
        this.finished = finished;
    }

    public static void encode(PregenProgressPacket packet, PacketBuffer buf) {
        buf.writeInt(packet.completedChunks);
        buf.writeInt(packet.totalChunks);
        buf.writeBoolean(packet.finished);
    }

    public static PregenProgressPacket decode(PacketBuffer buf) {
        return new PregenProgressPacket(buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(PregenProgressPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handlePregenProgress(packet.completedChunks, packet.totalChunks, packet.finished);
        });
        ctx.get().setPacketHandled(true);
    }
}

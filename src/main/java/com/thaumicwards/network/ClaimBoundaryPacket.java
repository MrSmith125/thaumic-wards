package com.thaumicwards.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ClaimBoundaryPacket {

    private final List<ChunkPos> claimedChunks;
    private final List<Boolean> isGuild;

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
            ClientPacketHandler.handleClaimBoundary(packet.claimedChunks, packet.isGuild);
        });
        ctx.get().setPacketHandled(true);
    }
}

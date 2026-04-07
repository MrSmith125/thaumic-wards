package com.thaumicwards.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
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
            if (packet.finished) {
                Minecraft.getInstance().gui.setOverlayMessage(
                        new StringTextComponent("Arcane survey complete! All terrain charted.")
                                .withStyle(TextFormatting.GREEN), false);
            } else {
                int percent = packet.totalChunks > 0 ? (packet.completedChunks * 100 / packet.totalChunks) : 0;
                Minecraft.getInstance().gui.setOverlayMessage(
                        new StringTextComponent(String.format("Arcane surveying... %d%% (%d/%d chunks)",
                                percent, packet.completedChunks, packet.totalChunks))
                                .withStyle(TextFormatting.LIGHT_PURPLE), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

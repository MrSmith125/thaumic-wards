package com.thaumicwards.network;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1.1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ThaumicWards.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void init() {
        CHANNEL.registerMessage(nextId(), BorderParticlePacket.class,
                BorderParticlePacket::encode, BorderParticlePacket::decode, BorderParticlePacket::handle);

        CHANNEL.registerMessage(nextId(), ClaimBoundaryPacket.class,
                ClaimBoundaryPacket::encode, ClaimBoundaryPacket::decode, ClaimBoundaryPacket::handle);

        // Future packets:
        // CHANNEL.registerMessage(nextId(), FactionSyncPacket.class, ...);
        ThaumicWards.LOGGER.info("Thaumic Wards network channel initialized.");
    }
}

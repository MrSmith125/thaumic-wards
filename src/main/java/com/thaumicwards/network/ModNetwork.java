package com.thaumicwards.network;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1.0";
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
        // Packets will be registered here as features are implemented:
        // CHANNEL.registerMessage(nextId(), PregenProgressPacket.class, ...);
        // CHANNEL.registerMessage(nextId(), BorderParticlePacket.class, ...);
        // CHANNEL.registerMessage(nextId(), ClaimBoundaryPacket.class, ...);
        // CHANNEL.registerMessage(nextId(), FactionSyncPacket.class, ...);
        ThaumicWards.LOGGER.info("Thaumic Wards network channel initialized.");
    }
}

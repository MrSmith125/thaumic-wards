package com.thaumicwards.events;

import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.factions.FactionManager;
import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionWarStatus;
import com.thaumicwards.factions.ProgressionManager;
import com.thaumicwards.network.ClaimBoundaryPacket;
import com.thaumicwards.network.ModNetwork;
import com.thaumicwards.performance.ChunkLoadHandler;
import com.thaumicwards.performance.TickRateManager;
import com.thaumicwards.pregen.PregenManager;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ServerTickHandler {

    private static int claimParticleCounter = 0;
    private static int progressionCounter = 0;
    private static int warStatusCounter = 0;
    private static int buffCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        if (!(event.world instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) event.world;

        // Update tick rate manager for distant chunk tracking
        TickRateManager.tick(world);

        // Reset chunk load counter each tick
        ChunkLoadHandler.resetCounter();

        // Run chunk pre-generation if active
        PregenManager.tick(world);

        // Progression system — award playtime points every 1200 ticks (1 minute)
        progressionCounter++;
        if (progressionCounter >= 1200) {
            progressionCounter = 0;
            ProgressionManager.tick(world);
        }

        // War status recalculation
        warStatusCounter++;
        if (warStatusCounter >= ServerConfig.BUFF_RECALCULATION_INTERVAL_TICKS.get()) {
            warStatusCounter = 0;
            FactionWarStatus.recalculate();
        }

        // Faction buff application
        buffCounter++;
        if (buffCounter >= ServerConfig.BUFF_APPLICATION_INTERVAL_TICKS.get()) {
            buffCounter = 0;
            FactionBuffHandler.applyBuffs(world);
        }

        // Send claim boundary particles to nearby players every 40 ticks
        claimParticleCounter++;
        if (claimParticleCounter >= 40) {
            claimParticleCounter = 0;
            sendClaimParticles(world);
        }
    }

    private static void sendClaimParticles(ServerWorld world) {
        for (ServerPlayerEntity player : world.players()) {
            ChunkPos playerChunk = new ChunkPos(player.blockPosition());
            List<ClaimData> nearbyClaims = ClaimManager.getClaimsNear(playerChunk, 4);

            if (!nearbyClaims.isEmpty()) {
                List<ChunkPos> chunks = new ArrayList<>();
                List<Boolean> isGuild = new ArrayList<>();
                List<String> factionIds = new ArrayList<>();
                for (ClaimData claim : nearbyClaims) {
                    chunks.add(claim.getChunkPos());
                    isGuild.add(claim.isGuild());
                    // Determine faction string ID for coloring
                    String factionStringId = "";
                    if (claim.isGuild() && claim.getFactionId() != null) {
                        Faction faction = FactionManager.getFaction(claim.getFactionId());
                        if (faction != null) {
                            factionStringId = faction.getStringId();
                        }
                    }
                    factionIds.add(factionStringId);
                }
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new ClaimBoundaryPacket(chunks, isGuild, factionIds));
            }
        }
    }
}

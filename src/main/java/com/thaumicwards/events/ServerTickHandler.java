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
import com.thaumicwards.performance.PerformanceProfiler;
import com.thaumicwards.performance.TPSMonitor;
import com.thaumicwards.performance.TickRateManager;
import com.thaumicwards.scoreboard.FactionScoreboard;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ServerTickHandler {

    // Stagger counters so periodic tasks don't all fire on the same tick
    private static int claimParticleCounter = 20;
    private static int progressionCounter = 0;
    private static int warStatusCounter = 300;
    private static int buffCounter = 150;
    private static int scoreboardCounter = 0;
    private static int adaptiveCounter = 100; // staggered

    @SubscribeEvent
    public static void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        if (!(event.world instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) event.world;

        // Only run global logic once per tick (overworld only) to avoid
        // running counters 2-3x fast when nether/end are loaded
        if (world.dimension() != World.OVERWORLD) return;

        // Track TPS
        TPSMonitor.recordTick();

        // Performance profiler: clear entity counters and record player count
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (profiler.isEnabled()) {
            profiler.clearEntityCounters();
            profiler.recordPlayerCount(world.players().size());
        }

        // Update tick rate manager for distant chunk tracking
        TickRateManager.tick(world);

        // Reset chunk load counter each tick
        ChunkLoadHandler.resetCounter();

        // Reset redstone throttle counters
        com.thaumicwards.performance.RedstoneThrottler.resetCounts();

        // Tick stress test simulation (if active)
        com.thaumicwards.commands.StressTestCommand.tickSimulation(world);

        // Adaptive TPS throttler - every 200 ticks
        adaptiveCounter++;
        if (adaptiveCounter >= 200) {
            adaptiveCounter = 0;
            com.thaumicwards.performance.AdaptiveThrottler.tick();
        }

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

        // Scoreboard update
        scoreboardCounter++;
        if (scoreboardCounter >= com.thaumicwards.performance.AdaptiveThrottler.getEffectiveScoreboardInterval()) {
            scoreboardCounter = 0;
            FactionScoreboard.updateSidebar(world.getServer());
        }

        // Send claim boundary particles to nearby players every 40 ticks
        claimParticleCounter++;
        if (claimParticleCounter >= com.thaumicwards.performance.AdaptiveThrottler.getEffectiveClaimParticleInterval()) {
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

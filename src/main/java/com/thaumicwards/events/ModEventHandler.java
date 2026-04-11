package com.thaumicwards.events;

import com.thaumicwards.border.BorderEnforcementHandler;
import com.thaumicwards.border.BorderSavedData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.claims.ClaimProtectionHandler;
import com.thaumicwards.commands.ModCommands;
import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import com.thaumicwards.factions.*;
import com.thaumicwards.performance.AutoOptimizer;
import com.thaumicwards.performance.ChunkLoadHandler;
import com.thaumicwards.performance.EntityTickHandler;
import com.thaumicwards.performance.PerformanceProfiler;
import com.thaumicwards.performance.TickRateManager;
import com.thaumicwards.restart.RestartScheduler;
import com.thaumicwards.scoreboard.FactionScoreboard;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.UUID;

public class ModEventHandler {

    private static boolean handlersRegistered = false;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        ThaumicWards.LOGGER.info("Thaumic Wards commands registered.");
    }

    @SubscribeEvent
    public static void onServerStarting(FMLServerStartingEvent event) {
        ThaumicWards.LOGGER.info("Thaumic Wards server starting - initializing managers...");

        // Register event handlers only once to prevent duplicate registration on server restart
        if (!handlersRegistered) {
            MinecraftForge.EVENT_BUS.register(EntityTickHandler.class);
            MinecraftForge.EVENT_BUS.register(ChunkLoadHandler.class);
            MinecraftForge.EVENT_BUS.register(ServerTickHandler.class);
            MinecraftForge.EVENT_BUS.register(BorderEnforcementHandler.class);
            MinecraftForge.EVENT_BUS.register(ClaimProtectionHandler.class);
            MinecraftForge.EVENT_BUS.register(FactionPvPHandler.class);
            MinecraftForge.EVENT_BUS.register(ChatHandler.class);
            MinecraftForge.EVENT_BUS.register(com.thaumicwards.performance.RedstoneThrottler.class);
            MinecraftForge.EVENT_BUS.register(com.thaumicwards.performance.EntityCleanup.class);
            MinecraftForge.EVENT_BUS.register(PerformanceProfiler.class);
            MinecraftForge.EVENT_BUS.register(RestartScheduler.class);
            handlersRegistered = true;
        }

        // Load saved data - order matters: factions first, then progression
        BorderSavedData.get(event.getServer().overworld());
        ClaimManager.init(event.getServer().overworld());
        FactionManager.init(event.getServer().overworld());
        ProgressionManager.init(event.getServer().overworld());
        FactionKillTracker.init(event.getServer().overworld());
        ContestedZoneManager.init(event.getServer().overworld());
        FactionWarStatus.recalculate();
        FactionScoreboard.init(event.getServer());

        // Run AutoOptimizer on first startup
        if (ServerConfig.AUTO_OPTIMIZE_ENABLED.get()) {
            AutoOptimizer.runOptimizations(event.getServer());
        }

        // Auto-start profiler if configured
        if (ServerConfig.PROFILER_AUTO_START.get()) {
            PerformanceProfiler.getInstance().setEnabled(true);
        }

        // Initialize auto-restart scheduler
        RestartScheduler.init();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        UUID playerId = player.getUUID();

        // Auto-join: assign to smaller faction on first login
        if (!FactionManager.isPlayerInFaction(playerId) && ServerConfig.AUTO_JOIN_ENABLED.get()) {
            Faction faction = FactionManager.joinFaction(playerId, player.getName().getString());
            if (faction != null) {
                player.displayClientMessage(new StringTextComponent(
                        "The arcane winds have chosen you for ")
                        .withStyle(TextFormatting.LIGHT_PURPLE)
                        .append(new StringTextComponent(faction.getName())
                            .withStyle(faction.getFactionColor(), TextFormatting.BOLD))
                        .append(new StringTextComponent("!")
                            .withStyle(TextFormatting.LIGHT_PURPLE)), false);
                player.displayClientMessage(new StringTextComponent(
                        "You join as an Initiate. Earn Arcane Power through playtime and combat to rank up!")
                        .withStyle(TextFormatting.GRAY), false);

                FactionScoreboard.assignPlayerTeam(player, faction);
            }
        } else {
            // Returning player - restore their team color
            Faction faction = FactionManager.getPlayerFaction(playerId);
            if (faction != null) {
                FactionScoreboard.assignPlayerTeam(player, faction);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        ThaumicWards.LOGGER.info("Thaumic Wards server stopping - saving data...");
        PerformanceProfiler.getInstance().setEnabled(false);
        RestartScheduler.reset();
        com.thaumicwards.performance.EntityCleanup.reset();
        com.thaumicwards.performance.AdaptiveThrottler.reset();
        com.thaumicwards.performance.RedstoneThrottler.reset();
        FactionScoreboard.reset();
        TickRateManager.reset();
        ClaimManager.reset();
        FactionWarStatus.reset();
        ContestedZoneManager.reset();
        FactionKillTracker.reset();
        ProgressionManager.reset();
        FactionManager.reset();
    }
}

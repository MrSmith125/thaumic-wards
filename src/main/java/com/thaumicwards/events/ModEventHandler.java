package com.thaumicwards.events;

import com.thaumicwards.commands.ModCommands;
import com.thaumicwards.core.ThaumicWards;
import com.thaumicwards.performance.ChunkLoadHandler;
import com.thaumicwards.performance.EntityTickHandler;
import com.thaumicwards.performance.TickRateManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

public class ModEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        ThaumicWards.LOGGER.info("Thaumic Wards commands registered.");
    }

    @SubscribeEvent
    public static void onServerStarting(FMLServerStartingEvent event) {
        ThaumicWards.LOGGER.info("Thaumic Wards server starting - initializing managers...");
        // Register performance handlers
        MinecraftForge.EVENT_BUS.register(EntityTickHandler.class);
        MinecraftForge.EVENT_BUS.register(ChunkLoadHandler.class);
        MinecraftForge.EVENT_BUS.register(ServerTickHandler.class);
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        ThaumicWards.LOGGER.info("Thaumic Wards server stopping - saving data...");
        TickRateManager.reset();
    }
}

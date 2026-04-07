package com.thaumicwards.events;

import com.thaumicwards.commands.ModCommands;
import com.thaumicwards.core.ThaumicWards;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.world.WorldEvent;
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
        // Managers will be initialized here as features are implemented
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        ThaumicWards.LOGGER.info("Thaumic Wards server stopping - saving data...");
        // Cleanup and force-save will happen here
    }
}

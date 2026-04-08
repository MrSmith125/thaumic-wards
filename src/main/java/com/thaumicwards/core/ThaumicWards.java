package com.thaumicwards.core;

import com.thaumicwards.blocks.ModBlocks;
import com.thaumicwards.blocks.ModTileEntities;
import com.thaumicwards.config.ClientConfig;
import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.events.ModEventHandler;
import com.thaumicwards.items.ModItems;
import com.thaumicwards.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ThaumicWards.MOD_ID)
public class ThaumicWards {

    public static final String MOD_ID = "thaumic_wards";
    public static final Logger LOGGER = LogManager.getLogger();

    public ThaumicWards() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);

        // Register items, blocks, and tile entities
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModTileEntities.TILE_ENTITIES.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(ModEventHandler.class);

        LOGGER.info("Thaumic Wards initializing - magical protection awaits!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModNetwork.init();
        ModSetup.init(event);
        LOGGER.info("Thaumic Wards common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ClientSetup.init(event);
        LOGGER.info("Thaumic Wards client setup complete.");
    }
}

package com.thaumicwards.blocks;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModTileEntities {

    public static final DeferredRegister<TileEntityType<?>> TILE_ENTITIES =
            DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, ThaumicWards.MOD_ID);

    public static final RegistryObject<TileEntityType<WardStoneTileEntity>> WARD_STONE_TE =
            TILE_ENTITIES.register("ward_stone",
                    () -> TileEntityType.Builder.of(WardStoneTileEntity::new, ModBlocks.WARD_STONE.get()).build(null));

    public static final RegistryObject<TileEntityType<GuildNexusTileEntity>> GUILD_NEXUS_TE =
            TILE_ENTITIES.register("guild_nexus",
                    () -> TileEntityType.Builder.of(GuildNexusTileEntity::new, ModBlocks.GUILD_NEXUS.get()).build(null));
}

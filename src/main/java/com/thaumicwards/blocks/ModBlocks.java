package com.thaumicwards.blocks;

import com.thaumicwards.core.ThaumicWards;
import com.thaumicwards.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ThaumicWards.MOD_ID);

    public static final RegistryObject<Block> WARD_STONE = BLOCKS.register("ward_stone",
            WardStoneBlock::new);

    // Guild Nexus will be added in Phase 6

    // Register block items
    public static final RegistryObject<Item> WARD_STONE_ITEM = ModItems.ITEMS.register("ward_stone",
            () -> new BlockItem(WARD_STONE.get(), new Item.Properties().tab(ItemGroup.TAB_BUILDING_BLOCKS)));
}

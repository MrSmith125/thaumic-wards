package com.thaumicwards.items;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ThaumicWards.MOD_ID);

    public static final RegistryObject<Item> WARDING_WAND = ITEMS.register("warding_wand",
            () -> new WardingWandItem(new Item.Properties()
                    .stacksTo(1)
                    .tab(ItemGroup.TAB_TOOLS)));

    public static final RegistryObject<Item> FACTION_SIGIL = ITEMS.register("faction_sigil",
            () -> new FactionSigilItem(new Item.Properties()
                    .stacksTo(1)
                    .tab(ItemGroup.TAB_TOOLS)));

    // Block items are registered in ModBlocks
}

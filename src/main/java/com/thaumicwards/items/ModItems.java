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

    // Faction Sigil will be added in Phase 6
    // Block items will be added when blocks are created
}

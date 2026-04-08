package com.thaumicwards.blocks;

import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionManager;
import com.thaumicwards.factions.FactionPermissions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;

public class GuildNexusBlock extends Block {

    public GuildNexusBlock() {
        super(Properties.of(Material.STONE)
                .strength(75.0f, 2400.0f) // Extremely hard to break
                .lightLevel(state -> 12) // Bright magical glow
                .sound(SoundType.STONE)
                .noOcclusion());
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new GuildNexusTileEntity();
    }

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        if (!world.isClientSide && placer instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) placer;
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                player.displayClientMessage(new StringTextComponent(
                        "You must be in a guild to place a Guild Nexus.").withStyle(TextFormatting.RED), false);
                // Remove the block
                world.removeBlock(pos, false);
                return;
            }

            if (!FactionPermissions.canPlaceNexus(factionId, player.getUUID())) {
                player.displayClientMessage(new StringTextComponent(
                        "Only Masters and the Archon can place a Guild Nexus.").withStyle(TextFormatting.RED), false);
                world.removeBlock(pos, false);
                return;
            }

            Faction faction = FactionManager.getFaction(factionId);
            faction.setNexusPos(pos);

            player.displayClientMessage(new StringTextComponent(
                    String.format("The Guild Nexus of %s pulses with arcane energy!", faction.getName()))
                    .withStyle(TextFormatting.LIGHT_PURPLE), false);
        }
    }

    @Override
    public void playerWillDestroy(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClientSide) {
            // Find which faction owns this nexus
            for (Faction faction : FactionManager.getAllFactions()) {
                if (pos.equals(faction.getNexusPos())) {
                    if (faction.isMember(player.getUUID())) {
                        faction.setNexusPos(null);
                        if (player instanceof ServerPlayerEntity) {
                            ((ServerPlayerEntity) player).displayClientMessage(new StringTextComponent(
                                    "The Guild Nexus has been dismantled.").withStyle(TextFormatting.YELLOW), false);
                        }
                    }
                    break;
                }
            }
        }
        super.playerWillDestroy(world, pos, state, player);
    }
}

package com.thaumicwards.blocks;

import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class WardStoneBlock extends Block {

    public WardStoneBlock() {
        super(Properties.of(Material.STONE)
                .strength(50.0f, 1200.0f) // Very hard to break
                .lightLevel(state -> 7) // Magical glow
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
        return new WardStoneTileEntity();
    }

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        if (!world.isClientSide && placer instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) placer;
            ChunkPos chunkPos = new ChunkPos(pos);

            ClaimManager.ClaimResult result = ClaimManager.claimChunk(
                    chunkPos, player.getUUID(), player.getName().getString(),
                    ClaimData.ClaimType.PERSONAL, null);

            switch (result) {
                case SUCCESS:
                    player.displayClientMessage(
                            new StringTextComponent("The Ward Stone hums with power, sealing this land under your protection!")
                                    .withStyle(TextFormatting.GREEN), false);
                    break;
                case ALREADY_CLAIMED:
                    player.displayClientMessage(
                            new StringTextComponent("This land is already warded by another mage.")
                                    .withStyle(TextFormatting.RED), false);
                    break;
                case MAX_CLAIMS_REACHED:
                    player.displayClientMessage(
                            new StringTextComponent("Your magical reserves are depleted - you cannot ward more land.")
                                    .withStyle(TextFormatting.RED), false);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void playerWillDestroy(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClientSide) {
            ChunkPos chunkPos = new ChunkPos(pos);
            ClaimData claim = ClaimManager.getClaimAt(chunkPos);

            if (claim != null && claim.getOwnerUUID().equals(player.getUUID())) {
                ClaimManager.unclaimChunk(chunkPos, player.getUUID());
                if (player instanceof ServerPlayerEntity) {
                    ((ServerPlayerEntity) player).displayClientMessage(
                            new StringTextComponent("The wards dissolve, releasing this land from your protection.")
                                    .withStyle(TextFormatting.YELLOW), false);
                }
            }
        }
        super.playerWillDestroy(world, pos, state, player);
    }
}

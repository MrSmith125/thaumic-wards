package com.thaumicwards.blocks;

import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionManager;
import com.thaumicwards.factions.FactionRank;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Outpost block that factions can place in unclaimed territory.
 * Enemy factions can attack (right-click) to damage and eventually capture it.
 */
public class OutpostBlock extends Block {

    public OutpostBlock() {
        super(Properties.of(Material.STONE)
                .strength(50.0f, 1800.0f) // Very hard
                .lightLevel(state -> 10) // Magical glow
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
        return new OutpostTileEntity();
    }

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        if (!world.isClientSide && placer instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) placer;
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                player.displayClientMessage(new StringTextComponent(
                        "You must be in a faction to place an outpost.").withStyle(TextFormatting.RED), false);
                world.removeBlock(pos, false);
                return;
            }

            Faction faction = FactionManager.getFaction(factionId);
            FactionRank rank = faction.getRank(player.getUUID());

            // Require WARLOCK+ rank
            if (rank == null || rank.getLevel() < FactionRank.WARLOCK.getLevel()) {
                player.displayClientMessage(new StringTextComponent(
                        "Only Warlocks and above can place outposts.").withStyle(TextFormatting.RED), false);
                world.removeBlock(pos, false);
                return;
            }

            // Must be in unclaimed territory
            ChunkPos chunkPos = new ChunkPos(pos);
            if (ClaimManager.isChunkClaimed(chunkPos)) {
                player.displayClientMessage(new StringTextComponent(
                        "Outposts can only be placed in unclaimed territory.").withStyle(TextFormatting.RED), false);
                world.removeBlock(pos, false);
                return;
            }

            // Claim the chunk as an outpost
            ClaimManager.ClaimResult result = ClaimManager.claimChunk(
                    chunkPos, player.getUUID(), faction.getName(),
                    ClaimData.ClaimType.OUTPOST, factionId);

            if (result == ClaimManager.ClaimResult.SUCCESS) {
                TileEntity te = world.getBlockEntity(pos);
                if (te instanceof OutpostTileEntity) {
                    ((OutpostTileEntity) te).setOwningFactionId(factionId);
                }

                player.displayClientMessage(new StringTextComponent(
                        String.format("Outpost established for %s! Defend it from the enemy!", faction.getName()))
                        .withStyle(faction.getFactionColor(), TextFormatting.BOLD), false);
            } else {
                player.displayClientMessage(new StringTextComponent(
                        "Failed to claim territory for the outpost.").withStyle(TextFormatting.RED), false);
                world.removeBlock(pos, false);
            }
        }
    }

    /**
     * Right-click interaction — enemy faction members attack the outpost.
     * Allied faction members see outpost status.
     */
    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (world.isClientSide) return ActionResultType.SUCCESS;

        TileEntity te = world.getBlockEntity(pos);
        if (!(te instanceof OutpostTileEntity)) return ActionResultType.PASS;

        OutpostTileEntity outpost = (OutpostTileEntity) te;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        UUID playerFactionId = FactionManager.getPlayerFactionId(player.getUUID());

        if (playerFactionId == null) {
            serverPlayer.displayClientMessage(new StringTextComponent(
                    "You must join a faction to interact with outposts.")
                    .withStyle(TextFormatting.GRAY), false);
            return ActionResultType.CONSUME;
        }

        if (outpost.getOwningFactionId() != null && outpost.getOwningFactionId().equals(playerFactionId)) {
            // Allied — show status
            Faction owningFaction = FactionManager.getFaction(outpost.getOwningFactionId());
            serverPlayer.displayClientMessage(new StringTextComponent(
                    String.format("Outpost Status: %d/%d HP | Owned by %s",
                            outpost.getHealth(), outpost.getMaxHealth(),
                            owningFaction != null ? owningFaction.getName() : "Unknown"))
                    .withStyle(TextFormatting.GREEN), true);
            return ActionResultType.CONSUME;
        }

        // Enemy — attack!
        boolean captured = outpost.attack(serverPlayer);
        if (captured) {
            // The outpost was captured — the tile entity handled the claim swap
            // Block stays in place with new ownership
        }

        return ActionResultType.CONSUME;
    }

    @Override
    public void playerWillDestroy(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClientSide) {
            TileEntity te = world.getBlockEntity(pos);
            if (te instanceof OutpostTileEntity) {
                OutpostTileEntity outpost = (OutpostTileEntity) te;
                // Only allow the owning faction to break it
                UUID playerFactionId = FactionManager.getPlayerFactionId(player.getUUID());
                if (outpost.getOwningFactionId() != null && outpost.getOwningFactionId().equals(playerFactionId)) {
                    // Unclaim the chunk
                    ChunkPos chunkPos = new ChunkPos(pos);
                    ClaimManager.forceUnclaim(chunkPos);
                    if (player instanceof ServerPlayerEntity) {
                        ((ServerPlayerEntity) player).displayClientMessage(new StringTextComponent(
                                "Outpost dismantled. Territory released.").withStyle(TextFormatting.YELLOW), false);
                    }
                }
            }
        }
        super.playerWillDestroy(world, pos, state, player);
    }
}

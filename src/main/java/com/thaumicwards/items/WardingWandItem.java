package com.thaumicwards.items;

import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.util.ChunkUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;

public class WardingWandItem extends Item {

    public WardingWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClientSide) {
            return ActionResult.success(player.getItemInHand(hand));
        }

        ItemStack stack = player.getItemInHand(hand);
        CompoundNBT nbt = stack.getOrCreateTag();
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        ChunkPos currentChunk = new ChunkPos(player.blockPosition());

        if (!nbt.contains("firstCornerX")) {
            // First corner selection
            nbt.putInt("firstCornerX", currentChunk.x);
            nbt.putInt("firstCornerZ", currentChunk.z);

            serverPlayer.displayClientMessage(
                    new StringTextComponent(String.format(
                            "First ward anchor set at chunk [%d, %d]. Use again to set the second anchor.",
                            currentChunk.x, currentChunk.z))
                            .withStyle(TextFormatting.LIGHT_PURPLE), false);

        } else {
            // Second corner - attempt to claim
            int firstX = nbt.getInt("firstCornerX");
            int firstZ = nbt.getInt("firstCornerZ");
            ChunkPos firstCorner = new ChunkPos(firstX, firstZ);

            // Clear the saved corner
            nbt.remove("firstCornerX");
            nbt.remove("firstCornerZ");

            List<ChunkPos> chunks = ChunkUtils.getRectangleChunks(firstCorner, currentChunk);

            int successCount = 0;
            int failCount = 0;

            for (ChunkPos chunk : chunks) {
                ClaimManager.ClaimResult result = ClaimManager.claimChunk(
                        chunk, player.getUUID(), player.getName().getString(),
                        ClaimData.ClaimType.PERSONAL, null);

                if (result == ClaimManager.ClaimResult.SUCCESS) {
                    successCount++;
                } else if (result == ClaimManager.ClaimResult.MAX_CLAIMS_REACHED) {
                    serverPlayer.displayClientMessage(
                            new StringTextComponent("Your magical reserves are depleted - you cannot ward more land.")
                                    .withStyle(TextFormatting.RED), false);
                    break;
                } else {
                    failCount++;
                }
            }

            if (successCount > 0) {
                serverPlayer.displayClientMessage(
                        new StringTextComponent(String.format(
                                "The arcane wards seal %d chunk(s) under your protection!", successCount))
                                .withStyle(TextFormatting.GREEN), false);
            }
            if (failCount > 0) {
                serverPlayer.displayClientMessage(
                        new StringTextComponent(String.format(
                                "%d chunk(s) were already warded by another mage.", failCount))
                                .withStyle(TextFormatting.YELLOW), false);
            }
        }

        return ActionResult.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Enchanted glint effect
    }
}

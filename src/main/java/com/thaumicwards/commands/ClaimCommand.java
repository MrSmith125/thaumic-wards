package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class ClaimCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards").then(
                Commands.literal("claim")
                    .executes(ClaimCommand::claimChunk)
            ).then(
                Commands.literal("unclaim")
                    .executes(ClaimCommand::unclaimChunk)
            ).then(
                Commands.literal("claims")
                    .executes(ClaimCommand::listClaims)
            )
        );
    }

    private static int claimChunk(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimManager.ClaimResult result = ClaimManager.claimChunk(
                    chunkPos, player.getUUID(), player.getName().getString(),
                    ClaimData.ClaimType.PERSONAL, null);

            switch (result) {
                case SUCCESS:
                    context.getSource().sendSuccess(new StringTextComponent(
                            "The arcane wards seal this land under your protection!")
                            .withStyle(TextFormatting.GREEN), false);
                    return 1;
                case ALREADY_CLAIMED:
                    context.getSource().sendFailure(new StringTextComponent(
                            "Another mage has already warded this territory."));
                    return 0;
                case MAX_CLAIMS_REACHED:
                    context.getSource().sendFailure(new StringTextComponent(
                            "Your magical reserves are depleted - you cannot ward more land."));
                    return 0;
                default:
                    context.getSource().sendFailure(new StringTextComponent("Failed to claim chunk."));
                    return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static int unclaimChunk(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimData claim = ClaimManager.getClaimAt(chunkPos);
            if (claim == null) {
                context.getSource().sendFailure(new StringTextComponent(
                        "This land is not warded by anyone."));
                return 0;
            }

            if (ClaimManager.unclaimChunk(chunkPos, player.getUUID())) {
                context.getSource().sendSuccess(new StringTextComponent(
                        "The wards dissolve, releasing this land from your protection.")
                        .withStyle(TextFormatting.YELLOW), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "You do not have the authority to dispel these wards."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static int listClaims(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            List<ClaimData> claims = ClaimManager.getPlayerPersonalClaims(player.getUUID());

            if (claims.isEmpty()) {
                context.getSource().sendSuccess(new StringTextComponent(
                        "You have no warded territories.").withStyle(TextFormatting.GRAY), false);
            } else {
                context.getSource().sendSuccess(new StringTextComponent(
                        "=== Your Warded Territories ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
                for (ClaimData claim : claims) {
                    context.getSource().sendSuccess(new StringTextComponent(
                            String.format("  Chunk [%d, %d] - %s",
                                    claim.getChunkPos().x, claim.getChunkPos().z,
                                    claim.getType().name()))
                            .withStyle(TextFormatting.LIGHT_PURPLE), false);
                }
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("Total: %d claimed", claims.size()))
                        .withStyle(TextFormatting.GRAY), false);
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }
}

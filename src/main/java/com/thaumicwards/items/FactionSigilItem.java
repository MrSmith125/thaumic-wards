package com.thaumicwards.items;

import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.factions.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FactionSigilItem extends Item {

    public FactionSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClientSide) {
            return ActionResult.success(player.getItemInHand(hand));
        }

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        Faction faction = FactionManager.getPlayerFaction(player.getUUID());

        if (faction == null) {
            serverPlayer.displayClientMessage(new StringTextComponent(
                    "The sigil pulses faintly... You belong to no faction. Use /thaumicwards join to enlist.")
                    .withStyle(TextFormatting.GRAY), false);
            return ActionResult.fail(player.getItemInHand(hand));
        }

        // Faction header with color
        serverPlayer.displayClientMessage(new StringTextComponent(
                "=== " + faction.getName() + " ===")
                .withStyle(faction.getFactionColor(), TextFormatting.BOLD), false);

        // Leaders
        List<String> leaderNames = faction.getLeaderNames();
        String leadersStr = leaderNames.isEmpty() ? "None appointed" : String.join(", ", leaderNames);
        serverPlayer.displayClientMessage(new StringTextComponent(
                "Leaders: " + leadersStr).withStyle(TextFormatting.LIGHT_PURPLE), false);

        // Player's rank and progression
        FactionRank playerRank = faction.getRank(player.getUUID());
        serverPlayer.displayClientMessage(new StringTextComponent(
                "Your Rank: " + (playerRank != null ? playerRank.getDisplayName() : "Unknown"))
                .withStyle(playerRank != null ? playerRank.getColor() : TextFormatting.GRAY), false);

        // Arcane Power
        PlayerProgressionData progression = ProgressionManager.getData(player.getUUID());
        if (progression != null) {
            serverPlayer.displayClientMessage(new StringTextComponent(
                    String.format("Arcane Power: %d | Kills: %d | Playtime: %dh %dm",
                            progression.getArcanePower(),
                            progression.getFactionKills(),
                            progression.getPlaytimeMinutes() / 60,
                            progression.getPlaytimeMinutes() % 60))
                    .withStyle(TextFormatting.AQUA), false);

            if (progression.getPointsToNextRank() > 0) {
                serverPlayer.displayClientMessage(new StringTextComponent(
                        String.format("Next rank in: %d Arcane Power (%d%%)",
                                progression.getPointsToNextRank(), progression.getProgressPercent()))
                        .withStyle(TextFormatting.GRAY), false);
            } else if (playerRank == FactionRank.WARLOCK) {
                serverPlayer.displayClientMessage(new StringTextComponent(
                        "Max auto-rank reached. A Leader must promote you to Archmage.")
                        .withStyle(TextFormatting.GRAY), false);
            }
        }

        // Faction stats
        serverPlayer.displayClientMessage(new StringTextComponent(
                String.format("Members: %d | Territory: %d/%d chunks",
                        faction.getMemberCount(),
                        ClaimManager.getFactionClaims(faction.getFactionId()).size(),
                        faction.getMaxGuildClaims()))
                .withStyle(TextFormatting.GRAY), false);

        // Personal claims
        int personalClaims = ClaimManager.getPlayerPersonalClaims(player.getUUID()).size();
        int maxClaims = ClaimManager.getMaxPersonalClaims(player.getUUID());
        serverPlayer.displayClientMessage(new StringTextComponent(
                String.format("Your Claims: %d/%d", personalClaims, maxClaims))
                .withStyle(TextFormatting.GRAY), false);

        return ActionResult.success(player.getItemInHand(hand));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}

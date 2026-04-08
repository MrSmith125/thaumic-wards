package com.thaumicwards.items;

import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionManager;
import com.thaumicwards.factions.FactionRank;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

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
                    "The sigil pulses faintly... You belong to no guild.")
                    .withStyle(TextFormatting.GRAY), false);
            return ActionResult.fail(player.getItemInHand(hand));
        }

        // Display faction info in chat (GUI will be added later)
        serverPlayer.displayClientMessage(new StringTextComponent(
                "=== " + faction.getName() + " Guild ===")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);

        FactionRank playerRank = faction.getRank(player.getUUID());
        serverPlayer.displayClientMessage(new StringTextComponent(
                "Your Rank: " + (playerRank != null ? playerRank.getDisplayName() : "Unknown"))
                .withStyle(playerRank != null ? playerRank.getColor() : TextFormatting.GRAY), false);

        serverPlayer.displayClientMessage(new StringTextComponent(
                String.format("Members: %d | Territory: %d/%d chunks",
                        faction.getMemberCount(),
                        ClaimManager.getFactionClaims(faction.getFactionId()).size(),
                        faction.getMaxGuildClaims()))
                .withStyle(TextFormatting.GRAY), false);

        serverPlayer.displayClientMessage(new StringTextComponent("--- Members ---")
                .withStyle(TextFormatting.LIGHT_PURPLE), false);

        for (Map.Entry<UUID, FactionRank> entry : faction.getMembers().entrySet()) {
            FactionRank rank = entry.getValue();
            String memberName = faction.getMemberName(entry.getKey());
            serverPlayer.displayClientMessage(new StringTextComponent(
                    String.format("  [%s] %s", rank.getDisplayName(), memberName))
                    .withStyle(rank.getColor()), false);
        }

        return ActionResult.success(player.getItemInHand(hand));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}

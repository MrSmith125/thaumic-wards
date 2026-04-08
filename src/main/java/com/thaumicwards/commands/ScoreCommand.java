package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.factions.*;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("score")
                    .executes(ScoreCommand::showScore))
                .then(Commands.literal("stats")
                    .executes(ScoreCommand::showStats))
        );
    }

    // --- /thaumicwards score — Faction scoreboard ---

    private static int showScore(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();

        Faction mystics = FactionManager.getMystics();
        Faction crimsons = FactionManager.getCrimsons();

        int mysticKills = FactionKillTracker.getFactionTotalKills(Faction.MYSTICS_STRING_ID);
        int crimsonKills = FactionKillTracker.getFactionTotalKills(Faction.CRIMSONS_STRING_ID);

        // Header
        source.sendSuccess(new StringTextComponent(
                "========= WAR SCOREBOARD =========")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);

        // Determine who's winning
        String winningId = FactionKillTracker.getWinningFaction();
        String status;
        if (winningId == null) {
            status = "The war is perfectly balanced...";
        } else if (Faction.MYSTICS_STRING_ID.equals(winningId)) {
            status = "The Mystics hold the advantage!";
        } else {
            status = "The Crimsons dominate the battlefield!";
        }
        source.sendSuccess(new StringTextComponent(status)
                .withStyle(TextFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(new StringTextComponent(""), false);

        // Mystics section
        source.sendSuccess(new StringTextComponent(
                String.format("  The Mystics — %d kills (%d members)",
                        mysticKills, mystics != null ? mystics.getMemberCount() : 0))
                .withStyle(TextFormatting.BLUE, TextFormatting.BOLD), false);

        List<Map.Entry<UUID, Integer>> mysticTop = FactionKillTracker.getTopKillers(Faction.MYSTICS_STRING_ID, 5);
        if (mysticTop.isEmpty()) {
            source.sendSuccess(new StringTextComponent("    No kills yet")
                    .withStyle(TextFormatting.GRAY), false);
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : mysticTop) {
                String name = mystics != null ? mystics.getMemberName(entry.getKey()) : "Unknown";
                source.sendSuccess(new StringTextComponent(
                        String.format("    %d. %s — %d kills", rank++, name, entry.getValue()))
                        .withStyle(TextFormatting.BLUE), false);
            }
        }

        source.sendSuccess(new StringTextComponent(""), false);

        // Crimsons section
        source.sendSuccess(new StringTextComponent(
                String.format("  The Crimsons — %d kills (%d members)",
                        crimsonKills, crimsons != null ? crimsons.getMemberCount() : 0))
                .withStyle(TextFormatting.RED, TextFormatting.BOLD), false);

        List<Map.Entry<UUID, Integer>> crimsonTop = FactionKillTracker.getTopKillers(Faction.CRIMSONS_STRING_ID, 5);
        if (crimsonTop.isEmpty()) {
            source.sendSuccess(new StringTextComponent("    No kills yet")
                    .withStyle(TextFormatting.GRAY), false);
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : crimsonTop) {
                String name = crimsons != null ? crimsons.getMemberName(entry.getKey()) : "Unknown";
                source.sendSuccess(new StringTextComponent(
                        String.format("    %d. %s — %d kills", rank++, name, entry.getValue()))
                        .withStyle(TextFormatting.RED), false);
            }
        }

        source.sendSuccess(new StringTextComponent(
                "==================================")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);

        return 1;
    }

    // --- /thaumicwards stats — Personal stats ---

    private static int showStats(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();

            context.getSource().sendSuccess(new StringTextComponent(
                    "=== Your Arcane Stats ===")
                    .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);

            // Faction info
            Faction faction = FactionManager.getPlayerFaction(player.getUUID());
            if (faction == null) {
                context.getSource().sendSuccess(new StringTextComponent(
                        "Faction: None (use /thaumicwards join)")
                        .withStyle(TextFormatting.GRAY), false);
            } else {
                FactionRank rank = faction.getRank(player.getUUID());
                context.getSource().sendSuccess(new StringTextComponent("Faction: ")
                        .withStyle(TextFormatting.GRAY)
                        .append(new StringTextComponent(faction.getName())
                                .withStyle(faction.getFactionColor(), TextFormatting.BOLD)), false);
                context.getSource().sendSuccess(new StringTextComponent(
                        "Rank: " + (rank != null ? rank.getDisplayName() : "Unknown"))
                        .withStyle(rank != null ? rank.getColor() : TextFormatting.GRAY), false);
            }

            // Progression
            PlayerProgressionData progression = ProgressionManager.getOrCreate(player.getUUID());
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("Arcane Power: %d", progression.getArcanePower()))
                    .withStyle(TextFormatting.AQUA), false);

            // Progress bar to next rank
            if (progression.getPointsToNextRank() > 0) {
                int percent = progression.getProgressPercent();
                FactionRank earnedRank = progression.getEarnedRank();
                FactionRank nextRank = earnedRank.nextAutoRank();
                String bar = buildProgressBar(percent);

                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("To %s: %s %d%%", nextRank.getDisplayName(), bar, percent))
                        .withStyle(TextFormatting.GREEN), false);
            } else {
                Faction pFaction = FactionManager.getPlayerFaction(player.getUUID());
                FactionRank currentRank = pFaction != null ? pFaction.getRank(player.getUUID()) : null;
                if (currentRank == FactionRank.WARLOCK) {
                    context.getSource().sendSuccess(new StringTextComponent(
                            "Max auto-rank reached. Seek a Leader's promotion to Archmage.")
                            .withStyle(TextFormatting.GOLD), false);
                } else if (currentRank == FactionRank.ARCHMAGE || currentRank == FactionRank.LEADER) {
                    context.getSource().sendSuccess(new StringTextComponent(
                            "You have reached the pinnacle of arcane mastery.")
                            .withStyle(TextFormatting.GOLD), false);
                }
            }

            // Playtime
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("Playtime: %dh %dm",
                            progression.getPlaytimeMinutes() / 60,
                            progression.getPlaytimeMinutes() % 60))
                    .withStyle(TextFormatting.GRAY), false);

            // Combat stats
            int kills = FactionKillTracker.getPlayerKills(player.getUUID());
            int deaths = FactionKillTracker.getPlayerDeaths(player.getUUID());
            double kd = deaths > 0 ? (double) kills / deaths : kills;
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("Faction Kills: %d | Deaths: %d | K/D: %.2f", kills, deaths, kd))
                    .withStyle(TextFormatting.RED), false);

            // Claims
            int personalClaims = ClaimManager.getPlayerPersonalClaims(player.getUUID()).size();
            int maxClaims = ClaimManager.getMaxPersonalClaims(player.getUUID());
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("Claims: %d/%d", personalClaims, maxClaims))
                    .withStyle(TextFormatting.GRAY), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static String buildProgressBar(int percent) {
        int filled = percent / 5; // 20 chars total
        int empty = 20 - filled;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < filled; i++) bar.append("|");
        for (int i = 0; i < empty; i++) bar.append(".");
        bar.append("]");
        return bar.toString();
    }
}

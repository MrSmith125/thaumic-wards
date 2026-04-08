package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.factions.*;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.*;

public class FactionCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                // Top-level join command
                .then(Commands.literal("join")
                    .executes(FactionCommand::joinFaction))
                // Faction subcommands
                .then(Commands.literal("faction")
                    .then(Commands.literal("leave")
                        .executes(FactionCommand::leaveFaction))
                    .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FactionCommand::kickMember)))
                    .then(Commands.literal("promote")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FactionCommand::promoteMember)))
                    .then(Commands.literal("demote")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FactionCommand::demoteMember)))
                    .then(Commands.literal("setleader")
                        .requires(source -> source.hasPermission(2)) // OP only
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FactionCommand::setLeader)))
                    .then(Commands.literal("removeleader")
                        .requires(source -> source.hasPermission(2)) // OP only
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FactionCommand::removeLeader)))
                    .then(Commands.literal("info")
                        .executes(FactionCommand::factionInfo))
                    .then(Commands.literal("list")
                        .executes(FactionCommand::listFactions))
                    .then(Commands.literal("claim")
                        .executes(FactionCommand::claimGuild))
                    .then(Commands.literal("unclaim")
                        .executes(FactionCommand::unclaimGuild))
                )
        );
    }

    // --- Join (auto-assign to smallest faction) ---

    private static int joinFaction(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();

            if (FactionManager.isPlayerInFaction(player.getUUID())) {
                Faction current = FactionManager.getPlayerFaction(player.getUUID());
                context.getSource().sendFailure(new StringTextComponent(
                        "You already belong to " + current.getName() + ". Leave first with /thaumicwards faction leave."));
                return 0;
            }

            Faction faction = FactionManager.joinFaction(player.getUUID(), player.getName().getString());
            if (faction == null) {
                context.getSource().sendFailure(new StringTextComponent(
                        "Could not assign you to a faction. Please try again."));
                return 0;
            }

            // Welcome message with faction color
            context.getSource().sendSuccess(new StringTextComponent(
                    "The arcane forces have chosen you for ")
                    .withStyle(TextFormatting.LIGHT_PURPLE)
                    .append(new StringTextComponent(faction.getName())
                        .withStyle(faction.getFactionColor(), TextFormatting.BOLD))
                    .append(new StringTextComponent("!")
                        .withStyle(TextFormatting.LIGHT_PURPLE)), true);

            context.getSource().sendSuccess(new StringTextComponent(
                    "You join as an Initiate. Earn Arcane Power through playtime and combat to rank up!")
                    .withStyle(TextFormatting.GRAY), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    // --- Leave ---

    private static int leaveFaction(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();

            if (!FactionManager.isPlayerInFaction(player.getUUID())) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a faction."));
                return 0;
            }

            Faction faction = FactionManager.getPlayerFaction(player.getUUID());
            String factionName = faction != null ? faction.getName() : "Unknown";

            if (FactionManager.leaveFaction(player.getUUID())) {
                context.getSource().sendSuccess(new StringTextComponent(
                        "You have departed from " + factionName + ". Your arcane bonds dissolve...")
                        .withStyle(TextFormatting.YELLOW), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent("Could not leave the faction."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    // --- Kick (Leader only) ---

    private static int kickMember(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a faction."));
                return 0;
            }

            if (FactionManager.kickMember(factionId, player.getUUID(), target.getUUID())) {
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been expelled from the faction.", target.getName().getString()))
                        .withStyle(TextFormatting.YELLOW), true);
                target.displayClientMessage(new StringTextComponent(
                        "You have been expelled from the faction by a Leader.").withStyle(TextFormatting.RED), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Cannot kick this player. Only Leaders can kick, and Leaders cannot kick other Leaders."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    // --- Promote (Leader promotes Warlock -> Archmage) ---

    private static int promoteMember(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a faction."));
                return 0;
            }

            if (FactionManager.promoteMember(factionId, player.getUUID(), target.getUUID())) {
                Faction faction = FactionManager.getFaction(factionId);
                FactionRank newRank = faction.getRank(target.getUUID());
                String rankName = newRank != null ? newRank.getDisplayName() : "Unknown";

                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been promoted to %s!", target.getName().getString(), rankName))
                        .withStyle(TextFormatting.GREEN), true);
                target.displayClientMessage(new StringTextComponent(
                        String.format("A Leader has elevated you to %s! Your arcane mastery is recognized.", rankName))
                        .withStyle(TextFormatting.GREEN, TextFormatting.BOLD), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Cannot promote this player. Only Leaders can promote, and only Warlocks can be promoted to Archmage. " +
                        "Lower ranks are earned automatically through Arcane Power."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    // --- Demote (Leader demotes Archmage -> Warlock) ---

    private static int demoteMember(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a faction."));
                return 0;
            }

            if (FactionManager.demoteMember(factionId, player.getUUID(), target.getUUID())) {
                Faction faction = FactionManager.getFaction(factionId);
                FactionRank newRank = faction.getRank(target.getUUID());
                String rankName = newRank != null ? newRank.getDisplayName() : "Unknown";

                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been demoted to %s.", target.getName().getString(), rankName))
                        .withStyle(TextFormatting.YELLOW), true);
                target.displayClientMessage(new StringTextComponent(
                        String.format("You have been demoted to %s.", rankName))
                        .withStyle(TextFormatting.RED), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Cannot demote this player. Only Leaders can demote, and only Archmages can be demoted."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    // --- Set Leader (OP only) ---

    private static int setLeader(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");

            if (!FactionManager.isPlayerInFaction(target.getUUID())) {
                context.getSource().sendFailure(new StringTextComponent(
                        target.getName().getString() + " is not in a faction."));
                return 0;
            }

            if (FactionManager.setLeader(target.getUUID())) {
                Faction faction = FactionManager.getPlayerFaction(target.getUUID());
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been appointed as a Leader of %s!",
                                target.getName().getString(), faction.getName()))
                        .withStyle(TextFormatting.GREEN), true);
                target.displayClientMessage(new StringTextComponent(
                        "You have been appointed as a Leader of your faction by a server administrator!")
                        .withStyle(TextFormatting.LIGHT_PURPLE, TextFormatting.BOLD), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent("Could not set leader."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    // --- Remove Leader (OP only) ---

    private static int removeLeader(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");

            if (FactionManager.removeLeader(target.getUUID())) {
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been removed as Leader.", target.getName().getString()))
                        .withStyle(TextFormatting.YELLOW), true);
                target.displayClientMessage(new StringTextComponent(
                        "Your Leader status has been revoked by a server administrator.")
                        .withStyle(TextFormatting.RED), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        target.getName().getString() + " is not a Leader of any faction."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    // --- Info ---

    private static int factionInfo(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            Faction faction = FactionManager.getPlayerFaction(player.getUUID());

            if (faction == null) {
                context.getSource().sendFailure(new StringTextComponent(
                        "You are not in a faction. Use /thaumicwards join to enlist."));
                return 0;
            }

            displayFactionInfo(context.getSource(), faction);

            // Show personal progression
            PlayerProgressionData progression = ProgressionManager.getData(player.getUUID());
            if (progression != null) {
                FactionRank currentRank = faction.getRank(player.getUUID());
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("Your Rank: %s | Arcane Power: %d",
                                currentRank != null ? currentRank.getDisplayName() : "Unknown",
                                progression.getArcanePower()))
                        .withStyle(TextFormatting.AQUA), false);

                if (progression.getPointsToNextRank() > 0) {
                    context.getSource().sendSuccess(new StringTextComponent(
                            String.format("Progress to next rank: %d%% (%d points needed)",
                                    progression.getProgressPercent(), progression.getPointsToNextRank()))
                            .withStyle(TextFormatting.GRAY), false);
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static void displayFactionInfo(CommandSource source, Faction faction) {
        source.sendSuccess(new StringTextComponent(
                "=== " + faction.getName() + " ===")
                .withStyle(faction.getFactionColor(), TextFormatting.BOLD), false);

        // Leaders
        List<String> leaderNames = faction.getLeaderNames();
        String leadersStr = leaderNames.isEmpty() ? "None appointed" : String.join(", ", leaderNames);
        source.sendSuccess(new StringTextComponent("Leaders: " + leadersStr)
                .withStyle(TextFormatting.LIGHT_PURPLE), false);

        source.sendSuccess(new StringTextComponent(
                String.format("Members: %d | Guild Territory: %d/%d chunks",
                        faction.getMemberCount(),
                        ClaimManager.getFactionClaims(faction.getFactionId()).size(),
                        faction.getMaxGuildClaims()))
                .withStyle(TextFormatting.GRAY), false);

        // Member list grouped by rank
        source.sendSuccess(new StringTextComponent("--- Members ---")
                .withStyle(TextFormatting.GRAY), false);

        // Sort by rank (highest first)
        List<Map.Entry<UUID, FactionRank>> sorted = new ArrayList<>(faction.getMembers().entrySet());
        sorted.sort((a, b) -> b.getValue().getLevel() - a.getValue().getLevel());

        for (Map.Entry<UUID, FactionRank> entry : sorted) {
            FactionRank rank = entry.getValue();
            String memberName = faction.getMemberName(entry.getKey());
            source.sendSuccess(new StringTextComponent(
                    String.format("  [%s] %s", rank.getDisplayName(), memberName))
                    .withStyle(rank.getColor()), false);
        }
    }

    // --- List (always shows exactly 2 factions) ---

    private static int listFactions(CommandContext<CommandSource> context) {
        Faction mystics = FactionManager.getMystics();
        Faction crimsons = FactionManager.getCrimsons();

        context.getSource().sendSuccess(new StringTextComponent(
                "=== The Eternal Rivalry ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);

        if (mystics != null) {
            List<String> mysticLeaders = mystics.getLeaderNames();
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("  %s — %d members", mystics.getName(), mystics.getMemberCount()))
                    .withStyle(TextFormatting.BLUE)
                    .append(new StringTextComponent(
                            mysticLeaders.isEmpty() ? "" : " (Leaders: " + String.join(", ", mysticLeaders) + ")")
                            .withStyle(TextFormatting.GRAY)), false);
        }

        if (crimsons != null) {
            List<String> crimsonLeaders = crimsons.getLeaderNames();
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("  %s — %d members", crimsons.getName(), crimsons.getMemberCount()))
                    .withStyle(TextFormatting.RED)
                    .append(new StringTextComponent(
                            crimsonLeaders.isEmpty() ? "" : " (Leaders: " + String.join(", ", crimsonLeaders) + ")")
                            .withStyle(TextFormatting.GRAY)), false);
        }

        context.getSource().sendSuccess(new StringTextComponent(
                "Use /thaumicwards join to choose your allegiance!")
                .withStyle(TextFormatting.LIGHT_PURPLE), false);

        return 1;
    }

    // --- Guild Claim (Leader only) ---

    private static int claimGuild(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());
            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a faction."));
                return 0;
            }

            Faction faction = FactionManager.getFaction(factionId);
            FactionRank rank = faction.getRank(player.getUUID());
            if (!rank.canExpandGuild()) {
                context.getSource().sendFailure(new StringTextComponent(
                        "Only Leaders can claim guild territory."));
                return 0;
            }

            int currentClaims = ClaimManager.getFactionClaims(factionId).size();
            if (currentClaims >= faction.getMaxGuildClaims()) {
                context.getSource().sendFailure(new StringTextComponent(
                        "The faction has reached its maximum territory. Recruit more members to expand."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            ClaimManager.ClaimResult result = ClaimManager.claimChunk(
                    chunkPos, player.getUUID(), faction.getName(),
                    ClaimData.ClaimType.GUILD, factionId);

            if (result == ClaimManager.ClaimResult.SUCCESS) {
                context.getSource().sendSuccess(new StringTextComponent(
                        "This territory has been claimed for " + faction.getName() + "!")
                        .withStyle(faction.getFactionColor()), false);
                return 1;
            } else if (result == ClaimManager.ClaimResult.ALREADY_CLAIMED) {
                context.getSource().sendFailure(new StringTextComponent(
                        "This territory is already claimed."));
            }
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    // --- Guild Unclaim (Leader only) ---

    private static int unclaimGuild(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());
            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a faction."));
                return 0;
            }

            Faction faction = FactionManager.getFaction(factionId);
            FactionRank rank = faction.getRank(player.getUUID());
            if (!rank.canExpandGuild()) {
                context.getSource().sendFailure(new StringTextComponent(
                        "Only Leaders can unclaim guild territory."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            ClaimData claim = ClaimManager.getClaimAt(chunkPos);

            if (claim == null || !claim.isGuild() || !factionId.equals(claim.getFactionId())) {
                context.getSource().sendFailure(new StringTextComponent(
                        "This territory does not belong to your faction."));
                return 0;
            }

            ClaimManager.forceUnclaim(chunkPos);
            context.getSource().sendSuccess(new StringTextComponent(
                    "Faction territory released.").withStyle(TextFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }
}

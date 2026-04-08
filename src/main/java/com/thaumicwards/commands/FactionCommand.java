package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionManager;
import com.thaumicwards.factions.FactionRank;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class FactionCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards").then(
                Commands.literal("faction")
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(FactionCommand::createFaction)))
                    .then(Commands.literal("disband")
                        .executes(FactionCommand::disbandFaction))
                    .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FactionCommand::invitePlayer)))
                    .then(Commands.literal("accept")
                        .executes(FactionCommand::acceptInvite))
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
                    .then(Commands.literal("info")
                        .executes(FactionCommand::factionInfo)
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(FactionCommand::factionInfoNamed)))
                    .then(Commands.literal("list")
                        .executes(FactionCommand::listFactions))
                    .then(Commands.literal("claim")
                        .executes(FactionCommand::claimGuild))
                    .then(Commands.literal("unclaim")
                        .executes(FactionCommand::unclaimGuild))
            )
        );
    }

    private static int createFaction(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(context, "name");

            if (FactionManager.isPlayerInFaction(player.getUUID())) {
                context.getSource().sendFailure(new StringTextComponent(
                        "You must leave your current guild before creating a new one."));
                return 0;
            }

            Faction faction = FactionManager.createFaction(name, player.getUUID(), player.getName().getString());
            if (faction == null) {
                context.getSource().sendFailure(new StringTextComponent(
                        "Could not create guild. The name may already be taken or is too long."));
                return 0;
            }

            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("The %s guild has been established! May your magic grow strong.", name))
                    .withStyle(TextFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static int disbandFaction(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());
            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            Faction faction = FactionManager.getFaction(factionId);
            String factionName = faction != null ? faction.getName() : "Unknown";

            // Remove all guild claims
            ClaimManager.getFactionClaims(factionId).forEach(claim ->
                    ClaimManager.forceUnclaim(claim.getChunkPos()));

            if (FactionManager.disbandFaction(factionId, player.getUUID())) {
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("The %s guild has been dissolved. Its wards fade away...", factionName))
                        .withStyle(TextFormatting.YELLOW), true);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Only the Archon can dissolve the guild."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static int invitePlayer(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            if (FactionManager.invitePlayer(factionId, player.getUUID(), target.getUUID())) {
                Faction faction = FactionManager.getFaction(factionId);
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("Invitation sent to %s.", target.getName().getString()))
                        .withStyle(TextFormatting.GREEN), false);

                target.displayClientMessage(new StringTextComponent(
                        String.format("You have been invited to join the %s guild! Use /thaumicwards faction accept to join.",
                                faction != null ? faction.getName() : "Unknown"))
                        .withStyle(TextFormatting.LIGHT_PURPLE), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Could not invite player. They may already be in a guild, or you lack the rank (Adept+)."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    private static int acceptInvite(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();

            if (!FactionManager.hasPendingInvite(player.getUUID())) {
                context.getSource().sendFailure(new StringTextComponent("You have no pending guild invitations."));
                return 0;
            }

            Faction faction = FactionManager.acceptInvite(player.getUUID(), player.getName().getString());
            if (faction != null) {
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("You have joined the %s guild as an Apprentice!", faction.getName()))
                        .withStyle(TextFormatting.GREEN), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "The invitation has expired or the guild is full."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static int leaveFaction(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();

            if (!FactionManager.isPlayerInFaction(player.getUUID())) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            if (FactionManager.leaveFaction(player.getUUID())) {
                context.getSource().sendSuccess(new StringTextComponent(
                        "You have departed from the guild.").withStyle(TextFormatting.YELLOW), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "The Archon cannot leave while other members remain. Disband or transfer leadership first."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static int kickMember(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            if (FactionManager.kickMember(factionId, player.getUUID(), target.getUUID())) {
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been expelled from the guild.", target.getName().getString()))
                        .withStyle(TextFormatting.YELLOW), true);
                target.displayClientMessage(new StringTextComponent(
                        "You have been expelled from the guild.").withStyle(TextFormatting.RED), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Cannot kick this player. You need Master+ rank and cannot kick equal or higher rank."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    private static int promoteMember(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            if (FactionManager.promoteMember(factionId, player.getUUID(), target.getUUID())) {
                Faction faction = FactionManager.getFaction(factionId);
                FactionRank newRank = faction.getRank(target.getUUID());
                String rankName = newRank != null ? newRank.getDisplayName() : "Unknown";

                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been promoted to %s.", target.getName().getString(), rankName))
                        .withStyle(TextFormatting.GREEN), true);
                target.displayClientMessage(new StringTextComponent(
                        String.format("You have been promoted to %s!", rankName))
                        .withStyle(TextFormatting.GREEN), false);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Cannot promote this player. Only the Archon can promote, and Masters cannot be promoted further."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    private static int demoteMember(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());

            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            if (FactionManager.demoteMember(factionId, player.getUUID(), target.getUUID())) {
                Faction faction = FactionManager.getFaction(factionId);
                FactionRank newRank = faction.getRank(target.getUUID());
                String rankName = newRank != null ? newRank.getDisplayName() : "Unknown";

                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("%s has been demoted to %s.", target.getName().getString(), rankName))
                        .withStyle(TextFormatting.YELLOW), true);
                return 1;
            } else {
                context.getSource().sendFailure(new StringTextComponent(
                        "Cannot demote this player."));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Could not find the specified player."));
            return 0;
        }
    }

    private static int factionInfo(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            Faction faction = FactionManager.getPlayerFaction(player.getUUID());

            if (faction == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            displayFactionInfo(context.getSource(), faction);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }

    private static int factionInfoNamed(CommandContext<CommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        Faction faction = FactionManager.getFactionByName(name);

        if (faction == null) {
            context.getSource().sendFailure(new StringTextComponent(
                    String.format("No guild named '%s' exists.", name)));
            return 0;
        }

        displayFactionInfo(context.getSource(), faction);
        return 1;
    }

    private static void displayFactionInfo(CommandSource source, Faction faction) {
        source.sendSuccess(new StringTextComponent(
                "=== " + faction.getName() + " Guild ===")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
        source.sendSuccess(new StringTextComponent(
                String.format("Members: %d | Guild Claims: %d/%d",
                        faction.getMemberCount(),
                        ClaimManager.getFactionClaims(faction.getFactionId()).size(),
                        faction.getMaxGuildClaims()))
                .withStyle(TextFormatting.GRAY), false);

        source.sendSuccess(new StringTextComponent("Members:")
                .withStyle(TextFormatting.LIGHT_PURPLE), false);

        for (Map.Entry<UUID, FactionRank> entry : faction.getMembers().entrySet()) {
            FactionRank rank = entry.getValue();
            String memberName = faction.getMemberName(entry.getKey());
            source.sendSuccess(new StringTextComponent(
                    String.format("  %s - %s", memberName, rank.getDisplayName()))
                    .withStyle(rank.getColor()), false);
        }
    }

    private static int listFactions(CommandContext<CommandSource> context) {
        Collection<Faction> factions = FactionManager.getAllFactions();

        if (factions.isEmpty()) {
            context.getSource().sendSuccess(new StringTextComponent(
                    "No guilds have been established yet.").withStyle(TextFormatting.GRAY), false);
        } else {
            context.getSource().sendSuccess(new StringTextComponent(
                    "=== Active Guilds ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
            for (Faction faction : factions) {
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("  %s - %d members (Archon: %s)",
                                faction.getName(), faction.getMemberCount(),
                                faction.getMemberName(faction.getArchonId())))
                        .withStyle(TextFormatting.LIGHT_PURPLE), false);
            }
        }
        return 1;
    }

    private static int claimGuild(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());
            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            Faction faction = FactionManager.getFaction(factionId);
            FactionRank rank = faction.getRank(player.getUUID());
            if (!rank.canExpandGuild()) {
                context.getSource().sendFailure(new StringTextComponent(
                        "Only Masters and the Archon can claim guild territory."));
                return 0;
            }

            int currentClaims = ClaimManager.getFactionClaims(factionId).size();
            if (currentClaims >= faction.getMaxGuildClaims()) {
                context.getSource().sendFailure(new StringTextComponent(
                        "The guild has reached its maximum territory. Recruit more members to expand."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            ClaimManager.ClaimResult result = ClaimManager.claimChunk(
                    chunkPos, player.getUUID(), faction.getName(),
                    ClaimData.ClaimType.GUILD, factionId);

            if (result == ClaimManager.ClaimResult.SUCCESS) {
                context.getSource().sendSuccess(new StringTextComponent(
                        "This territory has been claimed for the " + faction.getName() + " guild!")
                        .withStyle(TextFormatting.GREEN), false);
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

    private static int unclaimGuild(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            UUID factionId = FactionManager.getPlayerFactionId(player.getUUID());
            if (factionId == null) {
                context.getSource().sendFailure(new StringTextComponent("You are not in a guild."));
                return 0;
            }

            Faction faction = FactionManager.getFaction(factionId);
            FactionRank rank = faction.getRank(player.getUUID());
            if (!rank.canExpandGuild()) {
                context.getSource().sendFailure(new StringTextComponent(
                        "Only Masters and the Archon can unclaim guild territory."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            ClaimData claim = ClaimManager.getClaimAt(chunkPos);

            if (claim == null || !claim.isGuild() || !factionId.equals(claim.getFactionId())) {
                context.getSource().sendFailure(new StringTextComponent(
                        "This territory does not belong to your guild."));
                return 0;
            }

            ClaimManager.forceUnclaim(chunkPos);
            context.getSource().sendSuccess(new StringTextComponent(
                    "Guild territory released.").withStyle(TextFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("This command can only be used by players."));
            return 0;
        }
    }
}

package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.factions.*;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.Collection;

public class WarCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("warscore")
                    .executes(WarCommand::showWarScore))
                .then(Commands.literal("contested")
                    .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                    .executes(WarCommand::addContestedZone)))))
                    .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(WarCommand::removeContestedZone))))
                    .then(Commands.literal("list")
                        .executes(WarCommand::listContestedZones)))
        );
    }

    private static int showWarScore(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();

        // Force recalculate
        FactionWarStatus.recalculate();

        int mysticScore = FactionWarStatus.getMysticScore();
        int crimsonScore = FactionWarStatus.getCrimsonScore();
        String winnerId = FactionWarStatus.getWinningFactionId();

        source.sendSuccess(new StringTextComponent(
                "========= WAR STATUS =========")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);

        // Mystics score
        source.sendSuccess(new StringTextComponent(
                String.format("  The Mystics: %d points", mysticScore))
                .withStyle(TextFormatting.BLUE, TextFormatting.BOLD), false);

        // Crimsons score
        source.sendSuccess(new StringTextComponent(
                String.format("  The Crimsons: %d points", crimsonScore))
                .withStyle(TextFormatting.RED, TextFormatting.BOLD), false);

        source.sendSuccess(new StringTextComponent(""), false);

        // Winner / buff status
        if (winnerId == null) {
            source.sendSuccess(new StringTextComponent(
                    "Status: The war is balanced — no faction buffs active.")
                    .withStyle(TextFormatting.GRAY), false);
        } else if (Faction.MYSTICS_STRING_ID.equals(winnerId)) {
            source.sendSuccess(new StringTextComponent(
                    "Status: The Mystics hold dominance! Buff: Speed I + XP bonus")
                    .withStyle(TextFormatting.BLUE, TextFormatting.BOLD), false);
        } else {
            source.sendSuccess(new StringTextComponent(
                    "Status: The Crimsons hold dominance! Buff: Speed I + XP bonus")
                    .withStyle(TextFormatting.RED, TextFormatting.BOLD), false);
        }

        // Kill breakdown
        int mysticKills = FactionKillTracker.getFactionTotalKills(Faction.MYSTICS_STRING_ID);
        int crimsonKills = FactionKillTracker.getFactionTotalKills(Faction.CRIMSONS_STRING_ID);
        source.sendSuccess(new StringTextComponent(
                String.format("Kill Totals: Mystics %d | Crimsons %d", mysticKills, crimsonKills))
                .withStyle(TextFormatting.GRAY), false);

        // Contested zones
        int zoneCount = ContestedZoneManager.getAllZones().size();
        if (zoneCount > 0) {
            source.sendSuccess(new StringTextComponent(
                    String.format("Active Contested Zones: %d (kills worth %dx points!)",
                            zoneCount, com.thaumicwards.config.ServerConfig.CONTESTED_ZONE_KILL_MULTIPLIER.get()))
                    .withStyle(TextFormatting.DARK_RED), false);
        }

        source.sendSuccess(new StringTextComponent(
                "==============================")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);

        return 1;
    }

    private static int addContestedZone(CommandContext<CommandSource> context) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");
        int radius = IntegerArgumentType.getInteger(context, "radius");

        if (ContestedZoneManager.addZone(x, z, radius)) {
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("Contested zone created at (%d, %d) with %d chunk radius!", x, z, radius))
                    .withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD), true);
            return 1;
        } else {
            context.getSource().sendFailure(new StringTextComponent(
                    "A contested zone already exists at those coordinates."));
            return 0;
        }
    }

    private static int removeContestedZone(CommandContext<CommandSource> context) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");

        if (ContestedZoneManager.removeZone(x, z)) {
            context.getSource().sendSuccess(new StringTextComponent(
                    String.format("Contested zone at (%d, %d) removed.", x, z))
                    .withStyle(TextFormatting.YELLOW), true);
            return 1;
        } else {
            context.getSource().sendFailure(new StringTextComponent(
                    "No contested zone found at those coordinates."));
            return 0;
        }
    }

    private static int listContestedZones(CommandContext<CommandSource> context) {
        Collection<ContestedZoneManager.ContestedZone> zones = ContestedZoneManager.getAllZones();

        if (zones.isEmpty()) {
            context.getSource().sendSuccess(new StringTextComponent(
                    "No contested zones defined.").withStyle(TextFormatting.GRAY), false);
        } else {
            context.getSource().sendSuccess(new StringTextComponent(
                    "=== Contested Zones ===").withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD), false);
            for (ContestedZoneManager.ContestedZone zone : zones) {
                context.getSource().sendSuccess(new StringTextComponent(
                        String.format("  Center: (%d, %d) | Radius: %d chunks",
                                zone.centerX, zone.centerZ, zone.radiusChunks))
                        .withStyle(TextFormatting.RED), false);
            }
        }
        return 1;
    }
}

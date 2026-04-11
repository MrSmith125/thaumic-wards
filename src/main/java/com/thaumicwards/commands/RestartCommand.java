package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.performance.RestartScheduler;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

public class RestartCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("restart")
                    .requires(source -> source.hasPermission(3))
                    .then(Commands.literal("status").executes(RestartCommand::showStatus))
                    .then(Commands.literal("now")
                        .executes(ctx -> triggerRestart(ctx, 60))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 900))
                            .executes(ctx -> triggerRestart(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))
                        )
                    )
                    .then(Commands.literal("cancel").executes(RestartCommand::cancelRestart))
                    .executes(RestartCommand::showStatus)
                )
        );
    }

    private static int showStatus(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        source.sendSuccess(
                new StringTextComponent("=== Auto-Restart Status ===")
                        .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
        if (RestartScheduler.isRestartPending()) {
            int seconds = RestartScheduler.getSecondsRemaining();
            source.sendSuccess(
                    new StringTextComponent("Restart PENDING: ")
                            .withStyle(TextFormatting.RED)
                            .append(new StringTextComponent(formatTime(seconds))
                                    .withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD)),
                    false);
        } else {
            source.sendSuccess(
                    new StringTextComponent("No restart pending.")
                            .withStyle(TextFormatting.GREEN), false);
        }
        source.sendSuccess(
                new StringTextComponent("Next scheduled: ")
                        .withStyle(TextFormatting.GRAY)
                        .append(new StringTextComponent(RestartScheduler.getNextRestartTime())
                                .withStyle(TextFormatting.YELLOW)),
                false);
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        int pct = (int) ((usedMB * 100) / maxMB);
        TextFormatting memColor = pct > 80 ? TextFormatting.RED :
                                  pct > 60 ? TextFormatting.YELLOW : TextFormatting.GREEN;
        source.sendSuccess(
                new StringTextComponent("Memory: ")
                        .withStyle(TextFormatting.GRAY)
                        .append(new StringTextComponent(usedMB + "MB / " + maxMB + "MB (" + pct + "%)")
                                .withStyle(memColor)),
                false);
        return 1;
    }

    private static int triggerRestart(CommandContext<CommandSource> context, int seconds) {
        CommandSource source = context.getSource();
        if (RestartScheduler.isRestartPending()) {
            source.sendFailure(new StringTextComponent("A restart is already pending! Use /thaumicwards restart cancel first."));
            return 0;
        }
        RestartScheduler.triggerRestart(seconds);
        source.sendSuccess(
                new StringTextComponent("Restart initiated: server will stop in " + formatTime(seconds) + ".")
                        .withStyle(TextFormatting.GOLD), true);
        return 1;
    }

    private static int cancelRestart(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        if (!RestartScheduler.isRestartPending()) {
            source.sendFailure(new StringTextComponent("No restart is currently pending."));
            return 0;
        }
        RestartScheduler.cancelRestart();
        for (ServerPlayerEntity player : source.getServer().getPlayerList().getPlayers()) {
            player.displayClientMessage(
                    new StringTextComponent("")
                            .append(new StringTextComponent("[Arcane Nexus] ").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD))
                            .append(new StringTextComponent("Restart cancelled. The ley lines hold steady.")
                                    .withStyle(TextFormatting.GREEN)),
                    false);
        }
        source.sendSuccess(
                new StringTextComponent("Restart cancelled successfully.")
                        .withStyle(TextFormatting.GREEN), true);
        return 1;
    }

    private static String formatTime(int totalSeconds) {
        if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            if (seconds == 0) return minutes + (minutes != 1 ? " minutes" : " minute");
            return minutes + "m " + seconds + "s";
        }
        return totalSeconds + (totalSeconds != 1 ? " seconds" : " second");
    }
}

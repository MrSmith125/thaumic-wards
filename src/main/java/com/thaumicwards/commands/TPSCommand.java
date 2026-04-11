package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.performance.TPSMonitor;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

public class TPSCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("tps")
                    .requires(source -> source.hasPermission(2)) // OP only
                    .executes(TPSCommand::showTPS))
        );
    }

    private static int showTPS(CommandContext<CommandSource> context) {
        double tps = TPSMonitor.getCurrentTPS();
        double mspt = TPSMonitor.getAverageTickMs();

        Runtime runtime = Runtime.getRuntime();
        long maxMB = runtime.maxMemory() / 1024 / 1024;
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        int memPercent = maxMB > 0 ? (int) (usedMB * 100 / maxMB) : 0;

        // TPS color: green >= 18, yellow >= 15, red < 15
        TextFormatting tpsColor;
        if (tps >= 18.0) {
            tpsColor = TextFormatting.GREEN;
        } else if (tps >= 15.0) {
            tpsColor = TextFormatting.YELLOW;
        } else {
            tpsColor = TextFormatting.RED;
        }

        // Memory color: green < 70%, yellow < 85%, red >= 85%
        TextFormatting memColor;
        if (memPercent < 70) {
            memColor = TextFormatting.GREEN;
        } else if (memPercent < 85) {
            memColor = TextFormatting.YELLOW;
        } else {
            memColor = TextFormatting.RED;
        }

        context.getSource().sendSuccess(new StringTextComponent(
                "=== Server Performance ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
        context.getSource().sendSuccess(new StringTextComponent("TPS: ")
                .withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%.1f", tps)).withStyle(tpsColor))
                .append(new StringTextComponent(String.format(" (%.1fms/tick)", mspt)).withStyle(TextFormatting.GRAY)), false);
        context.getSource().sendSuccess(new StringTextComponent("Memory: ")
                .withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%dMB / %dMB (%d%%)", usedMB, maxMB, memPercent)).withStyle(memColor)), false);

        return 1;
    }
}

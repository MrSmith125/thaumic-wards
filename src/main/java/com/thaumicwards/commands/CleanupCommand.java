package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.performance.EntityCleanup;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

public class CleanupCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("cleanup")
                    .requires(source -> source.hasPermission(2))
                    .executes(CleanupCommand::runCleanup))
        );
    }

    private static int runCleanup(CommandContext<CommandSource> context) {
        EntityCleanup.CleanupResult result = EntityCleanup.runCleanup(context.getSource().getServer());
        context.getSource().sendSuccess(new StringTextComponent("=== Entity Cleanup ===")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
        context.getSource().sendSuccess(new StringTextComponent(
                String.format("Removed %d items, %d XP orbs. Scanned %d entities.",
                        result.removedItems, result.removedXpOrbs, result.totalScanned))
                .withStyle(TextFormatting.GREEN), false);
        return 1;
    }
}

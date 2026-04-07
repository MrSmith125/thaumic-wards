package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("help").executes(ModCommands::showHelp))
                .executes(ModCommands::showHelp)
        );
        PregenCommand.register(dispatcher);
        BorderCommand.register(dispatcher);
        // ClaimCommand.register(dispatcher);
        // FactionCommand.register(dispatcher);
    }

    private static int showHelp(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        source.sendSuccess(new StringTextComponent("=== Thaumic Wards ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
        source.sendSuccess(new StringTextComponent("Magical protection and faction system").withStyle(TextFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(new StringTextComponent(""), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards pregen <radius>").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Pre-generate chunks").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards border set <radius>").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Set world border").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards claim").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Claim current chunk").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction <action>").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Faction management").withStyle(TextFormatting.GRAY)), false);
        return 1;
    }
}

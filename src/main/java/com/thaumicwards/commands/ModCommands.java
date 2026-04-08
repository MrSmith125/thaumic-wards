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
        ClaimCommand.register(dispatcher);
        FactionCommand.register(dispatcher);
    }

    private static int showHelp(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        source.sendSuccess(new StringTextComponent("=== Thaumic Wards ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
        source.sendSuccess(new StringTextComponent("Magical protection and faction system").withStyle(TextFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(new StringTextComponent(""), false);

        source.sendSuccess(new StringTextComponent("--- Server Admin (OP) ---").withStyle(TextFormatting.YELLOW), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards pregen <radius>").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Pre-generate chunks").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards pregen stop").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Stop pre-generation").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards border set <radius>").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Set magic barrier").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards border remove").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Remove barrier").withStyle(TextFormatting.GRAY)), false);

        source.sendSuccess(new StringTextComponent("--- Ward Spells ---").withStyle(TextFormatting.YELLOW), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards claim").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Ward current chunk").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards unclaim").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Release warded chunk").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards claims").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - List your territories").withStyle(TextFormatting.GRAY)), false);

        source.sendSuccess(new StringTextComponent("--- Guild System ---").withStyle(TextFormatting.YELLOW), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction create <name>").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Found a guild").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction invite/accept/leave").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Membership").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction promote/demote/kick").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Management").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction info/list").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Information").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction claim/unclaim").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Guild territory").withStyle(TextFormatting.GRAY)), false);
        return 1;
    }
}

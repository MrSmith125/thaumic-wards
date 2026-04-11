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
        BorderCommand.register(dispatcher);
        ClaimCommand.register(dispatcher);
        FactionCommand.register(dispatcher);
        ScoreCommand.register(dispatcher);
        WarCommand.register(dispatcher);
        TPSCommand.register(dispatcher);
        LagMapCommand.register(dispatcher);
    }

    private static int showHelp(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        source.sendSuccess(new StringTextComponent("=== Thaumic Wards ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD), false);
        source.sendSuccess(new StringTextComponent("Mystics vs Crimsons — Choose your allegiance!").withStyle(TextFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(new StringTextComponent(""), false);

        source.sendSuccess(new StringTextComponent("--- Faction ---").withStyle(TextFormatting.YELLOW), false);
        source.sendSuccess(new StringTextComponent("Factions are auto-assigned on first login!").withStyle(TextFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction leave").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Leave your faction").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction info/list").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Faction information").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction promote/demote/kick").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Leader management").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction claim/unclaim").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Faction territory (Leader)").withStyle(TextFormatting.GRAY)), false);

        source.sendSuccess(new StringTextComponent("--- Rivalry ---").withStyle(TextFormatting.YELLOW), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards score").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - War scoreboard").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards stats").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Your personal stats").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards warscore").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - War status & buffs").withStyle(TextFormatting.GRAY)), false);

        source.sendSuccess(new StringTextComponent("--- Ward Spells ---").withStyle(TextFormatting.YELLOW), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards claim").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Ward current chunk (personal)").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards unclaim").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Release warded chunk").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards claims").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - List your territories").withStyle(TextFormatting.GRAY)), false);

        source.sendSuccess(new StringTextComponent("--- Server Admin (OP) ---").withStyle(TextFormatting.YELLOW), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards faction setleader/removeleader <player>").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Manage leaders").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards tps").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Server performance").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards lagmap [start|stop|entities|tiles|memory|network|players|dimensions|dump]").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Performance profiler").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards border set <radius> / border remove").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Magic barrier").withStyle(TextFormatting.GRAY)), false);
        source.sendSuccess(new StringTextComponent("/thaumicwards contested add/remove/list").withStyle(TextFormatting.GOLD).append(new StringTextComponent(" - Contested zones").withStyle(TextFormatting.GRAY)), false);
        return 1;
    }
}

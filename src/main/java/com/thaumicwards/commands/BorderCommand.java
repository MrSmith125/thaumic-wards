package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.border.WorldBorderManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;

public class BorderCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards").then(
                Commands.literal("border")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("set")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100000))
                            .executes(BorderCommand::setBorder)))
                    .then(Commands.literal("remove")
                        .executes(BorderCommand::removeBorder))
                    .then(Commands.literal("info")
                        .executes(BorderCommand::borderInfo))
            )
        );
    }

    private static int setBorder(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        ServerWorld world = source.getLevel();

        BlockPos center = new BlockPos(source.getPosition());

        WorldBorderManager.setBorder(world, center, radius);

        source.sendSuccess(new StringTextComponent(
                String.format("A magical barrier has been erected at %d blocks from [%d, %d]!",
                        radius, center.getX(), center.getZ()))
                .withStyle(TextFormatting.DARK_PURPLE), true);
        return 1;
    }

    private static int removeBorder(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        ServerWorld world = source.getLevel();

        if (!WorldBorderManager.hasBorder(world.dimension())) {
            source.sendFailure(new StringTextComponent("No magical barrier exists in this dimension."));
            return 0;
        }

        WorldBorderManager.removeBorder(world);
        source.sendSuccess(new StringTextComponent(
                "The magical barrier has been dispelled.").withStyle(TextFormatting.YELLOW), true);
        return 1;
    }

    private static int borderInfo(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        ServerWorld world = source.getLevel();

        WorldBorderManager.BorderData border = WorldBorderManager.getBorder(world.dimension());
        if (border == null) {
            source.sendSuccess(new StringTextComponent(
                    "No magical barrier in this dimension.").withStyle(TextFormatting.GRAY), false);
        } else {
            source.sendSuccess(new StringTextComponent(
                    String.format("Magical barrier: center [%d, %d], radius %d blocks",
                            border.center.getX(), border.center.getZ(), border.radius))
                    .withStyle(TextFormatting.LIGHT_PURPLE), false);
        }
        return 1;
    }
}

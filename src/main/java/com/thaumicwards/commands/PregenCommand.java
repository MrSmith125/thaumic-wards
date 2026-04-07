package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.pregen.PregenManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;

import java.util.UUID;

public class PregenCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards").then(
                Commands.literal("pregen")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(PregenCommand::startPregen))
                    .then(Commands.literal("stop")
                        .executes(PregenCommand::stopPregen))
            )
        );
    }

    private static int startPregen(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int maxRadius = ServerConfig.MAX_PREGEN_RADIUS.get();

        if (radius > maxRadius) {
            source.sendSuccess(new StringTextComponent(
                    "Radius capped to maximum: " + maxRadius).withStyle(TextFormatting.YELLOW), false);
            radius = maxRadius;
        }

        if (PregenManager.isRunning()) {
            source.sendFailure(new StringTextComponent(
                    "An arcane survey is already in progress! Use /thaumicwards pregen stop first."));
            return 0;
        }

        ServerWorld world = source.getLevel();
        BlockPos pos = new BlockPos(source.getPosition());
        ChunkPos center = new ChunkPos(pos);

        UUID initiator = null;
        try {
            ServerPlayerEntity player = source.getPlayerOrException();
            initiator = player.getUUID();
        } catch (Exception e) {
            // Console command - no player UUID
        }

        int totalChunks = (2 * radius + 1) * (2 * radius + 1);
        boolean started = PregenManager.startPregen(world, center, radius, initiator);

        if (started) {
            source.sendSuccess(new StringTextComponent(
                    String.format("Arcane surveying begins... generating %d chunks (radius %d) centered at [%d, %d]",
                            totalChunks, radius, center.x, center.z))
                    .withStyle(TextFormatting.LIGHT_PURPLE), true);
        } else {
            source.sendFailure(new StringTextComponent("Failed to start pre-generation."));
        }

        return started ? 1 : 0;
    }

    private static int stopPregen(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();

        if (!PregenManager.isRunning()) {
            source.sendFailure(new StringTextComponent("No arcane survey is currently in progress."));
            return 0;
        }

        PregenManager.stopPregen();
        source.sendSuccess(new StringTextComponent(
                "Arcane surveying halted.").withStyle(TextFormatting.YELLOW), true);
        return 1;
    }
}

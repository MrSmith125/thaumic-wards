package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.core.ThaumicWards;
import com.thaumicwards.performance.TPSMonitor;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class StressTestCommand {

    private static final CopyOnWriteArrayList<UUID> spawnedEntityIds = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<BlockPos> placedBlocks = new CopyOnWriteArrayList<>();

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("stresstest")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.literal("chunkgen")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                            .executes(StressTestCommand::runChunkGen)))
                    .then(Commands.literal("entities")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                            .then(Commands.argument("type", StringArgumentType.string())
                                .executes(StressTestCommand::runEntityStress))))
                    .then(Commands.literal("tiles")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                            .executes(StressTestCommand::runTileStress)))
                    .then(Commands.literal("combat")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                            .executes(StressTestCommand::runCombatStress)))
                    .then(Commands.literal("memory")
                        .executes(StressTestCommand::runMemoryStress))
                    .then(Commands.literal("cleanup")
                        .executes(StressTestCommand::cleanup))
                    .then(Commands.literal("full")
                        .executes(StressTestCommand::runFullStress))
                )
        );
    }

    private static int runChunkGen(CommandContext<CommandSource> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        CommandSource src = context.getSource();
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            double preTps = TPSMonitor.getCurrentTPS();
            long preMem = getMemMB();

            src.sendSuccess(header("Chunk Gen Stress"), false);
            ChunkPos center = new ChunkPos(player.blockPosition());
            int total = (2 * radius + 1) * (2 * radius + 1);
            src.sendSuccess(msg(String.format("Generating %d chunks (radius %d)...", total, radius), TextFormatting.AQUA), false);

            long totalNs = 0;
            int count = 0;
            long worstNs = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long s = System.nanoTime();
                    world.getChunk(center.x + dx, center.z + dz);
                    long elapsed = System.nanoTime() - s;
                    totalNs += elapsed;
                    if (elapsed > worstNs) worstNs = elapsed;
                    count++;
                    if (count % 100 == 0) {
                        src.sendSuccess(msg(String.format("  ...%d/%d (%.1fms avg)", count, total,
                                (totalNs / 1e6) / count), TextFormatting.GRAY), false);
                    }
                }
            }

            double avgMs = count > 0 ? (totalNs / 1e6) / count : 0;
            src.sendSuccess(subHeader("Results"), false);
            src.sendSuccess(stat("Chunks", String.valueOf(count)), false);
            src.sendSuccess(stat("Avg/chunk", String.format("%.2fms", avgMs)), false);
            src.sendSuccess(stat("Worst", String.format("%.2fms", worstNs / 1e6)), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(preMem, getMemMB()), false);
        } catch (Exception e) {
            src.sendFailure(msg("Must be run by a player.", TextFormatting.RED));
        }
        return 1;
    }

    private static int runEntityStress(CommandContext<CommandSource> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        String typeStr = StringArgumentType.getString(context, "type");
        CommandSource src = context.getSource();
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            ResourceLocation rl = new ResourceLocation(typeStr.contains(":") ? typeStr : "minecraft:" + typeStr);
            EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(rl);
            if (entityType == null) { src.sendFailure(msg("Unknown type: " + typeStr, TextFormatting.RED)); return 0; }

            double preTps = TPSMonitor.getCurrentTPS();
            long preMem = getMemMB();
            src.sendSuccess(header("Entity Stress"), false);

            BlockPos center = player.blockPosition();
            int spawned = 0;
            Random rng = new Random();
            int spread = Math.max(16, (int) Math.sqrt(count) * 2);

            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double dist = spread * 0.3 + rng.nextDouble() * spread * 0.7;
                int x = center.getX() + (int)(Math.cos(angle) * dist);
                int z = center.getZ() + (int)(Math.sin(angle) * dist);
                int y = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                Entity e = entityType.create(world);
                if (e != null) {
                    e.moveTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360, 0);
                    if (e instanceof MobEntity) ((MobEntity) e).finalizeSpawn(world, world.getCurrentDifficultyAt(new BlockPos(x, y, z)), SpawnReason.COMMAND, null, null);
                    e.getPersistentData().putBoolean("tw_stress", true);
                    if (world.addFreshEntity(e)) { spawnedEntityIds.add(e.getUUID()); spawned++; }
                }
            }

            src.sendSuccess(msg(String.format("Spawned %d %s. Use /thaumicwards stresstest cleanup to remove.", spawned, rl), TextFormatting.YELLOW), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(preMem, getMemMB()), false);
        } catch (Exception e) {
            src.sendFailure(msg("Error: " + e.getMessage(), TextFormatting.RED));
        }
        return 1;
    }

    private static int runTileStress(CommandContext<CommandSource> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        CommandSource src = context.getSource();
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            double preTps = TPSMonitor.getCurrentTPS();
            long preMem = getMemMB();
            src.sendSuccess(header("Tile Entity Stress"), false);

            BlockPos base = player.blockPosition().above(10);
            int gridSize = (int) Math.ceil(Math.sqrt(count));
            int placed = 0;
            for (int i = 0; i < count; i++) {
                BlockPos pos = base.offset((i % gridSize) * 2, 0, (i / gridSize) * 2);
                BlockPos below = pos.below();
                world.setBlock(below, Blocks.STONE.defaultBlockState(), 2);
                placedBlocks.add(below);
                world.setBlock(pos, (i % 3 == 0 ? Blocks.CHEST : i % 3 == 1 ? Blocks.FURNACE : Blocks.HOPPER).defaultBlockState(), 2);
                placedBlocks.add(pos);
                placed++;
            }

            src.sendSuccess(msg(String.format("Placed %d tile entities. Use cleanup to remove.", placed), TextFormatting.YELLOW), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(preMem, getMemMB()), false);
        } catch (Exception e) {
            src.sendFailure(msg("Error: " + e.getMessage(), TextFormatting.RED));
        }
        return 1;
    }

    private static int runCombatStress(CommandContext<CommandSource> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        CommandSource src = context.getSource();
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            double preTps = TPSMonitor.getCurrentTPS();
            src.sendSuccess(header("Combat Stress"), false);

            EntityType<?>[] types = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH };
            BlockPos center = player.blockPosition();
            int spawned = 0;
            Random rng = new Random();

            for (int i = 0; i < count; i++) {
                EntityType<?> type = types[i % types.length];
                double angle = 2 * Math.PI * i / count;
                double dist = 8 + rng.nextDouble() * 16;
                int x = center.getX() + (int)(Math.cos(angle) * dist);
                int z = center.getZ() + (int)(Math.sin(angle) * dist);
                int y = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                Entity e = type.create(world);
                if (e != null) {
                    e.moveTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360, 0);
                    if (e instanceof MobEntity) {
                        ((MobEntity) e).finalizeSpawn(world, world.getCurrentDifficultyAt(new BlockPos(x, y, z)), SpawnReason.COMMAND, null, null);
                        ((MobEntity) e).setTarget(player);
                    }
                    e.getPersistentData().putBoolean("tw_stress", true);
                    if (world.addFreshEntity(e)) { spawnedEntityIds.add(e.getUUID()); spawned++; }
                }
            }

            src.sendSuccess(msg(String.format("Spawned %d hostile mobs targeting you!", spawned), TextFormatting.RED), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);
        } catch (Exception e) {
            src.sendFailure(msg("Error: " + e.getMessage(), TextFormatting.RED));
        }
        return 1;
    }

    private static int runMemoryStress(CommandContext<CommandSource> context) {
        CommandSource src = context.getSource();
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            src.sendSuccess(header("Memory Stress"), false);

            Runtime rt = Runtime.getRuntime();
            long preMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            ChunkPos center = new ChunkPos(player.blockPosition());

            int loaded = 0;
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
            for (int[] d : dirs) {
                for (int i = 1; i <= 16; i++) {
                    world.getChunk(center.x + d[0] * i, center.z + d[1] * i);
                    loaded++;
                }
            }

            long postLoad = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            long postGc = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long recovered = postLoad - postGc;
            double pct = (postLoad - preMem) > 0 ? recovered * 100.0 / (postLoad - preMem) : 0;

            src.sendSuccess(stat("Chunks loaded", String.valueOf(loaded)), false);
            src.sendSuccess(stat("Before", preMem + " MB"), false);
            src.sendSuccess(stat("After load", postLoad + " MB (+" + (postLoad - preMem) + ")"), false);
            src.sendSuccess(stat("After GC", postGc + " MB"), false);
            src.sendSuccess(stat("Recovered", recovered + " MB (" + String.format("%.0f%%", pct) + ")"), false);
        } catch (Exception e) {
            src.sendFailure(msg("Error: " + e.getMessage(), TextFormatting.RED));
        }
        return 1;
    }

    private static int runFullStress(CommandContext<CommandSource> context) {
        CommandSource src = context.getSource();
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            Runtime rt = Runtime.getRuntime();

            double startTps = TPSMonitor.getCurrentTPS();
            long startMem = getMemMB();

            src.sendSuccess(header("FULL STRESS TEST"), false);
            src.sendSuccess(msg("Running all stress tests sequentially...", TextFormatting.AQUA), false);
            src.sendSuccess(stat("Starting TPS", String.format("%.1f", startTps)), false);
            src.sendSuccess(stat("Starting Memory", startMem + " MB / " + (rt.maxMemory() / 1024 / 1024) + " MB"), false);
            src.sendSuccess(msg("", TextFormatting.WHITE), false);

            // === Phase 1: Chunk Generation ===
            src.sendSuccess(msg("[1/5] Chunk Generation - 200 chunks...", TextFormatting.GOLD), false);
            double preTps = TPSMonitor.getCurrentTPS();
            long preMem = getMemMB();
            ChunkPos center = new ChunkPos(player.blockPosition());
            long totalChunkNs = 0;
            int chunkCount = 0;
            long worstChunk = 0;
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    long s = System.nanoTime();
                    world.getChunk(center.x + dx, center.z + dz);
                    long e = System.nanoTime() - s;
                    totalChunkNs += e;
                    if (e > worstChunk) worstChunk = e;
                    chunkCount++;
                }
            }
            double chunkAvg = chunkCount > 0 ? (totalChunkNs / 1e6) / chunkCount : 0;
            src.sendSuccess(stat("  Chunks", String.format("%d generated, %.2fms avg, %.2fms worst", chunkCount, chunkAvg, worstChunk / 1e6)), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(preMem, getMemMB()), false);

            // === Phase 2: Entity Spawning ===
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("[2/5] Entity Stress - 300 mixed entities...", TextFormatting.GOLD), false);
            preTps = TPSMonitor.getCurrentTPS();
            preMem = getMemMB();
            EntityType<?>[] entityTypes = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SHEEP, EntityType.COW, EntityType.CHICKEN };
            BlockPos pCenter = player.blockPosition();
            int spawned = 0;
            Random rng = new Random(42);
            for (int i = 0; i < 300; i++) {
                EntityType<?> type = entityTypes[i % entityTypes.length];
                double angle = 2 * Math.PI * i / 300;
                double dist = 10 + rng.nextDouble() * 40;
                int x = pCenter.getX() + (int)(Math.cos(angle) * dist);
                int z = pCenter.getZ() + (int)(Math.sin(angle) * dist);
                int y = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                Entity ent = type.create(world);
                if (ent != null) {
                    ent.moveTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360, 0);
                    if (ent instanceof MobEntity) ((MobEntity) ent).finalizeSpawn(world, world.getCurrentDifficultyAt(new BlockPos(x, y, z)), SpawnReason.COMMAND, null, null);
                    ent.getPersistentData().putBoolean("tw_stress", true);
                    if (world.addFreshEntity(ent)) { spawnedEntityIds.add(ent.getUUID()); spawned++; }
                }
            }
            src.sendSuccess(stat("  Entities", String.format("%d spawned", spawned)), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(preMem, getMemMB()), false);

            // === Phase 3: Tile Entities ===
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("[3/5] Tile Entity Stress - 500 blocks...", TextFormatting.GOLD), false);
            preTps = TPSMonitor.getCurrentTPS();
            preMem = getMemMB();
            BlockPos tileBase = player.blockPosition().above(15);
            int gridSize = (int) Math.ceil(Math.sqrt(500));
            int placed = 0;
            for (int i = 0; i < 500; i++) {
                BlockPos pos = tileBase.offset((i % gridSize) * 2, 0, (i / gridSize) * 2);
                BlockPos below = pos.below();
                world.setBlock(below, Blocks.STONE.defaultBlockState(), 2);
                placedBlocks.add(below);
                world.setBlock(pos, (i % 3 == 0 ? Blocks.CHEST : i % 3 == 1 ? Blocks.FURNACE : Blocks.HOPPER).defaultBlockState(), 2);
                placedBlocks.add(pos);
                placed++;
            }
            src.sendSuccess(stat("  Tiles", String.format("%d placed", placed)), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(preMem, getMemMB()), false);

            // === Phase 4: Combat ===
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("[4/5] Combat Stress - 100 hostile mobs...", TextFormatting.GOLD), false);
            preTps = TPSMonitor.getCurrentTPS();
            EntityType<?>[] hostiles = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH };
            int combatSpawned = 0;
            for (int i = 0; i < 100; i++) {
                EntityType<?> type = hostiles[i % hostiles.length];
                double angle = 2 * Math.PI * i / 100;
                double dist = 8 + rng.nextDouble() * 20;
                int x = pCenter.getX() + (int)(Math.cos(angle) * dist);
                int z = pCenter.getZ() + (int)(Math.sin(angle) * dist);
                int y = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                Entity ent = type.create(world);
                if (ent != null) {
                    ent.moveTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360, 0);
                    if (ent instanceof MobEntity) {
                        ((MobEntity) ent).finalizeSpawn(world, world.getCurrentDifficultyAt(new BlockPos(x, y, z)), SpawnReason.COMMAND, null, null);
                        ((MobEntity) ent).setTarget(player);
                    }
                    ent.getPersistentData().putBoolean("tw_stress", true);
                    if (world.addFreshEntity(ent)) { spawnedEntityIds.add(ent.getUUID()); combatSpawned++; }
                }
            }
            src.sendSuccess(stat("  Hostiles", String.format("%d spawned and targeting you", combatSpawned)), false);
            src.sendSuccess(tpsReport(preTps, TPSMonitor.getCurrentTPS()), false);

            // === Phase 5: Memory + GC ===
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("[5/5] Memory Stress - 128 chunks in 8 directions...", TextFormatting.GOLD), false);
            long preLoadMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            int loaded = 0;
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
            for (int[] d : dirs) {
                for (int i = 1; i <= 16; i++) {
                    world.getChunk(center.x + d[0] * i, center.z + d[1] * i);
                    loaded++;
                }
            }
            long postLoadMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            long postGcMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long recovered = postLoadMem - postGcMem;
            double gcPct = (postLoadMem - preLoadMem) > 0 ? recovered * 100.0 / (postLoadMem - preLoadMem) : 0;
            src.sendSuccess(stat("  Chunks loaded", String.valueOf(loaded)), false);
            src.sendSuccess(stat("  Memory", String.format("%dMB -> %dMB -> %dMB after GC (%.0f%% recovered)", preLoadMem, postLoadMem, postGcMem, gcPct)), false);

            // === Final Summary ===
            double endTps = TPSMonitor.getCurrentTPS();
            long endMem = getMemMB();
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(header("FULL TEST SUMMARY"), false);
            src.sendSuccess(stat("Total entities spawned", String.valueOf(spawned + combatSpawned)), false);
            src.sendSuccess(stat("Total tiles placed", String.valueOf(placed)), false);
            src.sendSuccess(stat("Total chunks generated", String.valueOf(chunkCount + loaded)), false);
            src.sendSuccess(stat("Chunk gen avg", String.format("%.2fms", chunkAvg)), false);

            TextFormatting tpsColor = endTps >= 18 ? TextFormatting.GREEN : endTps >= 15 ? TextFormatting.YELLOW : TextFormatting.RED;
            src.sendSuccess(new StringTextComponent("  Overall TPS: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.format("%.1f -> %.1f", startTps, endTps)).withStyle(tpsColor)), false);

            TextFormatting memColor = (endMem - startMem) < 500 ? TextFormatting.GREEN : (endMem - startMem) < 1500 ? TextFormatting.YELLOW : TextFormatting.RED;
            src.sendSuccess(new StringTextComponent("  Overall Memory: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.format("%dMB -> %dMB (%+dMB)", startMem, endMem, endMem - startMem)).withStyle(memColor)), false);

            TextFormatting gcColor = gcPct > 60 ? TextFormatting.GREEN : gcPct > 30 ? TextFormatting.YELLOW : TextFormatting.RED;
            src.sendSuccess(new StringTextComponent("  GC Efficiency: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.format("%.0f%%", gcPct)).withStyle(gcColor)), false);

            String verdict;
            TextFormatting verdictColor;
            if (endTps >= 18 && (endMem - startMem) < 1000) {
                verdict = "EXCELLENT - Server can handle this load easily";
                verdictColor = TextFormatting.GREEN;
            } else if (endTps >= 15) {
                verdict = "GOOD - Server handles load but monitor at higher player counts";
                verdictColor = TextFormatting.YELLOW;
            } else if (endTps >= 10) {
                verdict = "WARNING - Server is struggling, reduce entity/tile entity limits";
                verdictColor = TextFormatting.RED;
            } else {
                verdict = "CRITICAL - Server cannot handle this load, immediate optimization needed";
                verdictColor = TextFormatting.DARK_RED;
            }
            src.sendSuccess(new StringTextComponent("  Verdict: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(verdict).withStyle(verdictColor, TextFormatting.BOLD)), false);

            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("Use /thaumicwards stresstest cleanup to remove all test entities and blocks.", TextFormatting.GRAY), false);
            src.sendSuccess(msg("Use /thaumicwards lagmap for detailed profiler data.", TextFormatting.GRAY), false);

            ThaumicWards.LOGGER.info("Full stress test completed: TPS {}->{}, Memory {}->{}MB, GC {}%",
                    String.format("%.1f", startTps), String.format("%.1f", endTps), startMem, endMem, String.format("%.0f", gcPct));

        } catch (Exception e) {
            src.sendFailure(msg("Must be run by a player. Error: " + e.getMessage(), TextFormatting.RED));
        }
        return 1;
    }

    private static int cleanup(CommandContext<CommandSource> context) {
        CommandSource src = context.getSource();
        ServerWorld world = src.getLevel();
        int entities = 0, blocks = 0;

        for (UUID id : spawnedEntityIds) {
            Entity e = world.getEntity(id);
            if (e != null) { e.remove(); entities++; }
        }
        spawnedEntityIds.clear();

        List<BlockPos> rev = new ArrayList<>(placedBlocks);
        Collections.reverse(rev);
        for (BlockPos pos : rev) {
            if (!world.getBlockState(pos).isAir(world, pos)) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                blocks++;
            }
        }
        placedBlocks.clear();

        src.sendSuccess(msg(String.format("Cleaned up %d entities, %d blocks.", entities, blocks), TextFormatting.GREEN), false);
        return 1;
    }

    private static long getMemMB() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
    }

    private static IFormattableTextComponent header(String t) { return new StringTextComponent("=== " + t + " ===").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD); }
    private static IFormattableTextComponent subHeader(String t) { return new StringTextComponent("--- " + t + " ---").withStyle(TextFormatting.YELLOW); }
    private static IFormattableTextComponent msg(String t, TextFormatting c) { return new StringTextComponent(t).withStyle(c); }
    private static IFormattableTextComponent stat(String l, String v) { return new StringTextComponent("  " + l + ": ").withStyle(TextFormatting.GRAY).append(new StringTextComponent(v).withStyle(TextFormatting.WHITE)); }
    private static IFormattableTextComponent tpsReport(double before, double after) {
        double drop = before - after;
        TextFormatting c = drop < 2 ? TextFormatting.GREEN : drop < 5 ? TextFormatting.YELLOW : TextFormatting.RED;
        return new StringTextComponent("  TPS: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%.1f -> %.1f (%+.1f)", before, after, -drop)).withStyle(c));
    }
    private static IFormattableTextComponent memReport(long before, long after) {
        long d = after - before;
        TextFormatting c = d < 50 ? TextFormatting.GREEN : d < 200 ? TextFormatting.YELLOW : TextFormatting.RED;
        return new StringTextComponent("  Memory: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%dMB -> %dMB (%+dMB)", before, after, d)).withStyle(c));
    }
}

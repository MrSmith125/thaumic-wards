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
                    .then(Commands.literal("simulate")
                        .then(Commands.argument("players", IntegerArgumentType.integer(1, 100))
                            .executes(StressTestCommand::runSimulatePlayers)))
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

    private static int runSimulatePlayers(CommandContext<CommandSource> context) {
        int playerCount = IntegerArgumentType.getInteger(context, "players");
        CommandSource src = context.getSource();
        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            Runtime rt = Runtime.getRuntime();

            double startTps = TPSMonitor.getCurrentTPS();
            long startMem = getMemMB();

            src.sendSuccess(header("SIMULATE " + playerCount + " PLAYERS"), false);
            src.sendSuccess(msg("Creating " + playerCount + " player hotspots spread across the world...", TextFormatting.AQUA), false);
            src.sendSuccess(stat("Starting TPS", String.format("%.1f", startTps)), false);
            src.sendSuccess(stat("Starting Memory", startMem + " MB"), false);

            BlockPos origin = player.blockPosition();
            Random rng = new Random(12345); // deterministic seed for reproducibility
            int totalChunksLoaded = 0;
            int totalEntitiesSpawned = 0;
            int hotspotSpacing = 200; // 200 blocks between each simulated player

            // Phase 1: Create hotspots and load chunks around each
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("[1/3] Loading chunks at " + playerCount + " locations (simulating view-distance 5)...", TextFormatting.GOLD), false);

            List<BlockPos> hotspots = new ArrayList<>();
            int spotsPerRing = 8;
            int ring = 0;
            int spotInRing = 0;

            for (int i = 0; i < playerCount; i++) {
                // Spread players in concentric rings
                double angle = 2 * Math.PI * spotInRing / Math.max(1, Math.min(spotsPerRing * (ring + 1), playerCount));
                int dist = hotspotSpacing * (ring + 1);
                int hx = origin.getX() + (int)(Math.cos(angle) * dist) + rng.nextInt(100) - 50;
                int hz = origin.getZ() + (int)(Math.sin(angle) * dist) + rng.nextInt(100) - 50;
                int hy = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, hx, hz);
                BlockPos hotspot = new BlockPos(hx, hy, hz);
                hotspots.add(hotspot);

                spotInRing++;
                if (spotInRing >= spotsPerRing * (ring + 1)) {
                    spotInRing = 0;
                    ring++;
                }

                // Load chunks in a 5-chunk radius around this hotspot (simulates view-distance 5)
                ChunkPos cp = new ChunkPos(hotspot);
                int viewDist = 5;
                for (int dx = -viewDist; dx <= viewDist; dx++) {
                    for (int dz = -viewDist; dz <= viewDist; dz++) {
                        if (dx * dx + dz * dz <= viewDist * viewDist) {
                            world.getChunk(cp.x + dx, cp.z + dz);
                            totalChunksLoaded++;
                        }
                    }
                }

                // Progress every 10 hotspots
                if ((i + 1) % 10 == 0) {
                    src.sendSuccess(msg(String.format("  ...%d/%d hotspots created (%d chunks loaded)",
                            i + 1, playerCount, totalChunksLoaded), TextFormatting.GRAY), false);
                }
            }

            src.sendSuccess(stat("  Hotspots created", String.valueOf(hotspots.size())), false);
            src.sendSuccess(stat("  Chunks loaded", String.valueOf(totalChunksLoaded)), false);
            src.sendSuccess(tpsReport(startTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(startMem, getMemMB()), false);

            // Phase 2: Spawn mobs at each hotspot (simulating natural spawning)
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("[2/3] Spawning mobs at each hotspot (10-15 per location)...", TextFormatting.GOLD), false);
            double preMobTps = TPSMonitor.getCurrentTPS();
            long preMobMem = getMemMB();

            EntityType<?>[] naturalMobs = {
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER,
                EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN,
                EntityType.ENDERMAN, EntityType.WITCH
            };

            for (BlockPos hotspot : hotspots) {
                int mobsPerSpot = 10 + rng.nextInt(6); // 10-15 mobs per hotspot
                for (int m = 0; m < mobsPerSpot; m++) {
                    EntityType<?> type = naturalMobs[rng.nextInt(naturalMobs.length)];
                    int mx = hotspot.getX() + rng.nextInt(64) - 32;
                    int mz = hotspot.getZ() + rng.nextInt(64) - 32;
                    int my = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, mx, mz);
                    Entity ent = type.create(world);
                    if (ent != null) {
                        ent.moveTo(mx + 0.5, my, mz + 0.5, rng.nextFloat() * 360, 0);
                        if (ent instanceof MobEntity) {
                            ((MobEntity) ent).finalizeSpawn(world, world.getCurrentDifficultyAt(new BlockPos(mx, my, mz)),
                                    SpawnReason.NATURAL, null, null);
                        }
                        ent.getPersistentData().putBoolean("tw_stress", true);
                        if (world.addFreshEntity(ent)) {
                            spawnedEntityIds.add(ent.getUUID());
                            totalEntitiesSpawned++;
                        }
                    }
                }
            }

            src.sendSuccess(stat("  Entities spawned", String.valueOf(totalEntitiesSpawned)), false);
            src.sendSuccess(tpsReport(preMobTps, TPSMonitor.getCurrentTPS()), false);
            src.sendSuccess(memReport(preMobMem, getMemMB()), false);

            // Phase 3: Place some tile entities at random hotspots (simulating player bases)
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("[3/3] Placing tile entities at hotspots (simulating bases)...", TextFormatting.GOLD), false);
            double preTileTps = TPSMonitor.getCurrentTPS();
            int tilesPlaced = 0;

            // Place 5-10 tile entities at each hotspot
            for (BlockPos hotspot : hotspots) {
                int tilesPerSpot = 5 + rng.nextInt(6);
                for (int t = 0; t < tilesPerSpot; t++) {
                    int tx = hotspot.getX() + rng.nextInt(20) - 10;
                    int tz = hotspot.getZ() + rng.nextInt(20) - 10;
                    int ty = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tx, tz);
                    BlockPos pos = new BlockPos(tx, ty, tz);
                    BlockPos below = pos.below();

                    // Place support + tile entity
                    if (!world.getBlockState(below).isAir(world, below) || true) {
                        world.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
                        placedBlocks.add(pos);
                        BlockPos above = pos.above();
                        world.setBlock(above, (t % 3 == 0 ? Blocks.CHEST : t % 3 == 1 ? Blocks.FURNACE : Blocks.HOPPER).defaultBlockState(), 2);
                        placedBlocks.add(above);
                        tilesPlaced++;
                    }
                }
            }

            src.sendSuccess(stat("  Tile entities placed", String.valueOf(tilesPlaced)), false);
            src.sendSuccess(tpsReport(preTileTps, TPSMonitor.getCurrentTPS()), false);

            // === Final Summary ===
            double endTps = TPSMonitor.getCurrentTPS();
            long endMem = getMemMB();
            long maxMem = rt.maxMemory() / 1024 / 1024;

            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(header(playerCount + "-PLAYER SIMULATION RESULTS"), false);
            src.sendSuccess(stat("Simulated players", String.valueOf(playerCount)), false);
            src.sendSuccess(stat("Hotspot spacing", hotspotSpacing + " blocks apart"), false);
            src.sendSuccess(stat("Chunks loaded", String.valueOf(totalChunksLoaded)), false);
            src.sendSuccess(stat("Entities spawned", String.valueOf(totalEntitiesSpawned)), false);
            src.sendSuccess(stat("Tile entities placed", String.valueOf(tilesPlaced)), false);

            TextFormatting tpsColor = endTps >= 18 ? TextFormatting.GREEN : endTps >= 15 ? TextFormatting.YELLOW
                    : endTps >= 10 ? TextFormatting.RED : TextFormatting.DARK_RED;
            src.sendSuccess(new StringTextComponent("  TPS: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.format("%.1f -> %.1f", startTps, endTps)).withStyle(tpsColor)), false);

            int memPct = (int)(endMem * 100 / maxMem);
            TextFormatting memColor = memPct < 60 ? TextFormatting.GREEN : memPct < 80 ? TextFormatting.YELLOW : TextFormatting.RED;
            src.sendSuccess(new StringTextComponent("  Memory: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.format("%dMB / %dMB (%d%%)", endMem, maxMem, memPct)).withStyle(memColor)), false);

            // Estimated per-player cost
            double tpsDrop = startTps - endTps;
            double perPlayerTpsCost = playerCount > 0 ? tpsDrop / playerCount : 0;
            long memPerPlayer = playerCount > 0 ? (endMem - startMem) / playerCount : 0;
            src.sendSuccess(stat("Est. TPS cost/player", String.format("%.3f", perPlayerTpsCost)), false);
            src.sendSuccess(stat("Est. memory/player", memPerPlayer + " MB"), false);

            // Max player estimate
            double availableTps = 20.0 - 5.0; // reserve 5 TPS for base server overhead
            int maxPlayers = perPlayerTpsCost > 0 ? (int)(availableTps / perPlayerTpsCost) : 999;
            TextFormatting maxColor = maxPlayers >= 60 ? TextFormatting.GREEN : maxPlayers >= 40 ? TextFormatting.YELLOW : TextFormatting.RED;
            src.sendSuccess(new StringTextComponent("  Est. max players before TPS<15: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.valueOf(Math.min(maxPlayers, 200))).withStyle(maxColor, TextFormatting.BOLD)), false);

            // Verdict
            String verdict;
            TextFormatting verdictColor;
            if (endTps >= 18 && memPct < 70) {
                verdict = "EXCELLENT - Server handles " + playerCount + " simulated players easily";
                verdictColor = TextFormatting.GREEN;
            } else if (endTps >= 15) {
                verdict = "GOOD - Playable but approaching limits";
                verdictColor = TextFormatting.YELLOW;
            } else if (endTps >= 10) {
                verdict = "WARNING - TPS below 15, reduce view-distance or entity caps";
                verdictColor = TextFormatting.RED;
            } else {
                verdict = "CRITICAL - Server cannot handle " + playerCount + " players at current settings";
                verdictColor = TextFormatting.DARK_RED;
            }
            src.sendSuccess(new StringTextComponent("  Verdict: ").withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(verdict).withStyle(verdictColor, TextFormatting.BOLD)), false);

            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("Entities and tiles remain loaded to observe sustained TPS impact.", TextFormatting.GRAY), false);
            src.sendSuccess(msg("Run /thaumicwards stresstest cleanup when done observing.", TextFormatting.GRAY), false);
            src.sendSuccess(msg("Run /thaumicwards lagmap for detailed profiler data.", TextFormatting.GRAY), false);

            ThaumicWards.LOGGER.info("{}-player simulation: TPS {}->{}, Memory {}->{}MB, {} entities, {} tiles, {} chunks",
                    playerCount, String.format("%.1f", startTps), String.format("%.1f", endTps),
                    startMem, endMem, totalEntitiesSpawned, tilesPlaced, totalChunksLoaded);

        } catch (Exception e) {
            src.sendFailure(msg("Must be run by a player. Error: " + e.getMessage(), TextFormatting.RED));
            ThaumicWards.LOGGER.error("Simulate players failed", e);
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

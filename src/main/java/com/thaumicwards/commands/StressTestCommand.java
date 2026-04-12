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
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

    // === Active simulation state ===
    private static volatile boolean simulationActive = false;
    private static final List<SimulatedPlayer> simulatedPlayers = new ArrayList<>();
    private static int simTickCounter = 0;
    private static CommandSource simSource = null;
    private static long simStartTime = 0;
    private static double simStartTps = 0;
    private static long simStartMem = 0;

    /**
     * Call from ServerTickHandler every tick (overworld only).
     * Drives the active player simulation — moves bots, loads chunks, spawns mobs.
     */
    public static void tickSimulation(ServerWorld world) {
        if (!simulationActive) return;
        simTickCounter++;

        // Gradually activate pending bots — 1 new player every 10 seconds
        if (!pendingPlayers.isEmpty()) {
            rampUpCounter++;
            if (rampUpCounter >= TICKS_BETWEEN_NEW_PLAYER) {
                rampUpCounter = 0;
                SimulatedPlayer newBot = pendingPlayers.remove(0);
                newBot.active = true;
                simulatedPlayers.add(newBot);

                // Load initial chunks for this new bot
                ChunkPos cp = new ChunkPos(new BlockPos(newBot.x, 64, newBot.z));
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        if (dx * dx + dz * dz <= 25) {
                            world.getChunk(cp.x + dx, cp.z + dz);
                        }
                    }
                }

                int total = simulatedPlayers.size();
                int remaining = pendingPlayers.size();
                if (simSource != null) {
                    double tps = TPSMonitor.getCurrentTPS();
                    long mem = getMemMB();
                    TextFormatting tpsColor = tps >= 18 ? TextFormatting.GREEN : tps >= 15 ? TextFormatting.YELLOW : TextFormatting.RED;
                    simSource.sendSuccess(new StringTextComponent(String.format(
                            "[+1 Player] %d active, %d pending | TPS: %.1f | Mem: %dMB",
                            total, remaining, tps, mem)).withStyle(tpsColor), false);
                }

                ThaumicWards.LOGGER.info("Simulation: activated bot {} ({} pending), TPS: {}",
                        total, remaining, String.format("%.1f", TPSMonitor.getCurrentTPS()));
            }
        }

        if (simulatedPlayers.isEmpty()) return;

        // Move each simulated player every 20 ticks (1 second)
        if (simTickCounter % 20 == 0) {
            Random rng = new Random(simTickCounter);
            int chunksLoaded = 0;
            int mobsSpawned = 0;

            for (SimulatedPlayer sim : simulatedPlayers) {
                // Grinders stay in place, everyone else walks
                if (!sim.isGrinder) {
                    // Move in their current direction (8-16 blocks per second, like sprinting)
                    int moveSpeed = 12 + rng.nextInt(5);
                    sim.x += (int)(Math.cos(sim.angle) * moveSpeed);
                    sim.z += (int)(Math.sin(sim.angle) * moveSpeed);

                    // Occasionally change direction (simulates exploring, not walking straight)
                    if (rng.nextInt(5) == 0) {
                        sim.angle += (rng.nextDouble() - 0.5) * Math.PI;
                    }
                }

                // Load chunks around new position (simulates view-distance 5)
                ChunkPos cp = new ChunkPos(new BlockPos(sim.x, 64, sim.z));
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        if (dx * dx + dz * dz <= 25) {
                            world.getChunk(cp.x + dx, cp.z + dz);
                            chunksLoaded++;
                        }
                    }
                }

                sim.moveCount++;

                // Spawn mobs near this bot every 5 movements (simulates natural spawning)
                if (sim.moveCount % 5 == 0) {
                    EntityType<?>[] types = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SHEEP, EntityType.COW };
                    for (int m = 0; m < 3; m++) {
                        int mx = sim.x + rng.nextInt(32) - 16;
                        int mz = sim.z + rng.nextInt(32) - 16;
                        int my = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, mx, mz);
                        Entity ent = types[rng.nextInt(types.length)].create(world);
                        if (ent != null) {
                            ent.moveTo(mx + 0.5, my, mz + 0.5, rng.nextFloat() * 360, 0);
                            if (ent instanceof MobEntity) ((MobEntity) ent).finalizeSpawn(world,
                                    world.getCurrentDifficultyAt(new BlockPos(mx, my, mz)), SpawnReason.NATURAL, null, null);
                            ent.getPersistentData().putBoolean("tw_stress", true);
                            if (world.addFreshEntity(ent)) { spawnedEntityIds.add(ent.getUUID()); mobsSpawned++; }
                        }
                    }
                }

                // Drop items on the ground every 3 movements (simulates mining/crafting/breaking)
                if (sim.moveCount % 3 == 0) {
                    ItemStack[] drops = { new ItemStack(Items.COBBLESTONE, 4 + rng.nextInt(12)),
                            new ItemStack(Items.DIRT, 2 + rng.nextInt(8)),
                            new ItemStack(Items.OAK_LOG, 1 + rng.nextInt(4)),
                            new ItemStack(Items.IRON_ORE, 1 + rng.nextInt(3)),
                            new ItemStack(Items.ROTTEN_FLESH, 1 + rng.nextInt(5)) };
                    for (int d = 0; d < 2 + rng.nextInt(3); d++) {
                        int dx = sim.x + rng.nextInt(16) - 8;
                        int dz = sim.z + rng.nextInt(16) - 8;
                        int dy = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, dx, dz);
                        ItemEntity item = new ItemEntity(world, dx + 0.5, dy + 1, dz + 0.5,
                                drops[rng.nextInt(drops.length)].copy());
                        item.getPersistentData().putBoolean("tw_stress", true);
                        if (world.addFreshEntity(item)) spawnedEntityIds.add(item.getUUID());
                    }
                }

                // Mob grinder simulation for ~1 in 6 players: rapid mob kill + XP + loot drops
                if (sim.isGrinder && sim.moveCount % 2 == 0) {
                    int gx = sim.x;
                    int gz = sim.z;
                    int gy = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, gx, gz);

                    // Spawn and immediately kill mobs (simulates a grinder)
                    for (int k = 0; k < 3; k++) {
                        // Spawn loot drops as if mob died
                        ItemStack[] loot = { new ItemStack(Items.ROTTEN_FLESH, 1 + rng.nextInt(3)),
                                new ItemStack(Items.BONE, rng.nextInt(3)),
                                new ItemStack(Items.ARROW, rng.nextInt(4)),
                                new ItemStack(Items.GUNPOWDER, rng.nextInt(2)),
                                new ItemStack(Items.STRING, rng.nextInt(3)) };
                        for (int l = 0; l < 2; l++) {
                            ItemEntity lootItem = new ItemEntity(world,
                                    gx + rng.nextDouble() * 4 - 2, gy + 1, gz + rng.nextDouble() * 4 - 2,
                                    loot[rng.nextInt(loot.length)].copy());
                            lootItem.getPersistentData().putBoolean("tw_stress", true);
                            if (world.addFreshEntity(lootItem)) spawnedEntityIds.add(lootItem.getUUID());
                        }

                        // Spawn XP orbs
                        for (int xp = 0; xp < 2; xp++) {
                            ExperienceOrbEntity orb = new ExperienceOrbEntity(world,
                                    gx + rng.nextDouble() * 4 - 2, gy + 1, gz + rng.nextDouble() * 4 - 2,
                                    3 + rng.nextInt(7));
                            if (world.addFreshEntity(orb)) spawnedEntityIds.add(orb.getUUID());
                        }
                    }

                    // Spawn replacement mobs for the grinder to "kill" next cycle
                    EntityType<?>[] grinderMobs = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER };
                    for (int g = 0; g < 2; g++) {
                        Entity gMob = grinderMobs[rng.nextInt(grinderMobs.length)].create(world);
                        if (gMob != null) {
                            gMob.moveTo(gx + rng.nextDouble() * 6 - 3, gy + 1, gz + rng.nextDouble() * 6 - 3,
                                    rng.nextFloat() * 360, 0);
                            gMob.getPersistentData().putBoolean("tw_stress", true);
                            if (world.addFreshEntity(gMob)) { spawnedEntityIds.add(gMob.getUUID()); mobsSpawned++; }
                        }
                    }
                }
            }

            // Periodic status every 30 seconds
            if (simTickCounter % 600 == 0 && simSource != null) {
                double tps = TPSMonitor.getCurrentTPS();
                long mem = getMemMB();
                int seconds = simTickCounter / 20;
                simSource.sendSuccess(msg(String.format(
                        "[Sim %ds] TPS: %.1f | Memory: %dMB | Entities: %d | Chunks loaded this tick: %d",
                        seconds, tps, mem, spawnedEntityIds.size(), chunksLoaded),
                        tps >= 18 ? TextFormatting.GREEN : tps >= 15 ? TextFormatting.YELLOW : TextFormatting.RED), false);
            }
        }
    }

    // Pending bots waiting to be activated (gradual ramp-up)
    private static final List<SimulatedPlayer> pendingPlayers = new ArrayList<>();
    private static int rampUpCounter = 0;
    private static final int TICKS_BETWEEN_NEW_PLAYER = 200; // 10 seconds between each new bot

    private static class SimulatedPlayer {
        int x, z;
        double angle;
        int moveCount = 0;
        boolean isGrinder = false;
        boolean active = false; // only active bots move and load chunks

        SimulatedPlayer(int x, int z, double angle) {
            this.x = x; this.z = z; this.angle = angle;
        }
    }

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

        if (simulationActive) {
            src.sendFailure(msg("Simulation already running! Use /thaumicwards stresstest cleanup to stop.", TextFormatting.RED));
            return 0;
        }

        try {
            ServerPlayerEntity player = src.getPlayerOrException();
            ServerWorld world = player.getLevel();
            Runtime rt = Runtime.getRuntime();

            double startTps = TPSMonitor.getCurrentTPS();
            long startMem = getMemMB();

            src.sendSuccess(header("SIMULATE " + playerCount + " MOVING PLAYERS"), false);
            src.sendSuccess(msg("Each simulated player will:", TextFormatting.AQUA), false);
            src.sendSuccess(msg("  - Walk ~12 blocks/sec in random directions", TextFormatting.GRAY), false);
            src.sendSuccess(msg("  - Load chunks in a 5-chunk radius as they move", TextFormatting.GRAY), false);
            src.sendSuccess(msg("  - Spawn mobs every 5 seconds of movement", TextFormatting.GRAY), false);
            src.sendSuccess(msg("  - Report status every 30 seconds", TextFormatting.GRAY), false);
            src.sendSuccess(msg("Use /thaumicwards stresstest cleanup to stop.", TextFormatting.YELLOW), false);
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(stat("Starting TPS", String.format("%.1f", startTps)), false);
            src.sendSuccess(stat("Starting Memory", startMem + " MB"), false);

            BlockPos origin = player.blockPosition();
            Random rng = new Random(12345);
            int hotspotSpacing = 200;

            // Create all simulated players but put them in pending queue
            simulatedPlayers.clear();
            pendingPlayers.clear();
            rampUpCounter = 0;
            int spotsPerRing = 8;
            int ring = 0;
            int spotInRing = 0;

            for (int i = 0; i < playerCount; i++) {
                double angle = 2 * Math.PI * spotInRing / Math.max(1, Math.min(spotsPerRing * (ring + 1), playerCount));
                int dist = hotspotSpacing * (ring + 1);
                int hx = origin.getX() + (int)(Math.cos(angle) * dist) + rng.nextInt(100) - 50;
                int hz = origin.getZ() + (int)(Math.sin(angle) * dist) + rng.nextInt(100) - 50;
                double walkAngle = rng.nextDouble() * 2 * Math.PI;
                SimulatedPlayer sim = new SimulatedPlayer(hx, hz, walkAngle);
                if (i % 6 == 0) {
                    sim.isGrinder = true;
                    sim.angle = 0;
                }
                pendingPlayers.add(sim);

                spotInRing++;
                if (spotInRing >= spotsPerRing * (ring + 1)) { spotInRing = 0; ring++; }
            }

            // Activate the first bot immediately
            if (!pendingPlayers.isEmpty()) {
                SimulatedPlayer first = pendingPlayers.remove(0);
                first.active = true;
                simulatedPlayers.add(first);
                ChunkPos cp = new ChunkPos(new BlockPos(first.x, 64, first.z));
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        if (dx * dx + dz * dz <= 25) {
                            world.getChunk(cp.x + dx, cp.z + dz);
                        }
                    }
                }
            }

            // Start the tick-driven simulation with gradual ramp-up
            simulationActive = true;
            simTickCounter = 0;
            simSource = src;
            simStartTime = System.currentTimeMillis();
            simStartTps = startTps;
            simStartMem = startMem;

            int rampMinutes = (playerCount * TICKS_BETWEEN_NEW_PLAYER / 20) / 60;

            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(header("SIMULATION STARTING - GRADUAL RAMP-UP"), false);
            src.sendSuccess(stat("Target players", String.valueOf(playerCount)), false);
            src.sendSuccess(stat("Ramp-up rate", "1 new player every 10 seconds"), false);
            src.sendSuccess(stat("Time to full load", rampMinutes + " minutes"), false);
            src.sendSuccess(stat("Spacing", hotspotSpacing + " blocks apart"), false);
            src.sendSuccess(msg("", TextFormatting.WHITE), false);
            src.sendSuccess(msg("Players will join gradually, just like a real server filling up.", TextFormatting.GREEN), false);
            src.sendSuccess(msg("Watch the TPS impact as each player joins.", TextFormatting.GRAY), false);
            src.sendSuccess(msg("Run /thaumicwards stresstest cleanup to stop.", TextFormatting.YELLOW), false);

            ThaumicWards.LOGGER.info("Started {}-player gradual simulation (1 bot / 10 sec, ~{} min to full)",
                    playerCount, rampMinutes);

        } catch (Exception e) {
            src.sendFailure(msg("Must be run by a player. Error: " + e.getMessage(), TextFormatting.RED));
            ThaumicWards.LOGGER.error("Simulate players failed", e);
        }
        return 1;
    }

    private static int cleanup(CommandContext<CommandSource> context) {
        CommandSource src = context.getSource();
        ServerWorld world = src.getLevel();

        // Stop active simulation
        if (simulationActive) {
            long elapsed = (System.currentTimeMillis() - simStartTime) / 1000;
            double endTps = TPSMonitor.getCurrentTPS();
            long endMem = getMemMB();
            src.sendSuccess(header("SIMULATION ENDED"), false);
            src.sendSuccess(stat("Duration", elapsed + " seconds"), false);
            src.sendSuccess(stat("Simulated players", String.valueOf(simulatedPlayers.size())), false);
            int grinderCount = (int) simulatedPlayers.stream().filter(s -> s.isGrinder).count();
            src.sendSuccess(stat("Mob grinders", String.valueOf(grinderCount)), false);
            src.sendSuccess(tpsReport(simStartTps, endTps), false);
            src.sendSuccess(memReport(simStartMem, endMem), false);
            simulationActive = false;
            simulatedPlayers.clear();
            simSource = null;
        }

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

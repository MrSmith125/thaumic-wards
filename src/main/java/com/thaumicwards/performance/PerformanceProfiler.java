package com.thaumicwards.performance;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive server performance profiler for Thaumic Wards.
 * Tracks per-tick timing, entity/tile entity counts by type and dimension,
 * chunk load rates, memory trends, player count correlation, and network packet rates.
 * Periodically dumps stats to a log file and provides data for the /thaumicwards lagmap command.
 */
public class PerformanceProfiler {

    // ---- Singleton ----
    private static final PerformanceProfiler INSTANCE = new PerformanceProfiler();

    public static PerformanceProfiler getInstance() {
        return INSTANCE;
    }

    // ---- Configuration constants ----
    private static final int HISTORY_SIZE = 600;           // ~30 seconds of tick history at 20 TPS
    private static final int MEMORY_HISTORY_SIZE = 360;    // 30 minutes of memory snapshots at 5s intervals
    private static final int PLAYER_HISTORY_SIZE = 360;    // 30 minutes of player count snapshots
    private static final int AUTO_LOG_INTERVAL_TICKS = 6000; // 5 minutes at 20 TPS
    private static final int SNAPSHOT_INTERVAL_TICKS = 100;  // 5 seconds at 20 TPS
    private static final DateTimeFormatter LOG_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ---- Enabled state ----
    private volatile boolean enabled = false;

    // ---- Per-tick timing breakdown ----
    private final long[] tickDurationsNs = new long[HISTORY_SIZE];
    private final long[] entityTickTimesNs = new long[HISTORY_SIZE];
    private final long[] tileEntityTickTimesNs = new long[HISTORY_SIZE];
    private final long[] worldTickTimesNs = new long[HISTORY_SIZE];
    private int tickIndex = 0;
    private boolean tickHistoryFilled = false;

    // Tick-level timing markers (set per tick)
    private long tickStartNs = 0;
    private long entityPhaseStartNs = 0;
    private long entityPhaseEndNs = 0;
    private long tileEntityPhaseStartNs = 0;
    private long tileEntityPhaseEndNs = 0;

    // ---- Entity counts by type (registry name) ----
    private final Map<String, AtomicInteger> entityCountsByType = new ConcurrentHashMap<>();

    // ---- Tile entity counts by type (registry name) ----
    private final Map<String, AtomicInteger> tileEntityCountsByType = new ConcurrentHashMap<>();

    // ---- Per-dimension entity / tile entity counts ----
    private final Map<String, Integer> entityCountsByDimension = new ConcurrentHashMap<>();
    private final Map<String, Integer> tileEntityCountsByDimension = new ConcurrentHashMap<>();

    // ---- Chunk load rate monitoring ----
    private final AtomicInteger chunksLoadedThisTick = new AtomicInteger(0);
    private final AtomicInteger chunksLoadedThisInterval = new AtomicInteger(0);
    private final int[] chunkLoadHistory = new int[HISTORY_SIZE];

    // ---- Memory usage trending ----
    private final long[] memoryUsedMB = new long[MEMORY_HISTORY_SIZE];
    private final long[] memoryMaxMB = new long[MEMORY_HISTORY_SIZE];
    private int memoryIndex = 0;
    private boolean memoryHistoryFilled = false;

    // ---- Player count impact correlation ----
    private final int[] playerCountHistory = new int[PLAYER_HISTORY_SIZE];
    private final double[] msptAtPlayerCount = new double[PLAYER_HISTORY_SIZE];
    private int playerHistoryIndex = 0;
    private boolean playerHistoryFilled = false;

    // ---- Network packet rate monitoring ----
    private final AtomicLong packetsInThisInterval = new AtomicLong(0);
    private final AtomicLong packetsOutThisInterval = new AtomicLong(0);
    private final long[] packetsInHistory = new long[MEMORY_HISTORY_SIZE];
    private final long[] packetsOutHistory = new long[MEMORY_HISTORY_SIZE];
    private int packetHistoryIndex = 0;
    private boolean packetHistoryFilled = false;

    // ---- Tick counters ----
    private int autoLogCounter = 0;
    private int snapshotCounter = 0;
    private int totalTicksProfiled = 0;

    // ---- Log directory ----
    private Path logDirectory;

    private PerformanceProfiler() {
        // Initialize log directory
        logDirectory = Paths.get("logs", "thaumicwards-profiler");
    }

    // =====================================================================
    // Public API
    // =====================================================================

    /**
     * Enable or disable the profiler. When disabled, event handlers early-return.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            reset();
            ThaumicWards.LOGGER.info("Performance profiler enabled.");
        } else {
            ThaumicWards.LOGGER.info("Performance profiler disabled.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Reset all profiler data.
     */
    public void reset() {
        tickIndex = 0;
        tickHistoryFilled = false;
        memoryIndex = 0;
        memoryHistoryFilled = false;
        playerHistoryIndex = 0;
        playerHistoryFilled = false;
        packetHistoryIndex = 0;
        packetHistoryFilled = false;
        autoLogCounter = 0;
        snapshotCounter = 0;
        totalTicksProfiled = 0;

        Arrays.fill(tickDurationsNs, 0);
        Arrays.fill(entityTickTimesNs, 0);
        Arrays.fill(tileEntityTickTimesNs, 0);
        Arrays.fill(worldTickTimesNs, 0);
        Arrays.fill(chunkLoadHistory, 0);
        Arrays.fill(memoryUsedMB, 0);
        Arrays.fill(memoryMaxMB, 0);
        Arrays.fill(playerCountHistory, 0);
        Arrays.fill(msptAtPlayerCount, 0);
        Arrays.fill(packetsInHistory, 0);
        Arrays.fill(packetsOutHistory, 0);

        entityCountsByType.clear();
        tileEntityCountsByType.clear();
        entityCountsByDimension.clear();
        tileEntityCountsByDimension.clear();

        chunksLoadedThisTick.set(0);
        chunksLoadedThisInterval.set(0);
        packetsInThisInterval.set(0);
        packetsOutThisInterval.set(0);
    }

    // =====================================================================
    // Event Handlers
    // =====================================================================

    @SubscribeEvent
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        PerformanceProfiler profiler = getInstance();
        if (!profiler.enabled) return;
        profiler.tickStartNs = System.nanoTime();
    }

    @SubscribeEvent
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PerformanceProfiler profiler = getInstance();
        if (!profiler.enabled) return;
        if (profiler.tickStartNs == 0) return;

        long tickEndNs = System.nanoTime();
        long tickDuration = tickEndNs - profiler.tickStartNs;

        int idx = profiler.tickIndex;
        profiler.tickDurationsNs[idx] = tickDuration;

        // Compute entity and tile entity phase times from markers
        long entityTime = 0;
        if (profiler.entityPhaseEndNs > profiler.entityPhaseStartNs) {
            entityTime = profiler.entityPhaseEndNs - profiler.entityPhaseStartNs;
        }
        profiler.entityTickTimesNs[idx] = entityTime;

        long tileEntityTime = 0;
        if (profiler.tileEntityPhaseEndNs > profiler.tileEntityPhaseStartNs) {
            tileEntityTime = profiler.tileEntityPhaseEndNs - profiler.tileEntityPhaseStartNs;
        }
        profiler.tileEntityTickTimesNs[idx] = tileEntityTime;

        // World tick time = total - entity - tileEntity (approximate "other" work)
        long worldTime = tickDuration - entityTime - tileEntityTime;
        profiler.worldTickTimesNs[idx] = Math.max(worldTime, 0);

        // Chunk load tracking
        profiler.chunkLoadHistory[idx] = profiler.chunksLoadedThisTick.getAndSet(0);
        profiler.chunksLoadedThisInterval.addAndGet(profiler.chunkLoadHistory[idx]);

        // Advance tick index
        profiler.tickIndex = (idx + 1) % HISTORY_SIZE;
        if (profiler.tickIndex == 0) profiler.tickHistoryFilled = true;
        profiler.totalTicksProfiled++;

        // Reset phase markers
        profiler.entityPhaseStartNs = 0;
        profiler.entityPhaseEndNs = 0;
        profiler.tileEntityPhaseStartNs = 0;
        profiler.tileEntityPhaseEndNs = 0;

        // Periodic snapshot (memory, player count, packets)
        profiler.snapshotCounter++;
        if (profiler.snapshotCounter >= SNAPSHOT_INTERVAL_TICKS) {
            profiler.snapshotCounter = 0;
            profiler.takePeriodicSnapshot();
        }

        // Auto-log
        profiler.autoLogCounter++;
        if (profiler.autoLogCounter >= AUTO_LOG_INTERVAL_TICKS) {
            profiler.autoLogCounter = 0;
            profiler.writeAutoLog();
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        PerformanceProfiler profiler = getInstance();
        if (!profiler.enabled) return;

        if (event.phase == TickEvent.Phase.START) {
            if (event.world instanceof ServerWorld) {
                // Use START phase to scan entities and tile entities for counting
                profiler.scanWorldEntities((ServerWorld) event.world);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        PerformanceProfiler profiler = getInstance();
        if (!profiler.enabled) return;
        if (event.getWorld() == null || event.getWorld().isClientSide()) return;
        profiler.chunksLoadedThisTick.incrementAndGet();
    }

    // =====================================================================
    // Phase Markers (called from mixins or tick handler hooks)
    // =====================================================================

    /**
     * Call at the start of entity ticking phase.
     */
    public void markEntityPhaseStart() {
        if (!enabled) return;
        entityPhaseStartNs = System.nanoTime();
    }

    /**
     * Call at the end of entity ticking phase.
     */
    public void markEntityPhaseEnd() {
        if (!enabled) return;
        entityPhaseEndNs = System.nanoTime();
    }

    /**
     * Call at the start of tile entity ticking phase.
     */
    public void markTileEntityPhaseStart() {
        if (!enabled) return;
        tileEntityPhaseStartNs = System.nanoTime();
    }

    /**
     * Call at the end of tile entity ticking phase.
     */
    public void markTileEntityPhaseEnd() {
        if (!enabled) return;
        tileEntityPhaseEndNs = System.nanoTime();
    }

    /**
     * Record an inbound network packet.
     */
    public void recordPacketIn() {
        if (!enabled) return;
        packetsInThisInterval.incrementAndGet();
    }

    /**
     * Record an outbound network packet.
     */
    public void recordPacketOut() {
        if (!enabled) return;
        packetsOutThisInterval.incrementAndGet();
    }

    // =====================================================================
    // Internal - Scanning and Snapshots
    // =====================================================================

    private void scanWorldEntities(ServerWorld world) {
        String dimKey = world.dimension().location().toString();

        int entityCount = 0;
        for (Entity entity : world.getAllEntities()) {
            entityCount++;
            String typeName = entity.getType().getRegistryName() != null
                    ? entity.getType().getRegistryName().toString()
                    : entity.getClass().getSimpleName();
            entityCountsByType.computeIfAbsent(typeName, k -> new AtomicInteger(0)).incrementAndGet();
        }
        entityCountsByDimension.put(dimKey, entityCount);

        int tileEntityCount = 0;
        for (TileEntity te : world.blockEntityList) {
            tileEntityCount++;
            String typeName = te.getType().getRegistryName() != null
                    ? te.getType().getRegistryName().toString()
                    : te.getClass().getSimpleName();
            tileEntityCountsByType.computeIfAbsent(typeName, k -> new AtomicInteger(0)).incrementAndGet();
        }
        tileEntityCountsByDimension.put(dimKey, tileEntityCount);
    }

    private void takePeriodicSnapshot() {
        // Memory
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMB = runtime.maxMemory() / 1024 / 1024;
        memoryUsedMB[memoryIndex] = usedMB;
        memoryMaxMB[memoryIndex] = maxMB;
        memoryIndex = (memoryIndex + 1) % MEMORY_HISTORY_SIZE;
        if (memoryIndex == 0) memoryHistoryFilled = true;

        // Packets
        packetsInHistory[packetHistoryIndex] = packetsInThisInterval.getAndSet(0);
        packetsOutHistory[packetHistoryIndex] = packetsOutThisInterval.getAndSet(0);
        packetHistoryIndex = (packetHistoryIndex + 1) % MEMORY_HISTORY_SIZE;
        if (packetHistoryIndex == 0) packetHistoryFilled = true;
    }

    /**
     * Record player count and corresponding MSPT for correlation analysis.
     * Called externally from the server tick handler.
     */
    public void recordPlayerCount(int playerCount) {
        if (!enabled) return;
        playerCountHistory[playerHistoryIndex] = playerCount;
        msptAtPlayerCount[playerHistoryIndex] = getAverageTickMs();
        playerHistoryIndex = (playerHistoryIndex + 1) % PLAYER_HISTORY_SIZE;
        if (playerHistoryIndex == 0) playerHistoryFilled = true;
    }

    // =====================================================================
    // Data Access Methods (used by LagMapCommand)
    // =====================================================================

    /**
     * Returns average tick duration in milliseconds over the recent history window.
     */
    public double getAverageTickMs() {
        int count = tickHistoryFilled ? HISTORY_SIZE : tickIndex;
        if (count == 0) return 0;
        long total = 0;
        for (int i = 0; i < count; i++) total += tickDurationsNs[i];
        return (double) total / count / 1_000_000.0;
    }

    /**
     * Returns average entity tick time in ms.
     */
    public double getAverageEntityTickMs() {
        int count = tickHistoryFilled ? HISTORY_SIZE : tickIndex;
        if (count == 0) return 0;
        long total = 0;
        for (int i = 0; i < count; i++) total += entityTickTimesNs[i];
        return (double) total / count / 1_000_000.0;
    }

    /**
     * Returns average tile entity tick time in ms.
     */
    public double getAverageTileEntityTickMs() {
        int count = tickHistoryFilled ? HISTORY_SIZE : tickIndex;
        if (count == 0) return 0;
        long total = 0;
        for (int i = 0; i < count; i++) total += tileEntityTickTimesNs[i];
        return (double) total / count / 1_000_000.0;
    }

    /**
     * Returns average "other" tick time in ms (world tick minus entities and tile entities).
     */
    public double getAverageWorldTickMs() {
        int count = tickHistoryFilled ? HISTORY_SIZE : tickIndex;
        if (count == 0) return 0;
        long total = 0;
        for (int i = 0; i < count; i++) total += worldTickTimesNs[i];
        return (double) total / count / 1_000_000.0;
    }

    /**
     * Returns the top N entity types by count, sorted descending.
     */
    public List<Map.Entry<String, Integer>> getTopEntityTypes(int n) {
        return entityCountsByType.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().get()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Returns the top N tile entity types by count, sorted descending.
     */
    public List<Map.Entry<String, Integer>> getTopTileEntityTypes(int n) {
        return tileEntityCountsByType.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().get()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Returns entity counts per dimension.
     */
    public Map<String, Integer> getEntityCountsByDimension() {
        return new LinkedHashMap<>(entityCountsByDimension);
    }

    /**
     * Returns tile entity counts per dimension.
     */
    public Map<String, Integer> getTileEntityCountsByDimension() {
        return new LinkedHashMap<>(tileEntityCountsByDimension);
    }

    /**
     * Returns total entities across all dimensions.
     */
    public int getTotalEntityCount() {
        return entityCountsByDimension.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns total tile entities across all dimensions.
     */
    public int getTotalTileEntityCount() {
        return tileEntityCountsByDimension.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns average chunks loaded per tick over the history window.
     */
    public double getAverageChunkLoadsPerTick() {
        int count = tickHistoryFilled ? HISTORY_SIZE : tickIndex;
        if (count == 0) return 0;
        int total = 0;
        for (int i = 0; i < count; i++) total += chunkLoadHistory[i];
        return (double) total / count;
    }

    /**
     * Returns total chunks loaded in the current snapshot interval.
     */
    public int getChunksLoadedThisInterval() {
        return chunksLoadedThisInterval.getAndSet(0);
    }

    /**
     * Returns current memory usage in MB.
     */
    public long getCurrentMemoryUsedMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    }

    /**
     * Returns max memory in MB.
     */
    public long getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    /**
     * Returns memory trend data: list of (usedMB, maxMB) pairs, oldest first.
     */
    public List<long[]> getMemoryTrend() {
        int count = memoryHistoryFilled ? MEMORY_HISTORY_SIZE : memoryIndex;
        List<long[]> trend = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int idx = memoryHistoryFilled ? (memoryIndex + i) % MEMORY_HISTORY_SIZE : i;
            trend.add(new long[]{memoryUsedMB[idx], memoryMaxMB[idx]});
        }
        return trend;
    }

    /**
     * Returns the memory usage delta over the last N snapshots.
     * Positive = memory growing, negative = memory shrinking.
     */
    public long getMemoryDeltaMB(int snapshots) {
        int count = memoryHistoryFilled ? MEMORY_HISTORY_SIZE : memoryIndex;
        if (count < 2) return 0;
        int actual = Math.min(snapshots, count);
        int newestIdx = memoryHistoryFilled
                ? (memoryIndex - 1 + MEMORY_HISTORY_SIZE) % MEMORY_HISTORY_SIZE
                : memoryIndex - 1;
        int oldestIdx = memoryHistoryFilled
                ? (memoryIndex - actual + MEMORY_HISTORY_SIZE) % MEMORY_HISTORY_SIZE
                : Math.max(0, memoryIndex - actual);
        return memoryUsedMB[newestIdx] - memoryUsedMB[oldestIdx];
    }

    /**
     * Returns player count impact correlation data.
     * Map of playerCount -> average MSPT at that count.
     */
    public Map<Integer, Double> getPlayerCountCorrelation() {
        int count = playerHistoryFilled ? PLAYER_HISTORY_SIZE : playerHistoryIndex;
        Map<Integer, List<Double>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int pc = playerCountHistory[i];
            grouped.computeIfAbsent(pc, k -> new ArrayList<>()).add(msptAtPlayerCount[i]);
        }
        Map<Integer, Double> result = new TreeMap<>();
        for (Map.Entry<Integer, List<Double>> entry : grouped.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            result.put(entry.getKey(), avg);
        }
        return result;
    }

    /**
     * Returns average packets in per 5-second interval.
     */
    public double getAveragePacketsIn() {
        int count = packetHistoryFilled ? MEMORY_HISTORY_SIZE : packetHistoryIndex;
        if (count == 0) return 0;
        long total = 0;
        for (int i = 0; i < count; i++) total += packetsInHistory[i];
        return (double) total / count;
    }

    /**
     * Returns average packets out per 5-second interval.
     */
    public double getAveragePacketsOut() {
        int count = packetHistoryFilled ? MEMORY_HISTORY_SIZE : packetHistoryIndex;
        if (count == 0) return 0;
        long total = 0;
        for (int i = 0; i < count; i++) total += packetsOutHistory[i];
        return (double) total / count;
    }

    /**
     * Returns current interval packets in.
     */
    public long getCurrentPacketsIn() {
        return packetsInThisInterval.get();
    }

    /**
     * Returns current interval packets out.
     */
    public long getCurrentPacketsOut() {
        return packetsOutThisInterval.get();
    }

    /**
     * Returns total ticks profiled since last reset.
     */
    public int getTotalTicksProfiled() {
        return totalTicksProfiled;
    }

    /**
     * Clears entity/tile entity counters per snapshot cycle.
     * Should be called at the start of each server tick to get fresh counts.
     */
    public void clearEntityCounters() {
        entityCountsByType.clear();
        tileEntityCountsByType.clear();
        entityCountsByDimension.clear();
        tileEntityCountsByDimension.clear();
    }

    // =====================================================================
    // Auto-logging
    // =====================================================================

    private void writeAutoLog() {
        try {
            if (!Files.exists(logDirectory)) {
                Files.createDirectories(logDirectory);
            }

            String fileName = "profiler-" + LocalDateTime.now().format(FILE_TIME_FMT) + ".log";
            Path logFile = logDirectory.resolve(fileName);

            try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write("========================================\n");
                writer.write("Thaumic Wards Performance Report\n");
                writer.write("Time: " + LocalDateTime.now().format(LOG_TIME_FMT) + "\n");
                writer.write("Ticks profiled: " + totalTicksProfiled + "\n");
                writer.write("========================================\n\n");

                // Tick timing
                writer.write("--- Tick Timing ---\n");
                writer.write(String.format("Average MSPT: %.2f ms\n", getAverageTickMs()));
                writer.write(String.format("  Entity ticking: %.2f ms (%.1f%%)\n",
                        getAverageEntityTickMs(),
                        getAverageTickMs() > 0 ? (getAverageEntityTickMs() / getAverageTickMs() * 100) : 0));
                writer.write(String.format("  Tile entity ticking: %.2f ms (%.1f%%)\n",
                        getAverageTileEntityTickMs(),
                        getAverageTickMs() > 0 ? (getAverageTileEntityTickMs() / getAverageTickMs() * 100) : 0));
                writer.write(String.format("  Other (world, network, etc.): %.2f ms (%.1f%%)\n",
                        getAverageWorldTickMs(),
                        getAverageTickMs() > 0 ? (getAverageWorldTickMs() / getAverageTickMs() * 100) : 0));
                writer.write("\n");

                // Entity counts
                writer.write("--- Top Entity Types ---\n");
                for (Map.Entry<String, Integer> entry : getTopEntityTypes(10)) {
                    writer.write(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
                }
                writer.write("Total entities: " + getTotalEntityCount() + "\n\n");

                // Tile entity counts
                writer.write("--- Top Tile Entity Types ---\n");
                for (Map.Entry<String, Integer> entry : getTopTileEntityTypes(10)) {
                    writer.write(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
                }
                writer.write("Total tile entities: " + getTotalTileEntityCount() + "\n\n");

                // Per-dimension counts
                writer.write("--- Per-Dimension Counts ---\n");
                for (Map.Entry<String, Integer> entry : getEntityCountsByDimension().entrySet()) {
                    int teCount = tileEntityCountsByDimension.getOrDefault(entry.getKey(), 0);
                    writer.write(String.format("  %s: %d entities, %d tile entities\n",
                            entry.getKey(), entry.getValue(), teCount));
                }
                writer.write("\n");

                // Chunk loading
                writer.write("--- Chunk Loading ---\n");
                writer.write(String.format("Avg chunks loaded/tick: %.2f\n", getAverageChunkLoadsPerTick()));
                writer.write("\n");

                // Memory
                writer.write("--- Memory ---\n");
                writer.write(String.format("Current: %d MB / %d MB (%d%%)\n",
                        getCurrentMemoryUsedMB(), getMaxMemoryMB(),
                        getMaxMemoryMB() > 0 ? (getCurrentMemoryUsedMB() * 100 / getMaxMemoryMB()) : 0));
                writer.write(String.format("5-min delta: %+d MB\n", getMemoryDeltaMB(60)));
                writer.write("\n");

                // Player correlation
                writer.write("--- Player Count Impact ---\n");
                Map<Integer, Double> correlation = getPlayerCountCorrelation();
                for (Map.Entry<Integer, Double> entry : correlation.entrySet()) {
                    writer.write(String.format("  %d players -> %.2f ms/tick avg\n",
                            entry.getKey(), entry.getValue()));
                }
                writer.write("\n");

                // Network
                writer.write("--- Network ---\n");
                writer.write(String.format("Avg packets in/5s: %.1f\n", getAveragePacketsIn()));
                writer.write(String.format("Avg packets out/5s: %.1f\n", getAveragePacketsOut()));
                writer.write("\n\n");
            }

            ThaumicWards.LOGGER.info("Performance profiler auto-log written to {}", logFile.toAbsolutePath());
        } catch (IOException e) {
            ThaumicWards.LOGGER.error("Failed to write profiler auto-log", e);
        }
    }

    /**
     * Force an immediate log write (called by admin command).
     */
    public void forceWriteLog() {
        writeAutoLog();
    }
}

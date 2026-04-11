package com.thaumicwards.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thaumicwards.performance.PerformanceProfiler;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Map;

/**
 * Admin command: /thaumicwards lagmap
 * Shows a comprehensive breakdown of server lag sources.
 *
 * Sub-commands:
 *   /thaumicwards lagmap           - Overview of all lag sources
 *   /thaumicwards lagmap entities   - Detailed entity breakdown
 *   /thaumicwards lagmap tiles      - Detailed tile entity breakdown
 *   /thaumicwards lagmap memory     - Memory usage trend
 *   /thaumicwards lagmap network    - Network packet statistics
 *   /thaumicwards lagmap players    - Player count impact correlation
 *   /thaumicwards lagmap dimensions - Per-dimension entity/tile entity counts
 *   /thaumicwards lagmap start      - Enable the profiler
 *   /thaumicwards lagmap stop       - Disable the profiler
 *   /thaumicwards lagmap dump       - Force a log file write now
 */
public class LagMapCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("thaumicwards")
                .then(Commands.literal("lagmap")
                    .requires(source -> source.hasPermission(2)) // OP only
                    .executes(LagMapCommand::showOverview)
                    .then(Commands.literal("entities").executes(LagMapCommand::showEntities))
                    .then(Commands.literal("tiles").executes(LagMapCommand::showTiles))
                    .then(Commands.literal("memory").executes(LagMapCommand::showMemory))
                    .then(Commands.literal("network").executes(LagMapCommand::showNetwork))
                    .then(Commands.literal("players").executes(LagMapCommand::showPlayers))
                    .then(Commands.literal("dimensions").executes(LagMapCommand::showDimensions))
                    .then(Commands.literal("start").executes(LagMapCommand::startProfiler))
                    .then(Commands.literal("stop").executes(LagMapCommand::stopProfiler))
                    .then(Commands.literal("dump").executes(LagMapCommand::dumpLog))
                )
        );
    }

    // ---- Start / Stop / Dump ----

    private static int startProfiler(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (profiler.isEnabled()) {
            context.getSource().sendSuccess(msg("Profiler is already running.", TextFormatting.YELLOW), false);
        } else {
            profiler.setEnabled(true);
            context.getSource().sendSuccess(msg("Performance profiler started.", TextFormatting.GREEN), false);
            context.getSource().sendSuccess(
                    msg("Auto-logging every 5 minutes to logs/thaumicwards-profiler/", TextFormatting.GRAY), false);
        }
        return 1;
    }

    private static int stopProfiler(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!profiler.isEnabled()) {
            context.getSource().sendSuccess(msg("Profiler is not running.", TextFormatting.YELLOW), false);
        } else {
            profiler.setEnabled(false);
            context.getSource().sendSuccess(msg("Performance profiler stopped.", TextFormatting.RED), false);
        }
        return 1;
    }

    private static int dumpLog(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!profiler.isEnabled()) {
            context.getSource().sendSuccess(msg("Profiler is not running. Start with /thaumicwards lagmap start", TextFormatting.RED), false);
            return 0;
        }
        profiler.forceWriteLog();
        context.getSource().sendSuccess(msg("Performance log dumped to logs/thaumicwards-profiler/", TextFormatting.GREEN), false);
        return 1;
    }

    // ---- Overview ----

    private static int showOverview(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        CommandSource src = context.getSource();

        if (!profiler.isEnabled()) {
            src.sendSuccess(msg("Profiler is not running.", TextFormatting.RED), false);
            src.sendSuccess(msg("Start with: /thaumicwards lagmap start", TextFormatting.GRAY), false);
            return 0;
        }

        src.sendSuccess(header("Lag Map Overview"), false);

        // Tick timing breakdown
        double totalMs = profiler.getAverageTickMs();
        double entityMs = profiler.getAverageEntityTickMs();
        double tileMs = profiler.getAverageTileEntityTickMs();
        double otherMs = profiler.getAverageWorldTickMs();

        src.sendSuccess(subHeader("Tick Timing (avg)"), false);
        src.sendSuccess(tickBar("Total", totalMs, 50.0), false);
        src.sendSuccess(tickBar("Entities", entityMs, totalMs), false);
        src.sendSuccess(tickBar("Tile Entities", tileMs, totalMs), false);
        src.sendSuccess(tickBar("Other", otherMs, totalMs), false);

        // Top lag sources
        src.sendSuccess(subHeader("Top Entity Types"), false);
        List<Map.Entry<String, Integer>> topEntities = profiler.getTopEntityTypes(5);
        for (Map.Entry<String, Integer> entry : topEntities) {
            src.sendSuccess(new StringTextComponent("  " + shortenTypeName(entry.getKey()) + ": ")
                    .withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.valueOf(entry.getValue()))
                            .withStyle(TextFormatting.WHITE)), false);
        }

        src.sendSuccess(subHeader("Top Tile Entity Types"), false);
        List<Map.Entry<String, Integer>> topTiles = profiler.getTopTileEntityTypes(5);
        for (Map.Entry<String, Integer> entry : topTiles) {
            src.sendSuccess(new StringTextComponent("  " + shortenTypeName(entry.getKey()) + ": ")
                    .withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(String.valueOf(entry.getValue()))
                            .withStyle(TextFormatting.WHITE)), false);
        }

        // Quick stats
        src.sendSuccess(subHeader("Quick Stats"), false);
        long usedMB = profiler.getCurrentMemoryUsedMB();
        long maxMB = profiler.getMaxMemoryMB();
        int pct = maxMB > 0 ? (int) (usedMB * 100 / maxMB) : 0;
        TextFormatting memColor = pct < 70 ? TextFormatting.GREEN : pct < 85 ? TextFormatting.YELLOW : TextFormatting.RED;
        src.sendSuccess(new StringTextComponent("  Memory: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%d/%dMB (%d%%)", usedMB, maxMB, pct)).withStyle(memColor))
                .append(new StringTextComponent(String.format(" [%+dMB/5min]", profiler.getMemoryDeltaMB(60))).withStyle(TextFormatting.DARK_GRAY)), false);
        src.sendSuccess(new StringTextComponent("  Chunk loads/tick: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%.2f", profiler.getAverageChunkLoadsPerTick())).withStyle(TextFormatting.WHITE)), false);
        src.sendSuccess(new StringTextComponent("  Entities: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.valueOf(profiler.getTotalEntityCount())).withStyle(TextFormatting.WHITE))
                .append(new StringTextComponent(" | Tile Entities: ").withStyle(TextFormatting.GRAY))
                .append(new StringTextComponent(String.valueOf(profiler.getTotalTileEntityCount())).withStyle(TextFormatting.WHITE)), false);
        src.sendSuccess(new StringTextComponent("  Ticks profiled: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.valueOf(profiler.getTotalTicksProfiled())).withStyle(TextFormatting.WHITE)), false);

        src.sendSuccess(new StringTextComponent("Use /thaumicwards lagmap <entities|tiles|memory|network|players|dimensions> for details")
                .withStyle(TextFormatting.DARK_GRAY, TextFormatting.ITALIC), false);

        return 1;
    }

    // ---- Detailed sub-commands ----

    private static int showEntities(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!checkEnabled(context, profiler)) return 0;
        CommandSource src = context.getSource();

        src.sendSuccess(header("Entity Breakdown"), false);
        src.sendSuccess(new StringTextComponent("Total entities: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.valueOf(profiler.getTotalEntityCount())).withStyle(TextFormatting.WHITE)), false);

        List<Map.Entry<String, Integer>> top = profiler.getTopEntityTypes(15);
        int rank = 1;
        for (Map.Entry<String, Integer> entry : top) {
            TextFormatting color = rank <= 3 ? TextFormatting.RED : rank <= 7 ? TextFormatting.YELLOW : TextFormatting.GREEN;
            src.sendSuccess(new StringTextComponent(String.format(" %2d. ", rank)).withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(shortenTypeName(entry.getKey())).withStyle(color))
                    .append(new StringTextComponent(": " + entry.getValue()).withStyle(TextFormatting.WHITE)), false);
            rank++;
        }
        return 1;
    }

    private static int showTiles(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!checkEnabled(context, profiler)) return 0;
        CommandSource src = context.getSource();

        src.sendSuccess(header("Tile Entity Breakdown"), false);
        src.sendSuccess(new StringTextComponent("Total tile entities: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.valueOf(profiler.getTotalTileEntityCount())).withStyle(TextFormatting.WHITE)), false);

        List<Map.Entry<String, Integer>> top = profiler.getTopTileEntityTypes(15);
        int rank = 1;
        for (Map.Entry<String, Integer> entry : top) {
            TextFormatting color = rank <= 3 ? TextFormatting.RED : rank <= 7 ? TextFormatting.YELLOW : TextFormatting.GREEN;
            src.sendSuccess(new StringTextComponent(String.format(" %2d. ", rank)).withStyle(TextFormatting.GRAY)
                    .append(new StringTextComponent(shortenTypeName(entry.getKey())).withStyle(color))
                    .append(new StringTextComponent(": " + entry.getValue()).withStyle(TextFormatting.WHITE)), false);
            rank++;
        }
        return 1;
    }

    private static int showMemory(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!checkEnabled(context, profiler)) return 0;
        CommandSource src = context.getSource();

        src.sendSuccess(header("Memory Usage Trend"), false);

        long usedMB = profiler.getCurrentMemoryUsedMB();
        long maxMB = profiler.getMaxMemoryMB();
        int pct = maxMB > 0 ? (int) (usedMB * 100 / maxMB) : 0;

        src.sendSuccess(new StringTextComponent("Current: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%d MB / %d MB (%d%%)", usedMB, maxMB, pct))
                        .withStyle(pct < 70 ? TextFormatting.GREEN : pct < 85 ? TextFormatting.YELLOW : TextFormatting.RED)), false);

        // Trend over different periods
        src.sendSuccess(new StringTextComponent("  1-min delta: ").withStyle(TextFormatting.GRAY)
                .append(formatDelta(profiler.getMemoryDeltaMB(12))), false);
        src.sendSuccess(new StringTextComponent("  5-min delta: ").withStyle(TextFormatting.GRAY)
                .append(formatDelta(profiler.getMemoryDeltaMB(60))), false);
        src.sendSuccess(new StringTextComponent("  15-min delta: ").withStyle(TextFormatting.GRAY)
                .append(formatDelta(profiler.getMemoryDeltaMB(180))), false);

        // Visual bar
        int barLength = 40;
        int filledLength = maxMB > 0 ? (int) (usedMB * barLength / maxMB) : 0;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filledLength ? '|' : ' ');
        }
        bar.append("]");
        src.sendSuccess(new StringTextComponent(bar.toString())
                .withStyle(pct < 70 ? TextFormatting.GREEN : pct < 85 ? TextFormatting.YELLOW : TextFormatting.RED), false);

        return 1;
    }

    private static int showNetwork(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!checkEnabled(context, profiler)) return 0;
        CommandSource src = context.getSource();

        src.sendSuccess(header("Network Packet Stats"), false);
        src.sendSuccess(new StringTextComponent("Current interval:").withStyle(TextFormatting.GRAY), false);
        src.sendSuccess(new StringTextComponent("  Packets in:  ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.valueOf(profiler.getCurrentPacketsIn())).withStyle(TextFormatting.WHITE)), false);
        src.sendSuccess(new StringTextComponent("  Packets out: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.valueOf(profiler.getCurrentPacketsOut())).withStyle(TextFormatting.WHITE)), false);

        src.sendSuccess(new StringTextComponent("Average per 5s interval:").withStyle(TextFormatting.GRAY), false);
        src.sendSuccess(new StringTextComponent("  Packets in:  ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%.1f", profiler.getAveragePacketsIn())).withStyle(TextFormatting.WHITE)), false);
        src.sendSuccess(new StringTextComponent("  Packets out: ").withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(String.format("%.1f", profiler.getAveragePacketsOut())).withStyle(TextFormatting.WHITE)), false);

        return 1;
    }

    private static int showPlayers(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!checkEnabled(context, profiler)) return 0;
        CommandSource src = context.getSource();

        src.sendSuccess(header("Player Count Impact"), false);

        Map<Integer, Double> correlation = profiler.getPlayerCountCorrelation();
        if (correlation.isEmpty()) {
            src.sendSuccess(msg("No player count data collected yet. Wait a few minutes.", TextFormatting.YELLOW), false);
            return 1;
        }

        src.sendSuccess(new StringTextComponent(" Players | Avg MSPT").withStyle(TextFormatting.GRAY, TextFormatting.UNDERLINE), false);
        for (Map.Entry<Integer, Double> entry : correlation.entrySet()) {
            double mspt = entry.getValue();
            TextFormatting color = mspt < 30 ? TextFormatting.GREEN : mspt < 45 ? TextFormatting.YELLOW : TextFormatting.RED;
            src.sendSuccess(new StringTextComponent(String.format(" %7d | ", entry.getKey())).withStyle(TextFormatting.WHITE)
                    .append(new StringTextComponent(String.format("%.2f ms", mspt)).withStyle(color)), false);
        }

        return 1;
    }

    private static int showDimensions(CommandContext<CommandSource> context) {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        if (!checkEnabled(context, profiler)) return 0;
        CommandSource src = context.getSource();

        src.sendSuccess(header("Per-Dimension Counts"), false);

        Map<String, Integer> entities = profiler.getEntityCountsByDimension();
        Map<String, Integer> tiles = profiler.getTileEntityCountsByDimension();

        // Collect all dimension keys
        java.util.Set<String> allDims = new java.util.TreeSet<>();
        allDims.addAll(entities.keySet());
        allDims.addAll(tiles.keySet());

        if (allDims.isEmpty()) {
            src.sendSuccess(msg("No dimension data collected yet.", TextFormatting.YELLOW), false);
            return 1;
        }

        src.sendSuccess(new StringTextComponent(" Dimension               | Entities | Tile Entities")
                .withStyle(TextFormatting.GRAY, TextFormatting.UNDERLINE), false);

        for (String dim : allDims) {
            int entityCount = entities.getOrDefault(dim, 0);
            int tileCount = tiles.getOrDefault(dim, 0);
            String dimShort = dim.length() > 25 ? dim.substring(dim.lastIndexOf(':') + 1) : dim;
            src.sendSuccess(new StringTextComponent(String.format(" %-25s| ", dimShort)).withStyle(TextFormatting.AQUA)
                    .append(new StringTextComponent(String.format("%8d | %13d", entityCount, tileCount)).withStyle(TextFormatting.WHITE)), false);
        }

        src.sendSuccess(new StringTextComponent(String.format(" %-25s| %8d | %13d", "TOTAL",
                profiler.getTotalEntityCount(), profiler.getTotalTileEntityCount()))
                .withStyle(TextFormatting.WHITE, TextFormatting.BOLD), false);

        return 1;
    }

    // ---- Utility ----

    private static boolean checkEnabled(CommandContext<CommandSource> context, PerformanceProfiler profiler) {
        if (!profiler.isEnabled()) {
            context.getSource().sendSuccess(msg("Profiler is not running.", TextFormatting.RED), false);
            context.getSource().sendSuccess(msg("Start with: /thaumicwards lagmap start", TextFormatting.GRAY), false);
            return false;
        }
        return true;
    }

    private static IFormattableTextComponent header(String text) {
        return new StringTextComponent("=== " + text + " ===")
                .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD);
    }

    private static IFormattableTextComponent subHeader(String text) {
        return new StringTextComponent("--- " + text + " ---")
                .withStyle(TextFormatting.YELLOW);
    }

    private static IFormattableTextComponent msg(String text, TextFormatting color) {
        return new StringTextComponent(text).withStyle(color);
    }

    private static IFormattableTextComponent tickBar(String label, double ms, double maxMs) {
        TextFormatting color;
        if (ms < 10) color = TextFormatting.GREEN;
        else if (ms < 30) color = TextFormatting.YELLOW;
        else color = TextFormatting.RED;

        double pct = maxMs > 0 ? (ms / maxMs * 100) : 0;
        int barLen = 20;
        int filled = maxMs > 0 ? (int) Math.round(ms / maxMs * barLen) : 0;
        filled = Math.min(filled, barLen);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLen; i++) {
            bar.append(i < filled ? '|' : '.');
        }

        return new StringTextComponent(String.format("  %-14s ", label)).withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent("[" + bar + "] ").withStyle(color))
                .append(new StringTextComponent(String.format("%.2fms", ms)).withStyle(TextFormatting.WHITE))
                .append(new StringTextComponent(String.format(" (%.0f%%)", pct)).withStyle(TextFormatting.DARK_GRAY));
    }

    private static IFormattableTextComponent formatDelta(long deltaMB) {
        TextFormatting color = deltaMB <= 0 ? TextFormatting.GREEN : deltaMB < 50 ? TextFormatting.YELLOW : TextFormatting.RED;
        return new StringTextComponent(String.format("%+d MB", deltaMB)).withStyle(color);
    }

    /**
     * Shorten a registry name like "minecraft:zombie" to just "zombie",
     * or "thaumic_wards:ward_stone" to "tw:ward_stone".
     */
    private static String shortenTypeName(String fullName) {
        if (fullName == null) return "unknown";
        if (fullName.startsWith("minecraft:")) {
            return fullName.substring("minecraft:".length());
        }
        if (fullName.startsWith("thaumic_wards:")) {
            return "tw:" + fullName.substring("thaumic_wards:".length());
        }
        // For other mods, keep namespace:name but abbreviate namespace
        int colon = fullName.indexOf(':');
        if (colon > 0 && colon < fullName.length() - 1) {
            String ns = fullName.substring(0, Math.min(colon, 8));
            return ns + ":" + fullName.substring(colon + 1);
        }
        return fullName;
    }
}

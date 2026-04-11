package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Automatic server restart scheduler with in-game warnings.
 * Designed for heavy modded servers (183 mods, 60+ players) to combat
 * memory leaks from Forge chunk cache, AE2 terminals, Valkyrien Skies
 * physics objects, and entity NBT accumulation.
 */
public class RestartScheduler {

    private static boolean enabled = false;
    private static boolean restartInProgress = false;
    private static long restartTickTarget = -1;
    private static long currentTick = 0;

    private static final int[] WARNING_SECONDS = {900, 600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1};
    private static int lastWarningSent = Integer.MAX_VALUE;

    @SuppressWarnings("unchecked")
    private static List<Integer> restartHours = Arrays.asList(4, 10, 16, 22);

    private static int scheduleCheckCounter = 0;
    private static final int SCHEDULE_CHECK_INTERVAL = 1200;

    private static boolean savePending = false;
    private static boolean saveOffSent = false;

    public static void init() {
        enabled = ServerConfig.AUTO_RESTART_ENABLED.get();
        restartInProgress = false;
        restartTickTarget = -1;
        currentTick = 0;
        lastWarningSent = Integer.MAX_VALUE;
        scheduleCheckCounter = 0;
        savePending = false;
        saveOffSent = false;

        if (enabled) {
            restartHours = (List<Integer>) (List<?>) ServerConfig.RESTART_HOURS.get();
            ThaumicWards.LOGGER.info("Auto-restart scheduler enabled. Restart hours: {}", restartHours);
            ThaumicWards.LOGGER.info("Next restart: {}", getNextRestartTime());
        } else {
            ThaumicWards.LOGGER.info("Auto-restart scheduler is disabled.");
        }
    }

    public static void reset() {
        enabled = false;
        restartInProgress = false;
        restartTickTarget = -1;
        currentTick = 0;
        lastWarningSent = Integer.MAX_VALUE;
        scheduleCheckCounter = 0;
        savePending = false;
        saveOffSent = false;
    }

    public static void triggerRestart(int secondsUntilRestart) {
        if (restartInProgress) return;
        restartInProgress = true;
        restartTickTarget = currentTick + ((long) secondsUntilRestart * 20L);
        lastWarningSent = Integer.MAX_VALUE;
        savePending = false;
        saveOffSent = false;
        ThaumicWards.LOGGER.info("Restart triggered in {} seconds.", secondsUntilRestart);
    }

    public static void cancelRestart() {
        if (restartInProgress) {
            restartInProgress = false;
            restartTickTarget = -1;
            lastWarningSent = Integer.MAX_VALUE;
            savePending = false;
            saveOffSent = false;
            ThaumicWards.LOGGER.info("Restart cancelled.");
        }
    }

    public static boolean isRestartPending() {
        return restartInProgress;
    }

    public static int getSecondsRemaining() {
        if (!restartInProgress || restartTickTarget < 0) return -1;
        long ticksRemaining = restartTickTarget - currentTick;
        return Math.max(0, (int) (ticksRemaining / 20));
    }

    public static String getNextRestartTime() {
        if (restartHours.isEmpty()) return "none scheduled";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nearest = null;
        for (int hour : restartHours) {
            LocalDateTime candidate = now.toLocalDate().atTime(hour, 0);
            if (candidate.isBefore(now) || candidate.isEqual(now)) {
                candidate = candidate.plusDays(1);
            }
            if (nearest == null || candidate.isBefore(nearest)) {
                nearest = candidate;
            }
        }
        if (nearest == null) return "none scheduled";
        long minutesUntil = ChronoUnit.MINUTES.between(now, nearest);
        long hours = minutesUntil / 60;
        long mins = minutesUntil % 60;
        return String.format("%02d:00 (%dh %dm from now)", nearest.getHour(), hours, mins);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.world instanceof ServerWorld)) return;
        ServerWorld world = (ServerWorld) event.world;
        if (world.dimension() != World.OVERWORLD) return;
        currentTick++;
        if (enabled && !restartInProgress) {
            scheduleCheckCounter++;
            if (scheduleCheckCounter >= SCHEDULE_CHECK_INTERVAL) {
                scheduleCheckCounter = 0;
                checkSchedule();
            }
        }
        if (restartInProgress) {
            processRestart(world);
        }
    }

    private static void checkSchedule() {
        LocalTime now = LocalTime.now();
        int warningLeadMinutes = ServerConfig.RESTART_WARNING_MINUTES.get();
        for (int hour : restartHours) {
            LocalTime restartTime = LocalTime.of(hour, 0);
            LocalTime warningTime = restartTime.minusMinutes(warningLeadMinutes);
            long secondsUntilWarning = ChronoUnit.SECONDS.between(now, warningTime);
            if (secondsUntilWarning >= 0 && secondsUntilWarning < 60) {
                int totalSeconds = warningLeadMinutes * 60 + (int) secondsUntilWarning;
                triggerRestart(totalSeconds);
                ThaumicWards.LOGGER.info("Scheduled restart at {}:00, countdown {} seconds.", hour, totalSeconds);
                return;
            }
        }
    }

    private static void processRestart(ServerWorld world) {
        int secondsLeft = getSecondsRemaining();
        MinecraftServer server = world.getServer();
        if (secondsLeft < 0) {
            performShutdown(server);
            return;
        }
        for (int threshold : WARNING_SECONDS) {
            if (secondsLeft <= threshold && lastWarningSent > threshold) {
                lastWarningSent = threshold;
                sendWarning(server, secondsLeft);
                break;
            }
        }
        if (secondsLeft <= 120 && !saveOffSent) {
            saveOffSent = true;
            server.getCommands().performCommand(server.createCommandSourceStack(), "save-off");
            ThaumicWards.LOGGER.info("Auto-save disabled for restart.");
        }
        if (secondsLeft <= 90 && !savePending) {
            savePending = true;
            server.getCommands().performCommand(server.createCommandSourceStack(), "save-all flush");
            ThaumicWards.LOGGER.info("World save forced before restart.");
        }
        if (secondsLeft <= 0) {
            performShutdown(server);
        }
    }

    private static void sendWarning(MinecraftServer server, int secondsLeft) {
        String timeStr = formatTime(secondsLeft);
        IFormattableTextComponent message;
        if (secondsLeft > 120) {
            message = new StringTextComponent("")
                .append(new StringTextComponent("[Arcane Nexus] ").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD))
                .append(new StringTextComponent("The ley lines require realignment in ").withStyle(TextFormatting.YELLOW))
                .append(new StringTextComponent(timeStr).withStyle(TextFormatting.RED, TextFormatting.BOLD))
                .append(new StringTextComponent(". Secure your belongings.").withStyle(TextFormatting.YELLOW));
        } else if (secondsLeft > 30) {
            message = new StringTextComponent("")
                .append(new StringTextComponent("[Arcane Nexus] ").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD))
                .append(new StringTextComponent("Server restart in ").withStyle(TextFormatting.RED))
                .append(new StringTextComponent(timeStr).withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD))
                .append(new StringTextComponent("! Close GUIs and find safety!").withStyle(TextFormatting.RED));
        } else {
            message = new StringTextComponent("")
                .append(new StringTextComponent("[Arcane Nexus] ").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD))
                .append(new StringTextComponent("RESTARTING IN ").withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD))
                .append(new StringTextComponent(timeStr).withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD, TextFormatting.UNDERLINE));
        }
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, false);
            if (secondsLeft <= 60) {
                player.displayClientMessage(
                    new StringTextComponent("SERVER RESTART: " + timeStr)
                        .withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD),
                    true);
            }
            if (secondsLeft <= 5) {
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 0.5f);
            } else if (secondsLeft <= 30) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL, SoundCategory.MASTER, 1.0f, 0.6f);
            } else {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.8f, 1.0f);
            }
        }
        ThaumicWards.LOGGER.info("Restart warning: {} remaining.", timeStr);
    }

    private static void performShutdown(MinecraftServer server) {
        ThaumicWards.LOGGER.info("=== AUTO-RESTART: Stopping server ===");
        IFormattableTextComponent finalMessage = new StringTextComponent("")
            .append(new StringTextComponent("[Arcane Nexus] ").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD))
            .append(new StringTextComponent("The ley lines are realigning. Reconnect shortly...")
                .withStyle(TextFormatting.LIGHT_PURPLE));
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(finalMessage, false);
            player.connection.disconnect(
                new StringTextComponent("Server is restarting. Please reconnect in ~90 seconds.")
                    .withStyle(TextFormatting.GOLD));
        }
        server.halt(false);
    }

    private static String formatTime(int totalSeconds) {
        if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            if (seconds == 0) {
                return minutes + (minutes != 1 ? " minutes" : " minute");
            }
            return minutes + "m " + seconds + "s";
        }
        return totalSeconds + (totalSeconds != 1 ? " seconds" : " second");
    }
}

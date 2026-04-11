package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;

public class AdaptiveThrottler {

    public enum ThrottleLevel { NONE, LIGHT, MODERATE, SEVERE }

    private static final double TPS_LIGHT = 18.0, TPS_MODERATE = 15.0, TPS_SEVERE = 12.0, TPS_RECOVERY = 19.0;
    private static ThrottleLevel currentLevel = ThrottleLevel.NONE;
    private static int recoveryStreak = 0;
    private static final int RECOVERY_CYCLES = 3;

    private static boolean baselinesCaptured = false;
    private static int baseTickInterval, baseThreshold, baseScoreboardInterval;
    private static int overrideTickInterval = -1, overrideThreshold = -1;
    private static int overrideScoreboardInterval = -1, overrideParticleInterval = -1;

    public static void tick() {
        if (!ServerConfig.ADAPTIVE_THROTTLE_ENABLED.get()) {
            if (currentLevel != ThrottleLevel.NONE) { restoreBaselines(); currentLevel = ThrottleLevel.NONE; }
            return;
        }
        double tps = TPSMonitor.getCurrentTPS();
        ThrottleLevel desired = tps < TPS_SEVERE ? ThrottleLevel.SEVERE :
                tps < TPS_MODERATE ? ThrottleLevel.MODERATE :
                tps < TPS_LIGHT ? ThrottleLevel.LIGHT : ThrottleLevel.NONE;

        if (desired.ordinal() > currentLevel.ordinal()) {
            recoveryStreak = 0;
            applyLevel(desired, tps);
        } else if (desired.ordinal() < currentLevel.ordinal() && tps >= TPS_RECOVERY) {
            if (++recoveryStreak >= RECOVERY_CYCLES) {
                stepDown(tps);
                recoveryStreak = 0;
            }
        } else if (tps < TPS_RECOVERY) {
            recoveryStreak = 0;
        }
    }

    public static ThrottleLevel getCurrentLevel() { return currentLevel; }

    public static int getEffectiveDistantTickInterval() {
        return overrideTickInterval > 0 ? overrideTickInterval : ServerConfig.DISTANT_CHUNK_TICK_INTERVAL.get();
    }

    public static int getEffectiveDistantChunkThreshold() {
        return overrideThreshold > 0 ? overrideThreshold : ServerConfig.DISTANT_CHUNK_THRESHOLD.get();
    }

    public static int getEffectiveScoreboardInterval() {
        return overrideScoreboardInterval > 0 ? overrideScoreboardInterval : ServerConfig.SCOREBOARD_UPDATE_INTERVAL_TICKS.get();
    }

    public static int getEffectiveClaimParticleInterval() {
        return overrideParticleInterval > 0 ? overrideParticleInterval : 40;
    }

    private static void captureBaselines() {
        if (!baselinesCaptured) {
            baseTickInterval = ServerConfig.DISTANT_CHUNK_TICK_INTERVAL.get();
            baseThreshold = ServerConfig.DISTANT_CHUNK_THRESHOLD.get();
            baseScoreboardInterval = ServerConfig.SCOREBOARD_UPDATE_INTERVAL_TICKS.get();
            baselinesCaptured = true;
        }
    }

    private static void applyLevel(ThrottleLevel level, double tps) {
        captureBaselines();
        currentLevel = level;
        switch (level) {
            case LIGHT:
                overrideTickInterval = baseTickInterval * 2;
                overrideThreshold = -1; overrideScoreboardInterval = -1; overrideParticleInterval = -1;
                ThaumicWards.LOGGER.warn("[AdaptiveThrottler] TPS={} — LIGHT: tick interval {}->{}",
                        String.format("%.1f", tps), baseTickInterval, overrideTickInterval);
                break;
            case MODERATE:
                overrideTickInterval = baseTickInterval * 4;
                overrideThreshold = Math.max(1, baseThreshold - 4);
                overrideScoreboardInterval = -1; overrideParticleInterval = -1;
                ThaumicWards.LOGGER.warn("[AdaptiveThrottler] TPS={} — MODERATE: tick interval {}->{}," +
                        " threshold {}->{}",
                        String.format("%.1f", tps), baseTickInterval, overrideTickInterval,
                        baseThreshold, overrideThreshold);
                break;
            case SEVERE:
                overrideTickInterval = baseTickInterval * 6;
                overrideThreshold = Math.max(1, baseThreshold - 6);
                overrideScoreboardInterval = baseScoreboardInterval * 3;
                overrideParticleInterval = 120;
                ThaumicWards.LOGGER.warn("[AdaptiveThrottler] TPS={} — SEVERE: all throttles engaged",
                        String.format("%.1f", tps));
                break;
            default: break;
        }
    }

    private static void stepDown(double tps) {
        ThrottleLevel next = ThrottleLevel.values()[currentLevel.ordinal() - 1];
        if (next == ThrottleLevel.NONE) {
            restoreBaselines(); currentLevel = ThrottleLevel.NONE;
            ThaumicWards.LOGGER.info("[AdaptiveThrottler] TPS={} — recovered, baselines restored",
                    String.format("%.1f", tps));
        } else {
            applyLevel(next, tps);
        }
    }

    private static void restoreBaselines() {
        overrideTickInterval = -1; overrideThreshold = -1;
        overrideScoreboardInterval = -1; overrideParticleInterval = -1;
    }

    public static void reset() {
        if (currentLevel != ThrottleLevel.NONE) restoreBaselines();
        currentLevel = ThrottleLevel.NONE; recoveryStreak = 0; baselinesCaptured = false;
    }
}

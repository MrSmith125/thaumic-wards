package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;

public class AdaptiveThrottler {

    public enum ThrottleLevel { NONE, LIGHT, MODERATE, SEVERE }

    // Kick in earlier: LIGHT at <19, MODERATE at <17, SEVERE at <14 (was 18/15/12).
    // Recovery requires sustained TPS≥19.5 for 5 consecutive checks (was ≥19, 3 checks)
    // to avoid thrashing when TPS hovers just above a boundary.
    private static final double TPS_LIGHT = 19.0, TPS_MODERATE = 17.0, TPS_SEVERE = 14.0, TPS_RECOVERY = 19.5;
    private static ThrottleLevel currentLevel = ThrottleLevel.NONE;
    private static int recoveryStreak = 0;
    private static final int RECOVERY_CYCLES = 5;

    private static boolean baselinesCaptured = false;
    private static int baseTickInterval, baseThreshold, baseScoreboardInterval, baseBuffInterval;
    private static int overrideTickInterval = -1, overrideThreshold = -1;
    private static int overrideScoreboardInterval = -1, overrideParticleInterval = -1;
    private static int overrideBuffInterval = -1;

    // Dynamic view distance management
    private static net.minecraft.server.MinecraftServer serverRef;
    private static int baseViewDistance = -1;

    /** Store a reference to the server for dynamic view distance. Call from onServerStarting. */
    public static void setServer(net.minecraft.server.MinecraftServer server) {
        serverRef = server;
        baseViewDistance = server.getPlayerList().getViewDistance();
    }

    /**
     * Returns the spawn cancel probability for the current throttle level.
     * 0.0 = allow all spawns, 1.0 = cancel all spawns.
     * Hostile and passive use different rates.
     */
    public static double getSpawnCancelChance(boolean isPassive) {
        switch (currentLevel) {
            case LIGHT:    return isPassive ? 0.25 : 0.0;   // 25% passive deny, hostiles untouched
            case MODERATE: return isPassive ? 0.50 : 0.30;  // 50% passive, 30% hostile deny
            case SEVERE:   return isPassive ? 0.75 : 0.50;  // 75% passive, 50% hostile deny
            default:       return 0.0;
        }
    }

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

    public static int getEffectiveBuffInterval() {
        return overrideBuffInterval > 0 ? overrideBuffInterval : com.thaumicwards.config.ServerConfig.BUFF_APPLICATION_INTERVAL_TICKS.get();
    }

    private static void captureBaselines() {
        if (!baselinesCaptured) {
            baseTickInterval = ServerConfig.DISTANT_CHUNK_TICK_INTERVAL.get();
            baseThreshold = ServerConfig.DISTANT_CHUNK_THRESHOLD.get();
            baseScoreboardInterval = ServerConfig.SCOREBOARD_UPDATE_INTERVAL_TICKS.get();
            baseBuffInterval = ServerConfig.BUFF_APPLICATION_INTERVAL_TICKS.get();
            baselinesCaptured = true;
        }
    }

    private static void applyLevel(ThrottleLevel level, double tps) {
        captureBaselines();
        currentLevel = level;
        switch (level) {
            case LIGHT:
                // Distant-chunk entity ticks at 3x base, scoreboard slows 2x, particles slightly slower
                overrideTickInterval = baseTickInterval * 3;
                overrideThreshold = Math.max(1, baseThreshold - 2);
                overrideScoreboardInterval = baseScoreboardInterval * 2;
                overrideParticleInterval = 60;
                overrideBuffInterval = -1;
                // View distance unchanged at LIGHT
                ThaumicWards.LOGGER.warn("[AdaptiveThrottler] TPS={} — LIGHT: tick interval {}->{}, threshold {}->{}, scoreboard {}->{}",
                        String.format("%.1f", tps), baseTickInterval, overrideTickInterval,
                        baseThreshold, overrideThreshold, baseScoreboardInterval, overrideScoreboardInterval);
                break;
            case MODERATE:
                // 5x entity tick interval, tighter near-player radius, scoreboard 3x, particles every 80 ticks
                overrideTickInterval = baseTickInterval * 5;
                overrideThreshold = Math.max(1, baseThreshold - 4);
                overrideScoreboardInterval = baseScoreboardInterval * 3;
                overrideParticleInterval = 80;
                overrideBuffInterval = baseBuffInterval * 2;
                // Drop view distance by 2 under MODERATE load
                setViewDistance(Math.max(3, baseViewDistance - 2));
                ThaumicWards.LOGGER.warn("[AdaptiveThrottler] TPS={} — MODERATE: tick interval {}->{}, threshold {}->{}, scoreboard {}->{}",
                        String.format("%.1f", tps), baseTickInterval, overrideTickInterval,
                        baseThreshold, overrideThreshold, baseScoreboardInterval, overrideScoreboardInterval);
                break;
            case SEVERE:
                // 8x entity tick interval, minimal near-player radius, scoreboard 6x, particles every 160 ticks,
                // faction buffs at 3x to reduce per-tick work
                overrideTickInterval = baseTickInterval * 8;
                overrideThreshold = Math.max(1, baseThreshold - 5);
                overrideScoreboardInterval = baseScoreboardInterval * 6;
                overrideParticleInterval = 160;
                overrideBuffInterval = baseBuffInterval * 3;
                // Drop view distance by 4 under SEVERE load (minimum 3)
                setViewDistance(Math.max(3, baseViewDistance - 4));
                ThaumicWards.LOGGER.warn("[AdaptiveThrottler] TPS={} — SEVERE: all throttles maximally engaged" +
                        " (tick *8, threshold -{}, scoreboard *6, particles every 160t, buffs *3)",
                        String.format("%.1f", tps), 5);
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
        overrideBuffInterval = -1;
        // Restore view distance
        if (baseViewDistance > 0) setViewDistance(baseViewDistance);
    }

    private static void setViewDistance(int vd) {
        if (serverRef != null && serverRef.getPlayerList() != null) {
            int current = serverRef.getPlayerList().getViewDistance();
            if (current != vd) {
                serverRef.getPlayerList().setViewDistance(vd);
                ThaumicWards.LOGGER.info("[AdaptiveThrottler] View distance changed: {} -> {}", current, vd);
            }
        }
    }

    public static void reset() {
        if (currentLevel != ThrottleLevel.NONE) restoreBaselines();
        currentLevel = ThrottleLevel.NONE; recoveryStreak = 0; baselinesCaptured = false;
    }
}

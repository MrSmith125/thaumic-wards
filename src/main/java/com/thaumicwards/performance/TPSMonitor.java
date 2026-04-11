package com.thaumicwards.performance;

import com.thaumicwards.core.ThaumicWards;

public class TPSMonitor {

    private static final int WINDOW_SIZE = 100;
    private static final long[] tickTimes = new long[WINDOW_SIZE];
    private static int tickIndex = 0;
    private static long lastTickTime = 0;
    private static boolean filled = false;
    private static int lowTpsWarningCooldown = 0;

    public static void recordTick() {
        long now = System.nanoTime();
        if (lastTickTime != 0) {
            tickTimes[tickIndex] = now - lastTickTime;
            tickIndex = (tickIndex + 1) % WINDOW_SIZE;
            if (tickIndex == 0) filled = true;
        }
        lastTickTime = now;

        // Warn when TPS drops below 15
        if (lowTpsWarningCooldown > 0) {
            lowTpsWarningCooldown--;
        } else {
            double tps = getCurrentTPS();
            if (tps > 0 && tps < 15.0) {
                ThaumicWards.LOGGER.warn("Server TPS is low: {}", String.format("%.1f", tps));
                lowTpsWarningCooldown = 1200; // Don't warn again for 1 minute
            }
        }
    }

    public static double getCurrentTPS() {
        int count = filled ? WINDOW_SIZE : tickIndex;
        if (count == 0) return 20.0;

        long total = 0;
        for (int i = 0; i < count; i++) {
            total += tickTimes[i];
        }

        double avgNanos = (double) total / count;
        if (avgNanos <= 0) return 20.0;

        double tps = 1_000_000_000.0 / avgNanos;
        return Math.min(tps, 20.0);
    }

    /**
     * Returns average milliseconds per tick interval (lower is better, 50ms = 20 TPS).
     */
    public static double getAverageTickMs() {
        int count = filled ? WINDOW_SIZE : tickIndex;
        if (count == 0) return 0;

        long total = 0;
        for (int i = 0; i < count; i++) {
            total += tickTimes[i];
        }

        return (double) total / count / 1_000_000.0;
    }

    public static void reset() {
        tickIndex = 0;
        lastTickTime = 0;
        filled = false;
        lowTpsWarningCooldown = 0;
    }
}

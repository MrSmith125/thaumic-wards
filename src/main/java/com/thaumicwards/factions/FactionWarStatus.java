package com.thaumicwards.factions;

import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.core.ThaumicWards;

/**
 * Calculates which faction is winning the war based on kills and outpost control.
 * Recalculated periodically by ServerTickHandler.
 */
public class FactionWarStatus {

    private static String winningFactionId = null;
    private static int mysticScore = 0;
    private static int crimsonScore = 0;
    private static long lastCalculatedAt = 0;

    /**
     * Recalculates war scores and determines the winning faction.
     */
    public static void recalculate() {
        int killWeight = ServerConfig.KILL_SCORE_WEIGHT.get();
        int outpostWeight = ServerConfig.OUTPOST_SCORE_WEIGHT.get();
        int margin = ServerConfig.WINNING_MARGIN.get();

        // Calculate scores
        int mysticKills = FactionKillTracker.getFactionTotalKills(Faction.MYSTICS_STRING_ID);
        int crimsonKills = FactionKillTracker.getFactionTotalKills(Faction.CRIMSONS_STRING_ID);

        // Count outposts
        Faction mystics = FactionManager.getMystics();
        Faction crimsons = FactionManager.getCrimsons();

        int mysticOutposts = 0;
        int crimsonOutposts = 0;

        if (mystics != null && crimsons != null) {
            for (ClaimData claim : ClaimManager.getAllClaims().values()) {
                if (claim.isOutpost() && claim.getFactionId() != null) {
                    if (claim.getFactionId().equals(mystics.getFactionId())) {
                        mysticOutposts++;
                    } else if (claim.getFactionId().equals(crimsons.getFactionId())) {
                        crimsonOutposts++;
                    }
                }
            }
        }

        mysticScore = (mysticKills * killWeight) + (mysticOutposts * outpostWeight);
        crimsonScore = (crimsonKills * killWeight) + (crimsonOutposts * outpostWeight);

        // Determine winner with margin
        String oldWinner = winningFactionId;
        if (mysticScore > crimsonScore + margin) {
            winningFactionId = Faction.MYSTICS_STRING_ID;
        } else if (crimsonScore > mysticScore + margin) {
            winningFactionId = Faction.CRIMSONS_STRING_ID;
        } else {
            winningFactionId = null; // Tied or within margin
        }

        lastCalculatedAt = System.currentTimeMillis();

        // Log if winner changed
        if (oldWinner == null && winningFactionId != null) {
            ThaumicWards.LOGGER.info("War status changed: {} is now winning!", winningFactionId);
        } else if (oldWinner != null && !oldWinner.equals(winningFactionId)) {
            ThaumicWards.LOGGER.info("War status changed: {} -> {}",
                    oldWinner, winningFactionId != null ? winningFactionId : "tied");
        }
    }

    // --- Getters ---

    public static String getWinningFactionId() { return winningFactionId; }
    public static int getMysticScore() { return mysticScore; }
    public static int getCrimsonScore() { return crimsonScore; }
    public static long getLastCalculatedAt() { return lastCalculatedAt; }

    public static boolean isWinning(String factionStringId) {
        return factionStringId != null && factionStringId.equals(winningFactionId);
    }

    public static void reset() {
        winningFactionId = null;
        mysticScore = 0;
        crimsonScore = 0;
        lastCalculatedAt = 0;
    }
}

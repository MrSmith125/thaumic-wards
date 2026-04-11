package com.thaumicwards.scoreboard;

import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.core.ThaumicWards;
import com.thaumicwards.factions.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.scoreboard.ScoreCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.HashSet;
import java.util.Set;

public class FactionScoreboard {

    private static final String OBJECTIVE_NAME = "tw_war";
    private static final String MYSTICS_TEAM = "tw_mystics";
    private static final String CRIMSONS_TEAM = "tw_crimsons";

    private static MinecraftServer server;
    private static final Set<String> previousLines = new HashSet<>();

    public static void init(MinecraftServer srv) {
        server = srv;
        ServerScoreboard scoreboard = srv.getScoreboard();

        // Create or get teams for colored names
        createTeam(scoreboard, MYSTICS_TEAM, "The Mystics", TextFormatting.BLUE);
        createTeam(scoreboard, CRIMSONS_TEAM, "The Crimsons", TextFormatting.RED);

        // Create sidebar objective
        ScoreObjective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_NAME,
                    ScoreCriteria.DUMMY,
                    new StringTextComponent("Eternal War").withStyle(TextFormatting.DARK_PURPLE, TextFormatting.BOLD),
                    ScoreCriteria.RenderType.INTEGER);
        }
        scoreboard.setDisplayObjective(1, objective); // 1 = sidebar

        // Initial update
        updateSidebar(srv);

        ThaumicWards.LOGGER.info("Faction scoreboard initialized.");
    }

    private static void createTeam(ServerScoreboard scoreboard, String name, String displayName, TextFormatting color) {
        ScorePlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
            team.setColor(color);
            team.setPlayerPrefix(new StringTextComponent("[" + displayName + "] ").withStyle(color));
        }
    }

    public static void assignPlayerTeam(ServerPlayerEntity player, Faction faction) {
        if (server == null) return;
        ServerScoreboard scoreboard = server.getScoreboard();

        String teamName;
        if (Faction.MYSTICS_STRING_ID.equals(faction.getStringId())) {
            teamName = MYSTICS_TEAM;
        } else {
            teamName = CRIMSONS_TEAM;
        }

        ScorePlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team != null) {
            scoreboard.addPlayerToTeam(player.getName().getString(), team);
        }
    }

    public static void removePlayerTeam(ServerPlayerEntity player) {
        if (server == null) return;
        ServerScoreboard scoreboard = server.getScoreboard();
        scoreboard.removePlayerFromTeam(player.getName().getString());
    }

    public static void updateSidebar(MinecraftServer srv) {
        if (srv == null) return;
        ServerScoreboard scoreboard = srv.getScoreboard();
        ScoreObjective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) return;

        // Clear all previous line entries
        for (String line : previousLines) {
            scoreboard.resetPlayerScore(line, objective);
        }
        previousLines.clear();

        // Get data
        Faction mystics = FactionManager.getMystics();
        Faction crimsons = FactionManager.getCrimsons();

        int mysticKills = mystics != null ? FactionKillTracker.getFactionTotalKills(mystics.getStringId()) : 0;
        int crimsonKills = crimsons != null ? FactionKillTracker.getFactionTotalKills(crimsons.getStringId()) : 0;

        int mysticOutposts = mystics != null ? countOutposts(mystics) : 0;
        int crimsonOutposts = crimsons != null ? countOutposts(crimsons) : 0;

        String leading;
        if (mysticKills > crimsonKills) {
            leading = "\u00a79The Mystics";
        } else if (crimsonKills > mysticKills) {
            leading = "\u00a7cThe Crimsons";
        } else {
            leading = "\u00a77Tied";
        }

        // Set lines (score = display order, higher = higher on screen)
        setLine(scoreboard, objective, "\u00a7d\u00a7l=== Eternal War ===", 10);
        setLine(scoreboard, objective, " ", 9); // blank
        setLine(scoreboard, objective, "\u00a79Mystics: \u00a7f" + mysticKills + " kills", 8);
        setLine(scoreboard, objective, "\u00a7cCrimsons: \u00a7f" + crimsonKills + " kills", 7);
        setLine(scoreboard, objective, "  ", 6); // blank
        setLine(scoreboard, objective, "\u00a7eLeading: " + leading, 5);
        setLine(scoreboard, objective, "\u00a77Outposts: \u00a79M:" + mysticOutposts + " \u00a7cC:" + crimsonOutposts, 4);
    }

    private static int countOutposts(Faction faction) {
        return (int) ClaimManager.getFactionClaims(faction.getFactionId()).stream()
                .filter(c -> c.isOutpost())
                .count();
    }

    private static void setLine(ServerScoreboard scoreboard, ScoreObjective objective, String text, int score) {
        scoreboard.getOrCreatePlayerScore(text, objective).setScore(score);
        previousLines.add(text);
    }

    public static void reset() {
        if (server != null) {
            ServerScoreboard scoreboard = server.getScoreboard();
            ScoreObjective objective = scoreboard.getObjective(OBJECTIVE_NAME);
            if (objective != null) {
                scoreboard.removeObjective(objective);
            }
        }
        previousLines.clear();
        server = null;
    }
}

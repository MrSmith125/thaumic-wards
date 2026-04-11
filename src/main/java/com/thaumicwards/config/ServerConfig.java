package com.thaumicwards.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {

    public static final ForgeConfigSpec SPEC;

    // Performance
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHUNK_LOAD_OPTIMIZATION;
    public static final ForgeConfigSpec.IntValue MAX_CHUNK_LOADS_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue ENTITY_TICK_REDUCTION_FACTOR;
    public static final ForgeConfigSpec.IntValue DISTANT_CHUNK_TICK_INTERVAL;
    public static final ForgeConfigSpec.IntValue DISTANT_CHUNK_THRESHOLD;

    // Border
    public static final ForgeConfigSpec.IntValue BORDER_WARNING_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue BORDER_DAMAGE;

    // Claims — Guild
    public static final ForgeConfigSpec.IntValue MAX_GUILD_HALL_CLAIMS;
    public static final ForgeConfigSpec.IntValue CLAIMS_PER_FACTION_MEMBER;
    public static final ForgeConfigSpec.IntValue CLAIM_EXPIRE_AFTER_DAYS;

    // Claims — Per-Rank Personal Limits
    public static final ForgeConfigSpec.IntValue CLAIMS_INITIATE;
    public static final ForgeConfigSpec.IntValue CLAIMS_ACOLYTE;
    public static final ForgeConfigSpec.IntValue CLAIMS_WARLOCK;
    public static final ForgeConfigSpec.IntValue CLAIMS_ARCHMAGE;
    public static final ForgeConfigSpec.IntValue CLAIMS_LEADER;

    // Outposts (Raid System)
    public static final ForgeConfigSpec.IntValue OUTPOST_HEALTH;
    public static final ForgeConfigSpec.IntValue OUTPOST_DAMAGE_PER_HIT;
    public static final ForgeConfigSpec.IntValue OUTPOST_HIT_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue OUTPOST_RECAPTURE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.BooleanValue RAID_WINDOW_ENABLED;
    public static final ForgeConfigSpec.IntValue RAID_WINDOW_START_HOUR;
    public static final ForgeConfigSpec.IntValue RAID_WINDOW_END_HOUR;

    // Factions
    public static final ForgeConfigSpec.IntValue MAX_FACTION_MEMBERS;

    // War Status & Buffs
    public static final ForgeConfigSpec.IntValue BUFF_RECALCULATION_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue BUFF_APPLICATION_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue KILL_SCORE_WEIGHT;
    public static final ForgeConfigSpec.IntValue OUTPOST_SCORE_WEIGHT;
    public static final ForgeConfigSpec.IntValue WINNING_MARGIN;
    public static final ForgeConfigSpec.IntValue CONTESTED_ZONE_KILL_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue BUFF_SPEED_ENABLED;
    public static final ForgeConfigSpec.IntValue BUFF_XP_BONUS_PERCENT;

    // Progression
    public static final ForgeConfigSpec.IntValue ARCANE_POWER_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue ARCANE_POWER_PER_KILL;
    public static final ForgeConfigSpec.LongValue ACOLYTE_THRESHOLD;
    public static final ForgeConfigSpec.LongValue WARLOCK_THRESHOLD;

    // Gamification
    public static final ForgeConfigSpec.IntValue SCOREBOARD_UPDATE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue CHAT_FACTION_PREFIX_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTO_JOIN_ENABLED;

    // Redstone Throttle
    public static final ForgeConfigSpec.BooleanValue ENABLE_REDSTONE_THROTTLE;
    public static final ForgeConfigSpec.IntValue REDSTONE_UPDATES_PER_CHUNK_PER_TICK;

    // Adaptive Throttle
    public static final ForgeConfigSpec.BooleanValue ADAPTIVE_THROTTLE_ENABLED;

    // Entity Cleanup
    public static final ForgeConfigSpec.BooleanValue ENTITY_CLEANUP_ENABLED;
    public static final ForgeConfigSpec.IntValue ENTITY_CLEANUP_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENTITY_CLEANUP_ITEMS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ENTITY_CLEANUP_XP_ENABLED;
    public static final ForgeConfigSpec.IntValue ENTITY_CLEANUP_ITEM_AGE_TICKS;
    public static final ForgeConfigSpec.IntValue ENTITY_CLEANUP_XP_AGE_TICKS;
    public static final ForgeConfigSpec.IntValue ENTITY_CLEANUP_WARN_THRESHOLD;

    // AutoOptimizer
    public static final ForgeConfigSpec.BooleanValue AUTO_OPTIMIZE_ENABLED;

    // Profiler
    public static final ForgeConfigSpec.BooleanValue PROFILER_AUTO_START;
    public static final ForgeConfigSpec.IntValue PROFILER_AUTO_LOG_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue PROFILER_SNAPSHOT_INTERVAL_TICKS;

    // Auto-Restart
    public static final ForgeConfigSpec.BooleanValue AUTO_RESTART_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> RESTART_HOURS;
    public static final ForgeConfigSpec.IntValue RESTART_WARNING_MINUTES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Performance Optimization Settings").push("performance");
        ENABLE_CHUNK_LOAD_OPTIMIZATION = builder
                .comment("Enable chunk load optimization to reduce unnecessary chunk loading")
                .define("enableChunkLoadOptimization", true);
        MAX_CHUNK_LOADS_PER_TICK = builder
                .comment("Maximum number of chunk loads allowed per server tick")
                .defineInRange("maxChunkLoadsPerTick", 4, 1, 64);
        ENTITY_TICK_REDUCTION_FACTOR = builder
                .comment("Fraction of entities ticked in unoccupied distant chunks (0.0 = none, 1.0 = all)")
                .defineInRange("entityTickReductionFactor", 0.5, 0.0, 1.0);
        DISTANT_CHUNK_TICK_INTERVAL = builder
                .comment("Ticks between entity updates in distant chunks (higher = less frequent)")
                .defineInRange("distantChunkTickInterval", 20, 1, 200);
        DISTANT_CHUNK_THRESHOLD = builder
                .comment("Chunk distance from nearest player to be considered 'distant'")
                .defineInRange("distantChunkThreshold", 8, 1, 32);
        ENABLE_REDSTONE_THROTTLE = builder
                .comment("Enable per-chunk redstone update throttling to prevent lag machines")
                .define("enableRedstoneThrottle", true);
        REDSTONE_UPDATES_PER_CHUNK_PER_TICK = builder
                .comment("Maximum redstone neighbor-notify updates allowed per chunk per tick")
                .defineInRange("redstoneUpdatesPerChunkPerTick", 64, 1, 1024);
        ADAPTIVE_THROTTLE_ENABLED = builder
                .comment("Enable adaptive TPS-based throttling that auto-adjusts performance when TPS drops")
                .define("adaptiveThrottleEnabled", true);
        builder.pop();

        builder.comment("Entity Cleanup Settings").push("entityCleanup");
        ENTITY_CLEANUP_ENABLED = builder.comment("Enable automatic periodic entity cleanup")
                .define("entityCleanupEnabled", true);
        ENTITY_CLEANUP_INTERVAL_TICKS = builder.comment("Ticks between cleanups (6000 = 5 minutes)")
                .defineInRange("entityCleanupIntervalTicks", 6000, 1200, 72000);
        ENTITY_CLEANUP_ITEMS_ENABLED = builder.comment("Remove old ground items")
                .define("cleanupItemsEnabled", true);
        ENTITY_CLEANUP_XP_ENABLED = builder.comment("Remove old XP orbs")
                .define("cleanupXpEnabled", true);
        ENTITY_CLEANUP_ITEM_AGE_TICKS = builder.comment("Item age before removal (6000 = 5 min)")
                .defineInRange("itemAgeTicks", 6000, 600, 72000);
        ENTITY_CLEANUP_XP_AGE_TICKS = builder.comment("XP orb age before removal (3600 = 3 min)")
                .defineInRange("xpAgeTicks", 3600, 600, 72000);
        ENTITY_CLEANUP_WARN_THRESHOLD = builder.comment("Warn if any entity type exceeds this count")
                .defineInRange("warnThreshold", 100, 10, 10000);
        builder.pop();

        builder.comment("World Border Settings").push("border");
        BORDER_WARNING_DISTANCE = builder
                .comment("Distance in blocks from the border where warning particles appear")
                .defineInRange("borderWarningDistance", 16, 1, 128);
        BORDER_DAMAGE = builder
                .comment("Damage per second dealt to players outside the border")
                .defineInRange("borderDamage", 1.0, 0.0, 20.0);
        builder.pop();

        builder.comment("Area Claiming Settings").push("claims");
        MAX_GUILD_HALL_CLAIMS = builder
                .comment("Base maximum number of chunks a faction guild hall can claim")
                .defineInRange("maxGuildHallClaims", 25, 1, 1024);
        CLAIMS_PER_FACTION_MEMBER = builder
                .comment("Bonus guild hall claim chunks granted per faction member")
                .defineInRange("claimsPerFactionMember", 3, 0, 50);
        CLAIM_EXPIRE_AFTER_DAYS = builder
                .comment("Days of owner inactivity before a claim expires (0 = never)")
                .defineInRange("claimExpireAfterDays", 30, 0, 365);

        builder.comment("Per-rank personal claim limits").push("rankClaims");
        CLAIMS_INITIATE = builder
                .comment("Max personal claims for Initiate rank")
                .defineInRange("claimsInitiate", 1, 1, 50);
        CLAIMS_ACOLYTE = builder
                .comment("Max personal claims for Acolyte rank")
                .defineInRange("claimsAcolyte", 3, 1, 50);
        CLAIMS_WARLOCK = builder
                .comment("Max personal claims for Warlock rank")
                .defineInRange("claimsWarlock", 6, 1, 50);
        CLAIMS_ARCHMAGE = builder
                .comment("Max personal claims for Archmage rank")
                .defineInRange("claimsArchmage", 10, 1, 100);
        CLAIMS_LEADER = builder
                .comment("Max personal claims for Leader rank")
                .defineInRange("claimsLeader", 10, 1, 100);
        builder.pop(); // rankClaims
        builder.pop(); // claims

        builder.comment("Outpost / Raid Settings").push("outposts");
        OUTPOST_HEALTH = builder
                .comment("Health points for outpost blocks")
                .defineInRange("outpostHealth", 100, 10, 1000);
        OUTPOST_DAMAGE_PER_HIT = builder
                .comment("Damage dealt per enemy attack on an outpost")
                .defineInRange("outpostDamagePerHit", 5, 1, 100);
        OUTPOST_HIT_COOLDOWN_SECONDS = builder
                .comment("Cooldown between attacks on an outpost per player (seconds)")
                .defineInRange("outpostHitCooldownSeconds", 3, 1, 60);
        OUTPOST_RECAPTURE_COOLDOWN_MINUTES = builder
                .comment("Cooldown before a captured outpost can be raided again (minutes)")
                .defineInRange("outpostRecaptureCooldownMinutes", 30, 0, 1440);
        RAID_WINDOW_ENABLED = builder
                .comment("Enable raid windows (outposts can only be attacked during certain hours)")
                .define("raidWindowEnabled", false);
        RAID_WINDOW_START_HOUR = builder
                .comment("Start hour for raid window (24-hour format)")
                .defineInRange("raidWindowStartHour", 18, 0, 23);
        RAID_WINDOW_END_HOUR = builder
                .comment("End hour for raid window (24-hour format)")
                .defineInRange("raidWindowEndHour", 22, 0, 24);
        builder.pop();

        builder.comment("Faction Settings").push("factions");
        MAX_FACTION_MEMBERS = builder
                .comment("Maximum number of members in a single faction")
                .defineInRange("maxFactionMembers", 200, 2, 1000);
        builder.pop();

        builder.comment("War Status, Buffs & Contested Zones").push("war");
        BUFF_RECALCULATION_INTERVAL_TICKS = builder
                .comment("Ticks between war status recalculations (72000 = 1 hour)")
                .defineInRange("buffRecalculationIntervalTicks", 72000, 1200, 1728000);
        BUFF_APPLICATION_INTERVAL_TICKS = builder
                .comment("Ticks between buff applications (600 = 30 seconds)")
                .defineInRange("buffApplicationIntervalTicks", 600, 100, 72000);
        KILL_SCORE_WEIGHT = builder
                .comment("Score weight per faction kill in war calculation")
                .defineInRange("killScoreWeight", 1, 0, 100);
        OUTPOST_SCORE_WEIGHT = builder
                .comment("Score weight per controlled outpost in war calculation")
                .defineInRange("outpostScoreWeight", 10, 0, 1000);
        WINNING_MARGIN = builder
                .comment("Score margin needed to be considered 'winning' and receive buffs")
                .defineInRange("winningMargin", 50, 0, 10000);
        CONTESTED_ZONE_KILL_MULTIPLIER = builder
                .comment("Arcane Power multiplier for kills inside contested zones")
                .defineInRange("contestedZoneKillMultiplier", 2, 1, 10);
        BUFF_SPEED_ENABLED = builder
                .comment("Enable Speed I buff for winning faction members")
                .define("buffSpeedEnabled", true);
        BUFF_XP_BONUS_PERCENT = builder
                .comment("XP bonus percentage for winning faction members")
                .defineInRange("buffXpBonusPercent", 10, 0, 100);
        builder.pop();

        builder.comment("Arcane Power Progression Settings").push("progression");
        ARCANE_POWER_PER_MINUTE = builder
                .comment("Arcane Power points awarded per minute of online playtime")
                .defineInRange("arcanePowerPerMinute", 1, 1, 100);
        ARCANE_POWER_PER_KILL = builder
                .comment("Arcane Power points awarded per enemy faction kill")
                .defineInRange("arcanePowerPerKill", 50, 1, 1000);
        ACOLYTE_THRESHOLD = builder
                .comment("Arcane Power required to auto-rank to Acolyte (default ~16 hours playtime)")
                .defineInRange("acolyteThreshold", 1000L, 1L, 1000000L);
        WARLOCK_THRESHOLD = builder
                .comment("Arcane Power required to auto-rank to Warlock (default ~83 hours or mix with kills)")
                .defineInRange("warlockThreshold", 5000L, 1L, 1000000L);
        builder.pop();

        builder.comment("Gamification Settings").push("gamification");
        SCOREBOARD_UPDATE_INTERVAL_TICKS = builder
                .comment("Ticks between sidebar scoreboard updates (600 = 30 seconds)")
                .defineInRange("scoreboardUpdateIntervalTicks", 600, 100, 6000);
        CHAT_FACTION_PREFIX_ENABLED = builder
                .comment("Enable faction and rank prefixes in chat messages")
                .define("chatFactionPrefixEnabled", true);
        AUTO_JOIN_ENABLED = builder
                .comment("Automatically assign players to a faction on first login")
                .define("autoJoinEnabled", true);
        builder.pop();

        builder.comment("AutoOptimizer - automatically tunes other mods' configs on startup").push("autoOptimizer");
        AUTO_OPTIMIZE_ENABLED = builder
                .comment("Enable automatic optimization of other mods' configs on server start")
                .define("autoOptimizeEnabled", true);
        builder.pop();

        builder.comment("Performance Profiler Settings").push("profiler");
        PROFILER_AUTO_START = builder
                .comment("Automatically start the performance profiler on server start")
                .define("profilerAutoStart", false);
        PROFILER_AUTO_LOG_INTERVAL_TICKS = builder
                .comment("Ticks between automatic profiler log dumps (6000 = 5 minutes)")
                .defineInRange("profilerAutoLogIntervalTicks", 6000, 1200, 72000);
        PROFILER_SNAPSHOT_INTERVAL_TICKS = builder
                .comment("Ticks between memory/packet snapshots (100 = 5 seconds)")
                .defineInRange("profilerSnapshotIntervalTicks", 100, 20, 1200);
        builder.pop();

        builder.comment("Auto-Restart Settings",
                "Combats memory leaks from Forge chunk cache, AE2 terminals,",
                "Valkyrien Skies physics objects, and entity NBT accumulation.",
                "Default schedule restarts every 6 hours at 04:00, 10:00, 16:00, 22:00.").push("autoRestart");
        AUTO_RESTART_ENABLED = builder
                .comment("Enable automatic scheduled server restarts")
                .define("autoRestartEnabled", true);
        RESTART_HOURS = builder
                .comment("Hours of day (0-23) when the server should restart.",
                         "Default: [4, 10, 16, 22] = every 6 hours, timed to clear memory before peak.")
                .defineList("restartHours", java.util.Arrays.asList(4, 10, 16, 22),
                        entry -> entry instanceof Integer && (Integer) entry >= 0 && (Integer) entry <= 23);
        RESTART_WARNING_MINUTES = builder
                .comment("Minutes before restart to begin warning players (default 15)")
                .defineInRange("restartWarningMinutes", 15, 1, 60);
        builder.pop();

        SPEC = builder.build();
    }
}

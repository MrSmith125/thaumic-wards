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

    // Pregen
    public static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_PREGEN_RADIUS;

    // Border
    public static final ForgeConfigSpec.IntValue BORDER_WARNING_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue BORDER_DAMAGE;

    // Claims
    public static final ForgeConfigSpec.IntValue MAX_PERSONAL_CLAIMS;
    public static final ForgeConfigSpec.IntValue MAX_GUILD_HALL_CLAIMS;
    public static final ForgeConfigSpec.IntValue CLAIMS_PER_FACTION_MEMBER;
    public static final ForgeConfigSpec.IntValue CLAIM_EXPIRE_AFTER_DAYS;

    // Factions
    public static final ForgeConfigSpec.IntValue MAX_FACTION_NAME_LENGTH;
    public static final ForgeConfigSpec.IntValue MAX_FACTION_MEMBERS;

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
        builder.pop();

        builder.comment("Chunk Pre-generation Settings").push("pregen");
        CHUNKS_PER_TICK = builder
                .comment("Number of chunks to generate per server tick during pre-generation")
                .defineInRange("chunksPerTick", 2, 1, 20);
        MAX_PREGEN_RADIUS = builder
                .comment("Maximum allowed pre-generation radius in chunks")
                .defineInRange("maxPregenRadius", 500, 1, 5000);
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
        MAX_PERSONAL_CLAIMS = builder
                .comment("Maximum number of chunks a single player can claim personally")
                .defineInRange("maxPersonalClaims", 9, 1, 256);
        MAX_GUILD_HALL_CLAIMS = builder
                .comment("Base maximum number of chunks a faction guild hall can claim")
                .defineInRange("maxGuildHallClaims", 25, 1, 1024);
        CLAIMS_PER_FACTION_MEMBER = builder
                .comment("Bonus guild hall claim chunks granted per faction member")
                .defineInRange("claimsPerFactionMember", 3, 0, 50);
        CLAIM_EXPIRE_AFTER_DAYS = builder
                .comment("Days of owner inactivity before a claim expires (0 = never)")
                .defineInRange("claimExpireAfterDays", 30, 0, 365);
        builder.pop();

        builder.comment("Faction Settings").push("factions");
        MAX_FACTION_NAME_LENGTH = builder
                .comment("Maximum length of a faction name")
                .defineInRange("maxFactionNameLength", 24, 3, 48);
        MAX_FACTION_MEMBERS = builder
                .comment("Maximum number of members in a single faction")
                .defineInRange("maxFactionMembers", 50, 2, 200);
        builder.pop();

        SPEC = builder.build();
    }
}

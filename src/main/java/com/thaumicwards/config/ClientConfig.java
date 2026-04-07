package com.thaumicwards.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_CLAIM_PARTICLES;
    public static final ForgeConfigSpec.BooleanValue SHOW_BORDER_PARTICLES;
    public static final ForgeConfigSpec.IntValue PARTICLE_DENSITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Client-side Visual Settings").push("visuals");
        SHOW_CLAIM_PARTICLES = builder
                .comment("Show magical forcefield particles at claim boundaries")
                .define("showClaimParticles", true);
        SHOW_BORDER_PARTICLES = builder
                .comment("Show magical barrier particles at the world border")
                .define("showBorderParticles", true);
        PARTICLE_DENSITY = builder
                .comment("Particle density multiplier (1 = low, 5 = high)")
                .defineInRange("particleDensity", 3, 1, 5);
        builder.pop();

        SPEC = builder.build();
    }
}

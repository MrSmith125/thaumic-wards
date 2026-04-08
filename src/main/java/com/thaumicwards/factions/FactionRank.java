package com.thaumicwards.factions;

import net.minecraft.util.text.TextFormatting;

public enum FactionRank {

    INITIATE(0, "Initiate", TextFormatting.GRAY, 1),
    ACOLYTE(1, "Acolyte", TextFormatting.GREEN, 3),
    WARLOCK(2, "Warlock", TextFormatting.AQUA, 6),
    ARCHMAGE(3, "Archmage", TextFormatting.GOLD, 10),
    LEADER(4, "Leader", TextFormatting.LIGHT_PURPLE, 10);

    private final int level;
    private final String displayName;
    private final TextFormatting color;
    private final int maxPersonalClaims;

    FactionRank(int level, String displayName, TextFormatting color, int maxPersonalClaims) {
        this.level = level;
        this.displayName = displayName;
        this.color = color;
        this.maxPersonalClaims = maxPersonalClaims;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public TextFormatting getColor() { return color; }
    public int getMaxPersonalClaims() { return maxPersonalClaims; }

    // Permission checks — Leader-only for management actions
    public boolean canKick() { return this == LEADER; }
    public boolean canPromote() { return this == LEADER; }
    public boolean canDemote() { return this == LEADER; }
    public boolean canExpandGuild() { return this == LEADER; }
    public boolean canPlaceNexus() { return level >= ARCHMAGE.level; }
    public boolean canClaim() { return level >= INITIATE.level; } // Everyone in a faction can claim

    public boolean isAtLeast(FactionRank other) {
        return this.level >= other.level;
    }

    /**
     * Returns the next auto-earnable rank (caps at WARLOCK).
     * ARCHMAGE requires leader promotion, LEADER requires OP assignment.
     */
    public FactionRank nextAutoRank() {
        if (this.level >= WARLOCK.level) return this; // Can't auto-promote past Warlock
        return values()[ordinal() + 1];
    }

    /**
     * Returns the next rank for manual promotion by a Leader.
     * Leaders can promote up to ARCHMAGE only.
     */
    public FactionRank nextManualRank() {
        if (this == ARCHMAGE || this == LEADER) return this;
        return values()[ordinal() + 1];
    }

    public FactionRank previousRank() {
        if (this == INITIATE) return INITIATE;
        if (this == LEADER) return LEADER; // Leaders can only be removed by OP, not demoted
        return values()[ordinal() - 1];
    }

    /**
     * Parses a rank from string with migration support for old rank names.
     */
    public static FactionRank fromString(String name) {
        if (name == null) return null;
        String upper = name.toUpperCase();

        // Migration from old rank names
        switch (upper) {
            case "APPRENTICE": return INITIATE;
            case "JOURNEYMAN": return ACOLYTE;
            case "ADEPT": return WARLOCK;
            case "MASTER": return ARCHMAGE;
            case "ARCHON": return LEADER;
        }

        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

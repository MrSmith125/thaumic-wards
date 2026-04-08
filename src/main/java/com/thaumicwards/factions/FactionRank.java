package com.thaumicwards.factions;

import net.minecraft.util.text.TextFormatting;

public enum FactionRank {

    APPRENTICE(0, "Apprentice", TextFormatting.GRAY),
    JOURNEYMAN(1, "Journeyman", TextFormatting.GREEN),
    ADEPT(2, "Adept", TextFormatting.AQUA),
    MASTER(3, "Master", TextFormatting.GOLD),
    ARCHON(4, "Archon", TextFormatting.LIGHT_PURPLE);

    private final int level;
    private final String displayName;
    private final TextFormatting color;

    FactionRank(int level, String displayName, TextFormatting color) {
        this.level = level;
        this.displayName = displayName;
        this.color = color;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public TextFormatting getColor() { return color; }

    public boolean canInvite() { return level >= ADEPT.level; }
    public boolean canKick() { return level >= MASTER.level; }
    public boolean canClaim() { return level >= JOURNEYMAN.level; }
    public boolean canExpandGuild() { return level >= MASTER.level; }
    public boolean canPromote() { return level >= ARCHON.level; }
    public boolean canDemote() { return level >= ARCHON.level; }
    public boolean canDisband() { return level >= ARCHON.level; }
    public boolean canPlaceNexus() { return level >= MASTER.level; }

    public boolean isAtLeast(FactionRank other) {
        return this.level >= other.level;
    }

    public static FactionRank fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public FactionRank nextRank() {
        if (this == ARCHON) return ARCHON;
        return values()[ordinal() + 1];
    }

    public FactionRank previousRank() {
        if (this == APPRENTICE) return APPRENTICE;
        return values()[ordinal() - 1];
    }
}

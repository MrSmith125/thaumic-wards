package com.thaumicwards.blocks;

import com.thaumicwards.claims.ClaimData;
import com.thaumicwards.claims.ClaimManager;
import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tile entity for faction outposts. Tracks health, ownership, and attack cooldowns.
 * Enemy faction members can attack to reduce health and eventually capture the outpost.
 */
public class OutpostTileEntity extends TileEntity {

    private UUID owningFactionId;
    private int health;
    private int maxHealth;
    private long placedAt;
    private long lastCapturedAt;
    private final Map<UUID, Long> attackerCooldowns = new HashMap<>();

    public OutpostTileEntity() {
        super(ModTileEntities.OUTPOST_TE.get());
        this.health = ServerConfig.OUTPOST_HEALTH.get();
        this.maxHealth = this.health;
        this.placedAt = System.currentTimeMillis();
        this.lastCapturedAt = 0;
    }

    // --- Getters ---

    public UUID getOwningFactionId() { return owningFactionId; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }

    public void setOwningFactionId(UUID factionId) {
        this.owningFactionId = factionId;
        setChanged();
    }

    // --- Attack / Capture ---

    /**
     * Attempts to deal damage to this outpost from an enemy attacker.
     * Returns true if the outpost was captured (health reached 0).
     */
    public boolean attack(ServerPlayerEntity attacker) {
        if (owningFactionId == null) return false;

        UUID attackerId = attacker.getUUID();
        UUID attackerFactionId = FactionManager.getPlayerFactionId(attackerId);

        // Must be in a faction and in the opposing faction
        if (attackerFactionId == null || attackerFactionId.equals(owningFactionId)) return false;

        // Check raid window
        if (ServerConfig.RAID_WINDOW_ENABLED.get()) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int startHour = ServerConfig.RAID_WINDOW_START_HOUR.get();
            int endHour = ServerConfig.RAID_WINDOW_END_HOUR.get();
            if (hour < startHour || hour >= endHour) {
                attacker.displayClientMessage(new StringTextComponent(
                        String.format("Outposts can only be raided between %d:00 and %d:00!", startHour, endHour))
                        .withStyle(TextFormatting.RED), true);
                return false;
            }
        }

        // Check recapture cooldown
        if (lastCapturedAt > 0) {
            long cooldownMs = ServerConfig.OUTPOST_RECAPTURE_COOLDOWN_MINUTES.get() * 60_000L;
            if (System.currentTimeMillis() - lastCapturedAt < cooldownMs) {
                long remaining = (cooldownMs - (System.currentTimeMillis() - lastCapturedAt)) / 60_000;
                attacker.displayClientMessage(new StringTextComponent(
                        String.format("This outpost was recently captured. Wait %d more minutes.", remaining + 1))
                        .withStyle(TextFormatting.RED), true);
                return false;
            }
        }

        // Check per-player attack cooldown
        long now = System.currentTimeMillis();
        Long lastAttack = attackerCooldowns.get(attackerId);
        long cooldownMs = ServerConfig.OUTPOST_HIT_COOLDOWN_SECONDS.get() * 1000L;
        if (lastAttack != null && now - lastAttack < cooldownMs) {
            return false; // Silently ignore — they'll see it happening
        }
        attackerCooldowns.put(attackerId, now);

        // Deal damage
        int damage = ServerConfig.OUTPOST_DAMAGE_PER_HIT.get();
        health -= damage;

        Faction owningFaction = FactionManager.getFaction(owningFactionId);
        String ownerName = owningFaction != null ? owningFaction.getName() : "Unknown";

        if (health <= 0) {
            // Capture!
            return captureOutpost(attacker, attackerFactionId);
        } else {
            // Show damage feedback
            attacker.displayClientMessage(new StringTextComponent(
                    String.format("Outpost damaged! %d/%d HP remaining", health, maxHealth))
                    .withStyle(TextFormatting.YELLOW), true);
            setChanged();
            return false;
        }
    }

    /**
     * Captures this outpost for the attacking faction.
     */
    private boolean captureOutpost(ServerPlayerEntity capturer, UUID newFactionId) {
        Faction oldFaction = FactionManager.getFaction(owningFactionId);
        Faction newFaction = FactionManager.getFaction(newFactionId);

        String oldName = oldFaction != null ? oldFaction.getName() : "Unknown";
        String newName = newFaction != null ? newFaction.getName() : "Unknown";

        // Unclaim old faction's outpost claim
        if (level != null) {
            ChunkPos chunkPos = new ChunkPos(worldPosition);
            ClaimManager.forceUnclaim(chunkPos);

            // Reclaim for new faction
            ClaimManager.claimChunk(chunkPos, capturer.getUUID(),
                    newName, ClaimData.ClaimType.OUTPOST, newFactionId);
        }

        // Transfer ownership
        this.owningFactionId = newFactionId;
        this.health = maxHealth;
        this.lastCapturedAt = System.currentTimeMillis();
        this.attackerCooldowns.clear();
        setChanged();

        // Broadcast capture announcement
        if (capturer.getServer() != null) {
            TextFormatting newColor = newFaction != null ? newFaction.getFactionColor() : TextFormatting.WHITE;
            for (ServerPlayerEntity player : capturer.getServer().getPlayerList().getPlayers()) {
                player.displayClientMessage(new StringTextComponent(
                        String.format("[OUTPOST CAPTURED] %s seized an outpost from %s!",
                                newName, oldName))
                        .withStyle(newColor, TextFormatting.BOLD), false);
            }
        }

        return true;
    }

    // --- Serialization ---

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        super.save(nbt);
        if (owningFactionId != null) {
            nbt.putUUID("owningFactionId", owningFactionId);
        }
        nbt.putInt("health", health);
        nbt.putInt("maxHealth", maxHealth);
        nbt.putLong("placedAt", placedAt);
        nbt.putLong("lastCapturedAt", lastCapturedAt);
        return nbt;
    }

    @Override
    public void load(BlockState state, CompoundNBT nbt) {
        super.load(state, nbt);
        if (nbt.contains("owningFactionId")) {
            this.owningFactionId = nbt.getUUID("owningFactionId");
        }
        this.health = nbt.getInt("health");
        this.maxHealth = nbt.getInt("maxHealth");
        this.placedAt = nbt.getLong("placedAt");
        this.lastCapturedAt = nbt.getLong("lastCapturedAt");

        if (maxHealth <= 0) {
            maxHealth = ServerConfig.OUTPOST_HEALTH.get();
            health = maxHealth;
        }
    }
}

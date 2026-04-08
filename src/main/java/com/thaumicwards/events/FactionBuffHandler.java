package com.thaumicwards.events;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionManager;
import com.thaumicwards.factions.FactionWarStatus;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;

/**
 * Applies buffs to members of the winning faction.
 * Called periodically from ServerTickHandler.
 */
public class FactionBuffHandler {

    /**
     * Applies buffs to online members of the winning faction.
     */
    public static void applyBuffs(ServerWorld world) {
        String winningId = FactionWarStatus.getWinningFactionId();
        if (winningId == null) return; // No winner, no buffs

        Faction winningFaction = FactionManager.getFactionByStringId(winningId);
        if (winningFaction == null) return;

        for (ServerPlayerEntity player : world.players()) {
            if (!winningFaction.isMember(player.getUUID())) continue;

            // Speed I for 35 seconds (reapplied every 30 seconds = 600 ticks)
            if (ServerConfig.BUFF_SPEED_ENABLED.get()) {
                player.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 700, 0, true, true, true));
            }
        }
    }

    /**
     * Handles XP bonus for winning faction members.
     * Called from an XP event handler or inline.
     */
    public static float getXpMultiplier(ServerPlayerEntity player) {
        String winningId = FactionWarStatus.getWinningFactionId();
        if (winningId == null) return 1.0f;

        Faction faction = FactionManager.getPlayerFaction(player.getUUID());
        if (faction == null) return 1.0f;

        if (faction.getStringId().equals(winningId)) {
            return 1.0f + (ServerConfig.BUFF_XP_BONUS_PERCENT.get() / 100.0f);
        }
        return 1.0f;
    }
}

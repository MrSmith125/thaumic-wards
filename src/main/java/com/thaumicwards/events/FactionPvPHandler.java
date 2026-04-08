package com.thaumicwards.events;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.factions.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles PvP death events between faction members.
 * Tracks kills and awards progression points for enemy faction kills.
 */
public class FactionPvPHandler {

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        // Check if the dead entity is a player
        if (!(event.getEntityLiving() instanceof ServerPlayerEntity)) return;

        // Check if the killer is a player
        if (event.getSource().getEntity() == null) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayerEntity)) return;

        ServerPlayerEntity victim = (ServerPlayerEntity) event.getEntityLiving();
        ServerPlayerEntity killer = (ServerPlayerEntity) event.getSource().getEntity();

        // Both must be in opposing factions
        if (!FactionManager.areEnemies(killer.getUUID(), victim.getUUID())) return;

        // Record the kill
        FactionKillTracker.recordKill(killer.getUUID(), victim.getUUID(), killer);

        // Check if in contested zone for bonus points
        boolean inContestedZone = ContestedZoneManager.isInContestedZone(killer.blockPosition());
        if (inContestedZone) {
            int multiplier = ServerConfig.CONTESTED_ZONE_KILL_MULTIPLIER.get() - 1; // -1 because base already awarded
            for (int i = 0; i < multiplier; i++) {
                ProgressionManager.onFactionKill(killer.getUUID(), victim.getUUID(), null); // Extra points, no re-notify
            }
        }

        // Get faction info for messages
        Faction killerFaction = FactionManager.getPlayerFaction(killer.getUUID());
        Faction victimFaction = FactionManager.getPlayerFaction(victim.getUUID());

        if (killerFaction == null || victimFaction == null) return;

        // Notify killer
        int basePoints = ServerConfig.ARCANE_POWER_PER_KILL.get();
        int totalPoints = inContestedZone ? basePoints * ServerConfig.CONTESTED_ZONE_KILL_MULTIPLIER.get() : basePoints;
        String bonusText = inContestedZone ? " (CONTESTED ZONE BONUS!)" : "";
        killer.displayClientMessage(new StringTextComponent(
                String.format("Enemy slain! +%d Arcane Power%s", totalPoints, bonusText))
                .withStyle(TextFormatting.GREEN, TextFormatting.BOLD), true); // Action bar

        // Notify victim
        victim.displayClientMessage(new StringTextComponent(
                "Defeated by " + killer.getName().getString() + " of " + killerFaction.getName())
                .withStyle(killerFaction.getFactionColor()), false);

        // Broadcast to all online players — faction kill announcement
        int killerKills = FactionKillTracker.getPlayerKills(killer.getUUID());
        for (ServerPlayerEntity onlinePlayer : killer.getServer().getPlayerList().getPlayers()) {
            onlinePlayer.displayClientMessage(new StringTextComponent(
                    String.format("[%s] ", killerFaction.getName()))
                    .withStyle(killerFaction.getFactionColor())
                    .append(new StringTextComponent(
                            String.format("%s slew %s! (%d kills)",
                                    killer.getName().getString(),
                                    victim.getName().getString(),
                                    killerKills))
                            .withStyle(TextFormatting.GRAY)), false);
        }
    }
}

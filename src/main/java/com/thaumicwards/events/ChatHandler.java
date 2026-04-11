package com.thaumicwards.events;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.factions.Faction;
import com.thaumicwards.factions.FactionManager;
import com.thaumicwards.factions.FactionRank;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ChatHandler {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (!ServerConfig.CHAT_FACTION_PREFIX_ENABLED.get()) return;

        ServerPlayerEntity player = event.getPlayer();
        Faction faction = FactionManager.getPlayerFaction(player.getUUID());
        if (faction == null) return;

        FactionRank rank = faction.getRank(player.getUUID());
        if (rank == null) return;

        // Build: [The Mystics] [Archmage] PlayerName: message
        ITextComponent factionTag = new StringTextComponent("[" + faction.getName() + "] ")
                .withStyle(faction.getFactionColor());
        ITextComponent rankTag = new StringTextComponent("[" + rank.getDisplayName() + "] ")
                .withStyle(rank.getColor());
        ITextComponent playerName = new StringTextComponent(player.getName().getString())
                .withStyle(faction.getFactionColor());
        ITextComponent message = new StringTextComponent(": " + event.getMessage());

        ITextComponent full = factionTag.copy().append(rankTag).append(playerName).append(message);
        event.setComponent(full);
    }
}

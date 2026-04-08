package com.thaumicwards.claims;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class ClaimProtectionHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isClientSide()) return;

        PlayerEntity player = event.getPlayer();
        ChunkPos chunkPos = new ChunkPos(event.getPos());

        if (!ClaimManager.canPlayerInteract(chunkPos, player.getUUID())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity) player).displayClientMessage(
                        new StringTextComponent("Arcane wards prevent you from breaking blocks here!")
                                .withStyle(TextFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isClientSide()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) entity;
        ChunkPos chunkPos = new ChunkPos(event.getPos());

        if (!ClaimManager.canPlayerInteract(chunkPos, player.getUUID())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity) player).displayClientMessage(
                        new StringTextComponent("Arcane wards prevent you from placing blocks here!")
                                .withStyle(TextFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isClientSide()) return;

        PlayerEntity player = event.getPlayer();
        ChunkPos chunkPos = new ChunkPos(event.getPos());

        if (!ClaimManager.canPlayerInteract(chunkPos, player.getUUID())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity) player).displayClientMessage(
                        new StringTextComponent("Arcane wards prevent interaction here!")
                                .withStyle(TextFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getWorld().isClientSide()) return;

        // Remove affected blocks that are in claimed chunks
        List<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            ChunkPos chunkPos = new ChunkPos(pos);
            if (ClaimManager.isChunkClaimed(chunkPos)) {
                toRemove.add(pos);
            }
        }
        event.getAffectedBlocks().removeAll(toRemove);

        // Remove affected entities in claimed chunks
        event.getAffectedEntities().removeIf(entity -> {
            ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
            return ClaimManager.isChunkClaimed(chunkPos);
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving().level.isClientSide()) return;

        // Protect entities in claimed chunks from player damage
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof PlayerEntity)) return;

        ChunkPos victimChunk = new ChunkPos(event.getEntityLiving().blockPosition());
        ClaimData claim = ClaimManager.getClaimAt(victimChunk);

        if (claim != null) {
            // Don't protect the claim owner's entities from themselves
            if (!claim.getOwnerUUID().equals(attacker.getUUID())) {
                if (!ClaimManager.canPlayerInteract(victimChunk, attacker.getUUID())) {
                    event.setCanceled(true);
                }
            }
        }
    }
}

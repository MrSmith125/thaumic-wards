package com.thaumicwards.border;

import com.thaumicwards.config.ServerConfig;
import com.thaumicwards.network.BorderParticlePacket;
import com.thaumicwards.network.ModNetwork;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.PacketDistributor;

public class BorderEnforcementHandler {

    private static final DamageSource BORDER_DAMAGE = new DamageSource("thaumic_wards.border")
            .bypassArmor().bypassMagic();

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntityLiving();

        if (!WorldBorderManager.hasBorder(player.level.dimension())) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        double distToBorder = WorldBorderManager.distanceToBorder(player.level.dimension(), playerPos);
        int warningDistance = ServerConfig.BORDER_WARNING_DISTANCE.get();

        // Send particle data when near border
        if (distToBorder <= warningDistance && distToBorder > 0) {
            // Send particles every 10 ticks to reduce packet spam
            if (player.tickCount % 10 == 0) {
                WorldBorderManager.BorderData border = WorldBorderManager.getBorder(player.level.dimension());
                if (border != null) {
                    ModNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new BorderParticlePacket(border.center, border.radius)
                    );
                }
            }

            // Warning message every 3 seconds
            if (player.tickCount % 60 == 0) {
                player.displayClientMessage(
                        new StringTextComponent("You feel the magical barrier pushing against you...")
                                .withStyle(TextFormatting.DARK_PURPLE), true);
            }
        }

        // Enforce border - push back and damage
        if (distToBorder < 0) {
            WorldBorderManager.BorderData border = WorldBorderManager.getBorder(player.level.dimension());
            if (border != null) {
                // Push player back toward center
                Vector3d toCenter = new Vector3d(
                        border.center.getX() - player.getX(),
                        0,
                        border.center.getZ() - player.getZ()
                ).normalize().scale(0.5);

                player.setDeltaMovement(toCenter.x, player.getDeltaMovement().y, toCenter.z);
                player.hurtMarked = true;

                // Apply damage every second
                if (player.tickCount % 20 == 0) {
                    float damage = ServerConfig.BORDER_DAMAGE.get().floatValue();
                    if (damage > 0) {
                        player.hurt(BORDER_DAMAGE, damage);
                    }
                }
            }
        }
    }
}

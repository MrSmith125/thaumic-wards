package com.thaumicwards.blocks;

import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.Random;

public class GuildNexusTileEntity extends TileEntity implements ITickableTileEntity {

    private int tickCount = 0;
    private final Random random = new Random();

    public GuildNexusTileEntity() {
        super(ModTileEntities.GUILD_NEXUS_TE.get());
    }

    @Override
    public void tick() {
        if (level == null || !level.isClientSide) return;

        tickCount++;
        if (tickCount % 5 != 0) return;

        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5;

        // More dramatic particles than Ward Stone
        // Double helix spiral
        for (int i = 0; i < 2; i++) {
            double angle = (tickCount * 0.15 + i * Math.PI) % (Math.PI * 2);
            double radius = 0.8;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double py = y + (tickCount % 60) * 0.05;

            level.addParticle(ParticleTypes.WITCH, px, py, pz, 0, 0.05, 0);
        }

        // Enchant particles floating upward
        if (tickCount % 10 == 0) {
            for (int i = 0; i < 5; i++) {
                level.addParticle(ParticleTypes.ENCHANT,
                        x + random.nextGaussian() * 0.5,
                        y + random.nextDouble() * 2,
                        z + random.nextGaussian() * 0.5,
                        random.nextGaussian() * 0.02,
                        0.1 + random.nextDouble() * 0.1,
                        random.nextGaussian() * 0.02);
            }
        }

        // Portal particles for extra flair
        if (tickCount % 15 == 0) {
            level.addParticle(ParticleTypes.PORTAL,
                    x + random.nextGaussian() * 0.3,
                    y + 0.5,
                    z + random.nextGaussian() * 0.3,
                    random.nextGaussian() * 0.5,
                    random.nextDouble() * 0.5,
                    random.nextGaussian() * 0.5);
        }
    }
}

package com.thaumicwards.blocks;

import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.Random;

public class WardStoneTileEntity extends TileEntity implements ITickableTileEntity {

    private int tickCount = 0;
    private final Random random = new Random();

    public WardStoneTileEntity() {
        super(ModTileEntities.WARD_STONE_TE.get());
    }

    @Override
    public void tick() {
        if (level == null || !level.isClientSide) return;

        tickCount++;
        if (tickCount % 10 != 0) return;

        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5;

        // Enchantment particles spiraling upward
        for (int i = 0; i < 3; i++) {
            double angle = (tickCount * 0.1 + i * 2.094) % (Math.PI * 2);
            double radius = 0.5;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double py = y + (tickCount % 40) * 0.05;

            level.addParticle(ParticleTypes.ENCHANT, px, py, pz, 0, 0.1, 0);
        }

        // Occasional portal particles
        if (tickCount % 30 == 0) {
            level.addParticle(ParticleTypes.PORTAL, x, y + 0.5, z,
                    random.nextGaussian() * 0.2,
                    random.nextDouble() * 0.2,
                    random.nextGaussian() * 0.2);
        }
    }
}

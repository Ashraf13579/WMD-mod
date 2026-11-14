package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class AirFx {

    private AirFx() {}

    static final int RED = 0xFF2A2A, GREEN = 0x2AFF5A, WHITE = 0xFFFFFF;

    static void haze(Level lv, double[] w) {
        RandomSource r = lv.random;
        lv.addParticle(WmdParticles.HEAT_HAZE.get(), w[0] + (r.nextDouble() - 0.5) * 0.3, w[1] + (r.nextDouble() - 0.5) * 0.3, w[2] + (r.nextDouble() - 0.5) * 0.3, 0.0, 0.01, 0.0);
    }

    static void nav(Level lv, double x, double y, double z, int color, float size) {
        WmdSmokeParticle.nextColor = color;
        WmdSmokeParticle.nextSize = size;
        try { lv.addAlwaysVisibleParticle(WmdParticles.NAV_LIGHT.get(), true, x, y, z, 0.0, 0.0, 0.0); }
        finally { WmdSmokeParticle.nextColor = WHITE; WmdSmokeParticle.nextSize = 1.0F; }
    }

    static void downwash(Level lv, double x, double y, double z, float strength, double radius) {
        if (strength < 0.15F) return;
        RandomSource r = lv.random;
        int range = Balance.DOWNWASH_RANGE;
        for (int dy = 1; dy <= range; dy++) {
            BlockPos p = BlockPos.containing(x, y - dy, z);
            BlockState st = lv.getBlockState(p);
            boolean water = lv.getFluidState(p).is(FluidTags.WATER);
            if (st.isAir() && !water) continue;
            float k = strength * (1.0F - (dy - 1) / (float) range);
            int n = Math.round(k * 4.0F);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2.0, d = radius * (0.3 + 0.7 * r.nextDouble());
                double px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d, py = p.getY() + 1.05;
                double vx = Math.cos(a) * 0.35 * k, vz = Math.sin(a) * 0.35 * k;
                if (water) lv.addParticle(WmdParticles.WATER_SPRAY.get(), px, py, pz, vx, 0.06 + 0.08 * k, vz);
                else if (!st.getFluidState().isEmpty() || st.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) lv.addParticle(ParticleTypes.POOF, px, py, pz, vx, 0.02, vz);
                else lv.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), px, py, pz, vx * 3.0, 0.15, vz * 3.0);
            }
            return;
        }
    }
}

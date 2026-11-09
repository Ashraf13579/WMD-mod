package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class NoduleMining {

    private NoduleMining() {}

    record Seabed(int floorY, boolean wet) {}

    static Seabed seabed(ServerLevel sl, int x, int yTop, int z, int maxDown) {
        boolean wet = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = yTop; y >= Math.max(sl.getMinBuildHeight(), yTop - maxDown); y--) {
            p.set(x, y, z);
            if (!sl.isLoaded(p)) return null;
            BlockState s = sl.getBlockState(p);
            if (s.isAir()) continue;
            if (s.getFluidState().is(FluidTags.WATER) && s.getCollisionShape(sl, p).isEmpty()) { wet = true; continue; }
            return new Seabed(y, wet);
        }
        return null;
    }

    static boolean openNodule(ServerLevel sl, BlockPos p) {
        return sl.isLoaded(p) && sl.getBlockState(p).is(Wmd.NODULE_FIELD.get()) && sl.getFluidState(p.above()).is(FluidTags.WATER);
    }

    static BlockPos nearest(ServerLevel sl, int cx, int floorY, int cz, int reach) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++)
            for (int dz = -reach; dz <= reach; dz++) {
                double d = dx * dx + dz * dz;
                if (d > reach * reach + 0.5 || d >= bd) continue;
                for (int y = floorY + 4; y >= floorY - 4; y--) {
                    p.set(cx + dx, y, cz + dz);
                    if (openNodule(sl, p)) { best = p.immutable(); bd = d; break; }
                }
            }
        return best;
    }

    static int collect(ServerLevel sl, BlockPos p, int min, int max) {
        if (!openNodule(sl, p) || ClaimGuard.protectedAt(sl, p)) return 0;
        sl.setBlock(p, Blocks.GRAVEL.defaultBlockState(), 3);
        NoduleSurvey.mined(sl, p);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 24, 0.5, 0.3, 0.5, 0.05);
        sl.sendParticles(ParticleTypes.BUBBLE, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 10, 0.4, 0.3, 0.4, 0.05);
        sl.playSound(null, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
        return min + (max > min ? sl.random.nextInt(max - min + 1) : 0);
    }
}

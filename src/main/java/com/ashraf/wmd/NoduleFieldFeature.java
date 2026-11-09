package com.ashraf.wmd;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class NoduleFieldFeature extends Feature<NoneFeatureConfiguration> {

    public NoduleFieldFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) { return placeField(ctx.level(), ctx.random(), ctx.origin(), Heightmap.Types.OCEAN_FLOOR_WG) > 0; }

    static int placeField(WorldGenLevel level, RandomSource r, BlockPos o, Heightmap.Types hm) {
        int top = level.getHeight(hm, o.getX(), o.getZ());
        if (waterAbove(level, new BlockPos(o.getX(), top - 1, o.getZ())) < Balance.NODULE_MIN_WATER) return 0;
        double rad = Balance.NODULE_R_MIN + r.nextDouble() * (Balance.NODULE_R_MAX - Balance.NODULE_R_MIN);
        double sx = 0.7 + r.nextDouble() * 0.6, sz = 0.7 + r.nextDouble() * 0.6;
        int ir = (int) Math.ceil(rad * 1.3);
        int placed = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -ir; dx <= ir; dx++)
            for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.sqrt((dx / sx) * (dx / sx) + (dz / sz) * (dz / sz)) / rad;
                if (d > 1.0 || r.nextDouble() > Balance.NODULE_DENSITY * (1.0 - 0.5 * d * d)) continue;
                int x = o.getX() + dx, z = o.getZ() + dz;
                int h = level.getHeight(hm, x, z);
                p.set(x, h - 1, z);
                if (!seabed(level.getBlockState(p)) || !level.getFluidState(p.above()).is(FluidTags.WATER)) continue;
                if (Math.abs(h - top) > 6 || waterAbove(level, p) < Balance.NODULE_MIN_WATER) continue;

                if (level instanceof net.minecraft.server.level.ServerLevel live && !Config.Server.canBreak(live, p)) continue;
                level.setBlock(p, Wmd.NODULE_FIELD.get().defaultBlockState(), 2);
                placed++;
            }

        if (placed > 0) NoduleSurvey.generated(level, o.getX(), top - 1, o.getZ(), (int) Math.ceil(rad * Math.max(sx, sz)), placed);
        return placed;
    }

    private static int waterAbove(WorldGenLevel level, BlockPos floor) {
        int n = 0;
        BlockPos.MutableBlockPos p = floor.mutable();
        for (int i = 1; i <= 96; i++) {
            p.setY(floor.getY() + i);
            if (!level.getFluidState(p).is(FluidTags.WATER)) break;
            n++;
        }
        return n;
    }

    private static boolean seabed(BlockState s) {
        return s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD);
    }
}

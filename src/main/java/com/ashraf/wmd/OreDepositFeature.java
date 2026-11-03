package com.ashraf.wmd;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class OreDepositFeature extends Feature<OreDepositFeature.Config> {

    public record Config(BlockState state, int radiusMin, int radiusMax, int heightMin, int heightMax, float density) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(Config::state),
                Codec.intRange(1, 15).fieldOf("radius_min").forGetter(Config::radiusMin),
                Codec.intRange(1, 15).fieldOf("radius_max").forGetter(Config::radiusMax),
                Codec.intRange(1, 15).fieldOf("height_min").forGetter(Config::heightMin),
                Codec.intRange(1, 15).fieldOf("height_max").forGetter(Config::heightMax),
                Codec.floatRange(0.05F, 1.0F).fieldOf("density").forGetter(Config::density)
        ).apply(i, Config::new));
    }

    public OreDepositFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config cfg = context.config();
        BlockPos origin = context.origin();
        int rx = between(random, cfg.radiusMin(), cfg.radiusMax());
        int rz = between(random, cfg.radiusMin(), cfg.radiusMax());
        int ry = between(random, cfg.heightMin(), cfg.heightMax());

        int lx = origin.getX() + (random.nextInt(rx + 1) - rx / 2), lz = origin.getZ() + (random.nextInt(rz + 1) - rz / 2);
        int placed = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight() + 1, origin.getY() - ry), maxY = origin.getY() + ry;
        for (int x = origin.getX() - rx; x <= origin.getX() + rx; x++) {
            for (int z = origin.getZ() - rz; z <= origin.getZ() + rz; z++) {
                for (int y = minY; y <= maxY; y++) {
                    double dx = (x - origin.getX()) / (double) rx, dy = (y - origin.getY()) / (double) ry, dz = (z - origin.getZ()) / (double) rz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double ex = (x - lx) / (rx * 0.6), ez = (z - lz) / (rz * 0.6), ey = dy / 0.8;
                    double d2 = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    double dd = Math.min(d, d2);
                    if (dd >= 1.0) continue;

                    if (random.nextFloat() > cfg.density() * (1.0 - 0.6 * dd * dd)) continue;
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!(s.is(BlockTags.STONE_ORE_REPLACEABLES) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES))) continue;
                    level.setBlock(p, cfg.state(), 2);
                    placed++;
                }
            }
        }
        return placed > 0;
    }

    private static int between(RandomSource random, int a, int b) {
        return a >= b ? a : a + random.nextInt(b - a + 1);
    }
}

package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class OilDepositFeature extends Feature<NoneFeatureConfiguration> {

    public static final int MAX_CLUSTER = 8;

    private static final int SPACING = 3;

    public OilDepositFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockState deposit = Wmd.CRUDE_OIL_DEPOSIT.get().defaultBlockState();

        if (depositNearby(level, context.origin(), SPACING)) {
            return false;
        }

        int target = 1 + random.nextInt(MAX_CLUSTER);
        List<BlockPos> placed = new ArrayList<>();
        BlockPos next = context.origin();

        for (int attempt = 0; attempt < 64 && placed.size() < target; attempt++) {
            if (!placed.contains(next) && canReplace(level.getBlockState(next)) && !touchesOtherDeposit(level, next, placed)) {
                level.setBlock(next, deposit, 2);
                placed.add(next.immutable());
            }
            BlockPos from = placed.isEmpty() ? context.origin() : placed.get(random.nextInt(placed.size()));
            next = from.relative(Direction.getRandom(random));
        }
        return !placed.isEmpty();
    }

    private static boolean depositNearby(WorldGenLevel level, BlockPos pos, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
            if (isDeposit(level.getBlockState(p))) return true;
        }
        return false;
    }

    private static boolean touchesOtherDeposit(WorldGenLevel level, BlockPos pos, List<BlockPos> ownCluster) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.relative(dir);
            if (isDeposit(level.getBlockState(neighbour)) && !ownCluster.contains(neighbour)) return true;
        }
        return false;
    }

    private static boolean isDeposit(BlockState state) {
        return state.is(Wmd.CRUDE_OIL_DEPOSIT.get());
    }

    private static boolean canReplace(BlockState state) {
        return state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }
}

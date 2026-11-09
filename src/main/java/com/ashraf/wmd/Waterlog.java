package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class Waterlog {

    private Waterlog() {}

    public static final BooleanProperty W = BlockStateProperties.WATERLOGGED;

    public static boolean water(LevelReader level, BlockPos p) {
        return level != null && p != null && level.getFluidState(p).getType() == Fluids.WATER;
    }

    public static BlockState placed(BlockState s, LevelReader level, BlockPos p) {
        return s != null && s.hasProperty(W) ? s.setValue(W, water(level, p)) : s;
    }

    public static FluidState fluid(BlockState s) {
        return s.hasProperty(W) && s.getValue(W) ? Fluids.WATER.getSource(false) : null;
    }

    public static void tick(BlockState s, LevelAccessor level, BlockPos pos) {
        if (s.hasProperty(W) && s.getValue(W)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
    }

    public static BlockState part(LevelReader level, BlockPos p) {
        return placed(Wmd.MACHINE_PART.get().defaultBlockState(), level, p);
    }
}

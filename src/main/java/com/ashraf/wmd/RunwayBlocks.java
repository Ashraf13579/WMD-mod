package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RunwayBlocks {

    private RunwayBlocks() {}

    public static boolean isRunway(BlockState s) { return s.getBlock() instanceof Tile || s.getBlock() instanceof Line; }

    public static class Tile extends Block {
        public static final MapCodec<Tile> CODEC = simpleCodec(Tile::new);
        public Tile(Properties p) { super(p); }
        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    }

    public static class Line extends Block {
        public static final MapCodec<Line> CODEC = simpleCodec(Line::new);
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public Line(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z)); }
        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AXIS); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return defaultBlockState().setValue(AXIS, ctx.getHorizontalDirection().getAxis()); }
    }

    public static class Light extends Block {
        public static final MapCodec<Light> CODEC = simpleCodec(Light::new);
        private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 8, 10);
        public Light(Properties p) { super(p); }
        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }
    }
}

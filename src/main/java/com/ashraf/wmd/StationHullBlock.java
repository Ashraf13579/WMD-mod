package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class StationHullBlock {

    private StationHullBlock() {}

    public static class Light extends Block {
        public static final MapCodec<Light> CODEC = simpleCodec(Light::new);
        public static final BooleanProperty NIGHT = BooleanProperty.create("night");

        public Light(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(NIGHT, false)); }

        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(NIGHT); }
    }

    public static class Pole extends Block {
        public static final MapCodec<Pole> CODEC = simpleCodec(Pole::new);
        private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

        public Pole(Properties p) { super(p); }

        @Override protected MapCodec<? extends Block> codec() { return CODEC; }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState s, net.minecraft.world.level.BlockGetter l, net.minecraft.core.BlockPos p, net.minecraft.world.phys.shapes.CollisionContext c) { return SHAPE; }

        @Override
        public boolean isLadder(BlockState s, net.minecraft.world.level.LevelReader l, net.minecraft.core.BlockPos p, net.minecraft.world.entity.LivingEntity e) { return true; }
    }

    public static class Ladder extends Block {
        public static final MapCodec<Ladder> CODEC = simpleCodec(Ladder::new);
        public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        private static final net.minecraft.world.phys.shapes.VoxelShape N = Block.box(0, 0, 13, 16, 16, 16), S = Block.box(0, 0, 0, 16, 16, 3),
                W = Block.box(13, 0, 0, 16, 16, 16), E = Block.box(0, 0, 0, 3, 16, 16);

        public Ladder(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)); }

        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState s) { return net.minecraft.world.level.block.RenderShape.INVISIBLE; }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState s, net.minecraft.world.level.BlockGetter l, net.minecraft.core.BlockPos p, net.minecraft.world.phys.shapes.CollisionContext c) {
            return switch (s.getValue(FACING)) { case SOUTH -> S; case WEST -> W; case EAST -> E; default -> N; };
        }

        @Override
        public boolean isLadder(BlockState s, net.minecraft.world.level.LevelReader l, net.minecraft.core.BlockPos p, net.minecraft.world.entity.LivingEntity e) { return true; }
    }

    public static class Frame extends Block {
        public static final MapCodec<Frame> CODEC = simpleCodec(Frame::new);
        public static final BooleanProperty BREACH = BooleanProperty.create("breach");

        public Frame(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(BREACH, false)); }

        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(BREACH); }
    }
}

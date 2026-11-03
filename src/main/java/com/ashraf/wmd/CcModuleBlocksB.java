package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CcModuleBlocksB {

    private CcModuleBlocksB() {}

    public static class LaunchConsole extends CcModuleBlock {
        public static final MapCodec<LaunchConsole> CODEC = simpleCodec(LaunchConsole::new);
        public LaunchConsole(Properties p) { super(p, -1, 1, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcLaunchConsoleBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_LAUNCH_CONSOLE_BE.get(); }
    }

    public static class RetargetConsole extends CcModuleBlock {
        public static final MapCodec<RetargetConsole> CODEC = simpleCodec(RetargetConsole::new);
        public RetargetConsole(Properties p) { super(p, -1, 0, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcRetargetConsoleBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_RETARGET_CONSOLE_BE.get(); }
    }

    public static class StrikePlanner extends CcModuleBlock {
        public static final MapCodec<StrikePlanner> CODEC = simpleCodec(StrikePlanner::new);
        public StrikePlanner(Properties p) { super(p, -1, 1, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcStrikePlannerBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_STRIKE_PLANNER_BE.get(); }
    }

    public static class MissileCam extends CcModuleBlock {
        public static final MapCodec<MissileCam> CODEC = simpleCodec(MissileCam::new);
        public MissileCam(Properties p) { super(p, -1, 1, 0, 1, 0, 0, true); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcMissileCamBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_MISSILE_CAM_BE.get(); }
    }
}

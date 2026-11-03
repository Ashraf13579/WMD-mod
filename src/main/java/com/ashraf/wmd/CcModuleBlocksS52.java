package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CcModuleBlocksS52 {

    private CcModuleBlocksS52() {}

    public static class ArtilleryConsole extends CcModuleBlock {
        public static final MapCodec<ArtilleryConsole> CODEC = simpleCodec(ArtilleryConsole::new);
        public ArtilleryConsole(Properties p) { super(p, -1, 1, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcArtilleryConsoleBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_ARTILLERY_CONSOLE_BE.get(); }
    }

    public static class PlannerDesk extends CcModuleBlock {
        public static final MapCodec<PlannerDesk> CODEC = simpleCodec(PlannerDesk::new);

        public static final double DECK_DROP = 0.5;
        public PlannerDesk(Properties p) { super(p, -1, 1, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public net.minecraft.world.phys.shapes.VoxelShape machineShape(BlockPos local, net.minecraft.core.Direction facing) {
            return super.machineShape(local, facing).move(0.0, -DECK_DROP, 0.0);
        }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcPlannerBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_PLANNER_BE.get(); }
    }

    public static class IronDomeDesk extends CcModuleBlock {
        public static final MapCodec<IronDomeDesk> CODEC = simpleCodec(IronDomeDesk::new);
        public IronDomeDesk(Properties p) { super(p, -1, 1, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcIronDomeBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_IRON_DOME_BE.get(); }
    }
}

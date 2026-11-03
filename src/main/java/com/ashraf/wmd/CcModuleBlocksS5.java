package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CcModuleBlocksS5 {

    private CcModuleBlocksS5() {}

    public static class InterceptorConsole extends CcModuleBlock {
        public static final MapCodec<InterceptorConsole> CODEC = simpleCodec(InterceptorConsole::new);
        public InterceptorConsole(Properties p) { super(p, -1, 1, 0, 1, 0, 1, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcInterceptorConsoleBlockEntity(pos, state); }
        @Override protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return Wmd.CC_INTERCEPTOR_CONSOLE_BE.get(); }
    }
}

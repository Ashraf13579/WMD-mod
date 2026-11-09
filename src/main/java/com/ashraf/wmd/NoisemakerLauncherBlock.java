package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class NoisemakerLauncherBlock extends ItemUnitBlock {

    public static final MapCodec<NoisemakerLauncherBlock> CODEC = simpleCodec(NoisemakerLauncherBlock::new);

    public NoisemakerLauncherBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 0); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public boolean faceAway() { return true; }

    @Override
    public boolean hullPlug(BlockPos local) { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NoisemakerLauncherBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.NOISEMAKER_LAUNCHER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.NOISEMAKER_LAUNCHER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}

package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SwarmLauncherBlock extends UnitBlock {

    public static final MapCodec<SwarmLauncherBlock> CODEC = simpleCodec(SwarmLauncherBlock::new);

    public SwarmLauncherBlock(Properties properties) {
        super(properties, SwarmLauncherBlockEntity.X0, SwarmLauncherBlockEntity.X1, SwarmLauncherBlockEntity.Y0, SwarmLauncherBlockEntity.Y1,
                SwarmLauncherBlockEntity.Z0, SwarmLauncherBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SwarmLauncherBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SWARM_LAUNCHER_BE.get(), SwarmLauncherBlockEntity::clientTickSwarm)
                : createTickerHelper(type, Wmd.SWARM_LAUNCHER_BE.get(), SwarmLauncherBlockEntity::serverTickSwarm);
    }
}

package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SteamTurbineBlock extends ItemUnitBlock {

    public static final MapCodec<SteamTurbineBlock> CODEC = simpleCodec(SteamTurbineBlock::new);

    public SteamTurbineBlock(Properties properties) {
        super(properties, SteamTurbineBlockEntity.X0, SteamTurbineBlockEntity.X1, SteamTurbineBlockEntity.Y0, SteamTurbineBlockEntity.Y1,
                SteamTurbineBlockEntity.Z0, SteamTurbineBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SteamTurbineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.STEAM_TURBINE_BE.get(), SteamTurbineBlockEntity::clientTickTurbine)
                : createTickerHelper(type, Wmd.STEAM_TURBINE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}

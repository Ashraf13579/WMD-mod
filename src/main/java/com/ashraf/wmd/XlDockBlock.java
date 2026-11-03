package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class XlDockBlock extends RobotDockBlock {

    public static final MapCodec<XlDockBlock> CODEC = simpleCodec(XlDockBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();

    static {
        for (int x = -3; x <= 2; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 16; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public XlDockBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new XlDockBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.XL_DOCK_BE.get(), RobotDockBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.XL_DOCK_BE.get(), RobotDockBlockEntity::serverTick);
    }
}

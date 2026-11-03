package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CamoNetBlock extends MachineBlock {

    public static final MapCodec<CamoNetBlock> CODEC = simpleCodec(CamoNetBlock::new);
    private static final List<BlockPos> CELL = List.of(BlockPos.ZERO);

    public CamoNetBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override
    protected List<BlockPos> footprint() { return CELL; }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof CamoNetBlockEntity be) {
            be.refresh();
            player.displayClientMessage(be.status(), true);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CamoNetBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.CAMO_NET_BE.get(), CamoNetBlockEntity::serverTick);
    }
}

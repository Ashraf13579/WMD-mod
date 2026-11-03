package com.ashraf.wmd;

import java.util.ArrayList;
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

public class RobotDockBlock extends MachineBlock {

    public static final MapCodec<RobotDockBlock> CODEC = simpleCodec(RobotDockBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();

    static {
        for (int x = -2; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 8; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public RobotDockBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof RobotDockBlockEntity be) player.displayClientMessage(be.info(), true);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RobotDockBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.ROBOT_DOCK_BE.get(), RobotDockBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.ROBOT_DOCK_BE.get(), RobotDockBlockEntity::serverTick);
    }
}

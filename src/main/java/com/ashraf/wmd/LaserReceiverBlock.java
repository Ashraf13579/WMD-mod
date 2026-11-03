package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class LaserReceiverBlock extends MachineBlock {

    public static final MapCodec<LaserReceiverBlock> CODEC = simpleCodec(LaserReceiverBlock::new);
    public static final BooleanProperty ALERT = BooleanProperty.create("alert");
    private static final List<BlockPos> FOOTPRINT = List.of(BlockPos.ZERO, new BlockPos(0, 1, 0));

    public LaserReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ALERT, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ALERT);
    }

    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return state.getValue(ALERT) ? 15 : 0; }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof LaserReceiverBlockEntity be) player.displayClientMessage(be.info(), true);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LaserReceiverBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.LASER_RECEIVER_BE.get(), LaserReceiverBlockEntity::serverTick);
    }
}

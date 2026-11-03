package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SolarPanelBlock extends UnitBlock {

    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    private static final VoxelShape LOW = Block.box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape LOW_SOCKET = Shapes.or(LOW, Block.box(2, 0, 10, 14, 15, 15));
    private static final VoxelShape HIGH = Shapes.or(Block.box(6, 0, 6, 10, 8, 10), Block.box(0, 7, 0, 16, 9.5, 16));

    public SolarPanelBlock(Properties properties) { super(properties, 0, 0, 0, 1, 0, 0); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected boolean mountable() { return false; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        return s == null ? null : s.setValue(FACING, Direction.NORTH);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SolarPanelBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SOLAR_PANEL_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.SOLAR_PANEL_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        if (local.getY() > 0) return HIGH;
        return level.getBlockEntity(controller) instanceof SolarPanelBlockEntity be && !be.showsSocket() ? LOW : LOW_SOCKET;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return machineShape(level, pos, BlockPos.ZERO, Direction.NORTH);
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) { return local.getY() > 0 ? HIGH : LOW_SOCKET; }

    @Override
    public VoxelShape machineShape() { return LOW_SOCKET; }
}

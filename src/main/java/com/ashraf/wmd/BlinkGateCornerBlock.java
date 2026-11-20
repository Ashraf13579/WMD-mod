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

public class BlinkGateCornerBlock extends MachineBlock {

    public static final MapCodec<BlinkGateCornerBlock> CODEC = simpleCodec(BlinkGateCornerBlock::new);
    private static final List<BlockPos> FOOT = BlinkGateCoreBlockEntity.footprint(true);

    public BlinkGateCornerBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected List<BlockPos> footprint() { return FOOT; }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape machineShape() { return net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 16, 16); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return BlinkGatePieceBlockEntity.corner(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, BlinkGateBlocks.BLINK_CORNER_BE.get(), ProcessUnitBlockEntity::clientTick) : null;
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, net.minecraft.world.phys.BlockHitResult hit) { BlinkGatePieceBlockEntity.openCore(level, controller, player); }

    @Override
    public void interact(Level level, BlockPos controller, Player player) { BlinkGatePieceBlockEntity.openCore(level, controller, player); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlinkGatePieceBlockEntity.removed(this, level, pos, newState, movedByPiston, true);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

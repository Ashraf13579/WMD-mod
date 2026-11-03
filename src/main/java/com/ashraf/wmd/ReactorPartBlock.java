package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ReactorPartBlock extends Block {

    public static final BooleanProperty FORMED = MultiblockPartBlock.FORMED;

    public ReactorPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FORMED); }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this) && !state.getValue(FORMED) && !ReactorStructure.busy()) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!state.getValue(FORMED)) ReactorStructure.tryFormNear(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && state.getValue(FORMED) && !newState.is(this) && !ReactorStructure.busy()) {
            BlockPos ctrl = ReactorStructure.findController(level, pos, true);
            if (ctrl != null && level.getBlockState(ctrl).getBlock() instanceof NuclearReactorBlock rb) rb.unform(level, ctrl);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(FORMED)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockPos ctrl = ReactorStructure.findController(level, pos, true);
            if (ctrl != null && level.getBlockState(ctrl).getBlock() instanceof NuclearReactorBlock rb) rb.interactFormed(level, ctrl, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

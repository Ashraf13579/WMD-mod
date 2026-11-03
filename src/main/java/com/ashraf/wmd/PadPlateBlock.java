package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PadPlateBlock extends Block {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public PadPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(MachineBlock.MOUNT, net.minecraft.core.Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, MachineBlock.MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(MachineBlock.MOUNT, ShipPlace.upFor(context));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && state.getValue(FORMED) && !newState.is(this)) {
            BlockPos owner = PadStructure.findOwner(level, pos, Machines.mount(state));
            if (owner != null) {
                BlockState t = level.getBlockState(owner);
                LaunchTerminalBlock block = (LaunchTerminalBlock) t.getBlock();
                PadStructure.unform(level, owner, t.getValue(LaunchTerminalBlock.FACING), Machines.mount(t), block.getTier());
                level.setBlock(owner, t.setValue(LaunchTerminalBlock.FORMED, false), 3);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

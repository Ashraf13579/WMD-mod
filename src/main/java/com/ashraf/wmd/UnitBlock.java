package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.ToIntFunction;

public abstract class UnitBlock extends MachineBlock {

    private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);
    private final List<BlockPos> footprint = new ArrayList<>();

    protected UnitBlock(Properties properties, int x0, int x1, int y0, int y1, int z0, int z1) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, false));
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) footprint.add(new BlockPos(x, y, z));
    }

    public static ToIntFunction<BlockState> light(int level) { return s -> s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) ? level : 0; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    protected List<BlockPos> footprint() { return footprint; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!ShipRelink.shipMove(level, movedByPiston) && !level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof ProcessUnitBlockEntity be)
            for (net.minecraft.world.item.ItemStack s : be.takeUpgrades()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public VoxelShape machineShape() { return FULL; }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof ProcessUnitBlockEntity up && up.installUpgrade(player)) return;
        if (!player.isShiftKeyDown()) { MachineMenu.open(player, level.getBlockEntity(controller)); return; }
        if (level.getBlockEntity(controller) instanceof ProcessUnitBlockEntity be) player.displayClientMessage(be.status(), true);
    }
}

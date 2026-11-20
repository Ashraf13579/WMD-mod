package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CoalGeneratorBlock extends UnitBlock {

    public static final MapCodec<CoalGeneratorBlock> CODEC = simpleCodec(CoalGeneratorBlock::new);

    public CoalGeneratorBlock(Properties properties) {
        super(properties, -2, 2, 0, 3, 0, 2);
        for (int y = 4; y <= 7; y++) super.footprint().add(new BlockPos(-2, y, 1));
    }

    private java.util.List<BlockPos> full;

    java.util.List<BlockPos> cells() {
        if (full == null) full = ModelFootprint.extend(super.footprint(), "coal_generator", -2, 2, 4, 0, 2);
        return full;
    }

    @Override
    protected java.util.List<BlockPos> footprint() { return cells(); }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape machineShape(BlockPos local, net.minecraft.core.Direction facing) {
        if (local.getY() >= 4 && generated(local, facing) == null) return net.minecraft.world.phys.shapes.Shapes.empty();
        return super.machineShape(local, facing);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CoalGeneratorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.COAL_GENERATOR_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.COAL_GENERATOR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown() && level.getBlockEntity(controller) instanceof CoalGeneratorBlockEntity be && be.tryInsertFuel(player)) return;
        super.interact(level, controller, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity be) {
            ItemStack rest = be.takeFuel();
            if (!rest.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rest);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

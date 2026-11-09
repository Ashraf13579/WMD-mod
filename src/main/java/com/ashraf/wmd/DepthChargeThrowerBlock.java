package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DepthChargeThrowerBlock extends ItemUnitBlock {

    public static final MapCodec<DepthChargeThrowerBlock> CODEC = simpleCodec(DepthChargeThrowerBlock::new);

    public DepthChargeThrowerBlock(Properties properties) {
        super(properties, DepthChargeThrowerBlockEntity.X0, DepthChargeThrowerBlockEntity.X1, DepthChargeThrowerBlockEntity.Y0, DepthChargeThrowerBlockEntity.Y1,
                DepthChargeThrowerBlockEntity.Z0, DepthChargeThrowerBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        if (s == null) return null;
        Direction turned = s.getValue(FACING).getCounterClockWise(), mount = Machines.mount(s);
        Level level = ctx.getLevel();
        BlockPos origin = ctx.getClickedPos();
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (BlockPos l : footprint()) {
            BlockPos p = Machines.toWorld(origin, turned, mount, l.getX(), l.getY(), l.getZ());
            if (!level.isInWorldBounds(p) || (!p.equals(origin) && !level.getBlockState(p).canBeReplaced())) return s;
            cells.add(p);
        }
        if (PlaceGuard.blocker(level, cells) != null || !ClaimGuard.mayPlace(level, ctx.getPlayer(), cells, origin)) return s;
        return s.setValue(FACING, turned);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DepthChargeThrowerBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.DEPTH_CHARGE_THROWER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.DEPTH_CHARGE_THROWER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}

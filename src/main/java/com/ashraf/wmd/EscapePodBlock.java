package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class EscapePodBlock extends UnitBlock {

    public static final MapCodec<EscapePodBlock> CODEC = simpleCodec(EscapePodBlock::new);

    public EscapePodBlock(Properties properties) { super(properties, 0, 1, 0, 2, 0, 1); }

    @Override public boolean hullPlug(net.minecraft.core.BlockPos local) { return local.getY() == 2; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EscapePodBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.ESCAPE_POD_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.ESCAPE_POD_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown() && level.getBlockEntity(controller) instanceof EscapePodBlockEntity be && be.tryRearm(player)) return;
        super.interact(level, controller, player);
    }
}

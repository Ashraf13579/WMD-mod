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

public class LavaGeneratorBlock extends UnitBlock {

    public static final MapCodec<LavaGeneratorBlock> CODEC = simpleCodec(LavaGeneratorBlock::new);

    public LavaGeneratorBlock(Properties properties) { super(properties, 0, 1, 0, 1, 0, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected boolean mountable() { return false; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LavaGeneratorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.LAVA_GENERATOR_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.LAVA_GENERATOR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown() && level.getBlockEntity(controller) instanceof LavaGeneratorBlockEntity be && be.tryPourBucket(player)) return;
        super.interact(level, controller, player);
    }
}

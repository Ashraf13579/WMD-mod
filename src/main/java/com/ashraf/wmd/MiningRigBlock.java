package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MiningRigBlock extends ItemUnitBlock {

    public static final MapCodec<MiningRigBlock> CODEC = simpleCodec(MiningRigBlock::new);

    public MiningRigBlock(Properties properties) { super(properties, -2, 2, 0, 6, 0, 4); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MiningRigBlockEntity(pos, state); }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.world.entity.player.Player p && level.getBlockEntity(pos) instanceof MiningRigBlockEntity be) be.setPlacer(p.getUUID());
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.MINING_RIG_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.MINING_RIG_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}

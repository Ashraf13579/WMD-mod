package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class StormGeneratorBlock extends UnitBlock {

    public static final MapCodec<StormGeneratorBlock> CODEC = simpleCodec(StormGeneratorBlock::new);

    public StormGeneratorBlock(Properties properties) {
        super(properties, StormGeneratorBlockEntity.X0, StormGeneratorBlockEntity.X1, StormGeneratorBlockEntity.Y0, StormGeneratorBlockEntity.Y1,
                StormGeneratorBlockEntity.Z0, StormGeneratorBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StormGeneratorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, StormBlocks.STORM_GENERATOR_BE.get(), StormGeneratorBlockEntity::clientTickStorm)
                : createTickerHelper(type, StormBlocks.STORM_GENERATOR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.storm_generator", Balance.STGEN_MIN_RANGE, String.format("%,d", Balance.STGEN_MAX_RANGE),
                Balance.STGEN_STORM / 20, Balance.STGEN_BUFFER_FE / 1_000_000).withStyle(ChatFormatting.GRAY));
    }
}

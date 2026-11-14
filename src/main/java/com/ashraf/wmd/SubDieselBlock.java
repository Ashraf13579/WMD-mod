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

public class SubDieselBlock extends UnitBlock {

    public static final MapCodec<SubDieselBlock> CODEC = simpleCodec(SubDieselBlock::new);

    public SubDieselBlock(Properties properties) { super(properties, 0, 1, 0, 1, 0, 2); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SubDieselBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SUB_DIESEL_BE.get(), SubDieselBlockEntity::clientTickDiesel)
                : createTickerHelper(type, Wmd.SUB_DIESEL_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.sub_diesel").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.sub_diesel.air").withStyle(ChatFormatting.GRAY));
    }
}

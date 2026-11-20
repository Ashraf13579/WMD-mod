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

public class GravityWellBlock extends UnitBlock {

    public static final MapCodec<GravityWellBlock> CODEC = simpleCodec(GravityWellBlock::new);

    public GravityWellBlock(Properties properties) {
        super(properties, GravityWellBlockEntity.X0, GravityWellBlockEntity.X1, GravityWellBlockEntity.Y0, GravityWellBlockEntity.Y1, GravityWellBlockEntity.Z0,
                GravityWellBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GravityWellBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, GravityWellBlocks.GRAVITY_WELL_BE.get(), GravityWellBlockEntity::clientTickWell)
                : createTickerHelper(type, GravityWellBlocks.GRAVITY_WELL_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.gravity_well", Balance.GWG_R_MIN, Balance.GWG_R_MAX, Balance.GWG_BUFFER_FE / 1_000_000).withStyle(ChatFormatting.GRAY));
    }
}

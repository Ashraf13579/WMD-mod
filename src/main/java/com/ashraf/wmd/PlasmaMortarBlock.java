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

public class PlasmaMortarBlock extends ItemUnitBlock {

    public static final MapCodec<PlasmaMortarBlock> CODEC = simpleCodec(PlasmaMortarBlock::new);

    public PlasmaMortarBlock(Properties properties) {
        super(properties, PlasmaMortarBlockEntity.X0, PlasmaMortarBlockEntity.X1, PlasmaMortarBlockEntity.Y0, PlasmaMortarBlockEntity.Y1, PlasmaMortarBlockEntity.Z0,
                PlasmaMortarBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PlasmaMortarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, PlasmaMortarBlocks.PLASMA_MORTAR_BE.get(), PlasmaMortarBlockEntity::clientTickPlasma)
                : createTickerHelper(type, PlasmaMortarBlocks.PLASMA_MORTAR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.plasma_mortar", Balance.PMORT_MIN, String.format("%,d", Balance.PMORT_RANGE), String.format("%,d", Balance.PMORT_SHOT_FE),
                Balance.PMORT_H2_MB, Balance.PMORT_RELOAD / 20).withStyle(ChatFormatting.GRAY));
    }
}

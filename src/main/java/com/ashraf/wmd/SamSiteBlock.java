package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SamSiteBlock extends UnitBlock {

    public static final MapCodec<SamSiteBlock> CODEC = simpleCodec(SamSiteBlock::new);

    public SamSiteBlock(Properties properties) {
        super(properties, SamSiteBlockEntity.X0, SamSiteBlockEntity.X1, SamSiteBlockEntity.Y0, SamSiteBlockEntity.Y1, SamSiteBlockEntity.Z0, SamSiteBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SamSiteBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, AirBlocks.SAM_SITE_BE.get(), SamSiteBlockEntity::clientTickSam)
                : createTickerHelper(type, AirBlocks.SAM_SITE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof SamSiteBlockEntity be) {
            if (!gate(level, controller, player, be)) return;
            if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        }
        interact(level, controller, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.sam_site", String.format("%,d", (int) Balance.SAM_RANGE)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}

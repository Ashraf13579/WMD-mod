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

public class TeslaTowerBlock extends UnitBlock {

    public static final MapCodec<TeslaTowerBlock> CODEC = simpleCodec(TeslaTowerBlock::new);

    public TeslaTowerBlock(Properties properties) {
        super(properties, TeslaTowerBlockEntity.X0, TeslaTowerBlockEntity.X1, TeslaTowerBlockEntity.Y0, TeslaTowerBlockEntity.Y1, TeslaTowerBlockEntity.Z0, TeslaTowerBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TeslaTowerBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, TeslaBlocks.TESLA_TOWER_BE.get(), TeslaTowerBlockEntity::clientTickTesla)
                : createTickerHelper(type, TeslaBlocks.TESLA_TOWER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof TeslaTowerBlockEntity be) {
            if (!gate(level, controller, player, be)) return;
            if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        }
        interact(level, controller, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.tesla_tower", (int) Balance.TT_RANGE, Balance.TT_CHAIN).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}

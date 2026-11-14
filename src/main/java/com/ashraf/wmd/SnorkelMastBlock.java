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

public class SnorkelMastBlock extends UnitBlock {

    public static final MapCodec<SnorkelMastBlock> CODEC = simpleCodec(SnorkelMastBlock::new);

    public SnorkelMastBlock(Properties properties) { super(properties, 0, 0, 0, 2, 0, 0); }

    @Override public boolean hullPlug(BlockPos local) { return local.getY() == 2; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SnorkelMastBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SNORKEL_MAST_BE.get(), SnorkelMastBlockEntity::clientTickSnorkel)
                : createTickerHelper(type, Wmd.SNORKEL_MAST_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof SnorkelMastBlockEntity be) {
            BlockPos local = Machines.toLocal(controller, level.getBlockState(controller).getValue(FACING), hit.getBlockPos());
            if (CopycatHull.interact(level, hit.getBlockPos(), player, be, local.getY() == 2)) return;
        }
        super.interact(level, controller, player, hit);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.snorkel_mast").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_hull").withStyle(ChatFormatting.GRAY));
    }
}

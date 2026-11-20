package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HoloTableBlock extends CcMapTableBlock {

    public static final MapCodec<HoloTableBlock> CODEC = simpleCodec(HoloTableBlock::new);

    public HoloTableBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HoloTableBlockEntity(pos, state); }

    @Override
    protected BlockEntityType<? extends CcModuleBlockEntity> beType() { return HoloBlocks.HOLO_TABLE_BE.get(); }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof HoloTableBlockEntity t) {
            if (!gate(level, controller, player, t)) return;
            if (player instanceof ServerPlayer sp && !player.isShiftKeyDown() && t.clickBed(sp, hit.getLocation())) return;
        }
        interact(level, controller, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.holo_table").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.holo_table.use").withStyle(ChatFormatting.GRAY));
    }
}

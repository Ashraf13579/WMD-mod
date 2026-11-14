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

public class ScrewPropellerBlock extends UnitBlock implements WrenchUse {

    public static final MapCodec<ScrewPropellerBlock> CODEC = simpleCodec(ScrewPropellerBlock::new);

    public ScrewPropellerBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 1); }

    @Override public boolean hullPlug(BlockPos local) { return true; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newScrew(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SCREW_PROPELLER_BE.get(), ScrewPropellerBlockEntity::clientTickScrew)
                : createTickerHelper(type, Wmd.SCREW_PROPELLER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof ScrewPropellerBlockEntity be) {
            ItemStack held = player.getMainHandItem();
            if (held.is(Wmd.WRENCH.get())) {
                if (player.isShiftKeyDown()) {
                    ItemStack back = be.setCopy(null, ItemStack.EMPTY);
                    if (back != null) {
                        giveBack(player, back);
                        player.displayClientMessage(Component.translatable("message.wmd.screw.skin_reset"), true);
                    }
                } else {
                    be.toggleTail();
                    player.displayClientMessage(Component.translatable(be.tailMode() == 2 ? "message.wmd.screw.no_tail" : be.xTail() ? "message.wmd.screw.x_tail" : "message.wmd.screw.plus_tail"), true);
                }
                level.playSound(null, hit.getBlockPos(), net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.4F);
                return;
            }
            BlockPos local = Machines.toLocal(controller, level.getBlockState(controller).getValue(FACING), hit.getBlockPos());
            if (held.getItem() instanceof net.minecraft.world.item.BlockItem bi && local.getZ() >= 0) {
                BlockState st = bi.getBlock().defaultBlockState();
                if (ScrewPropellerBlockEntity.copyable(st, level, hit.getBlockPos())) {
                    ItemStack back = be.setCopy(st, held);
                    if (back != null) {
                        if (!player.getAbilities().instabuild) held.shrink(1);
                        giveBack(player, back);
                        player.displayClientMessage(Component.translatable("message.wmd.screw.skin", st.getBlock().getName()), true);
                        level.playSound(null, hit.getBlockPos(), st.getSoundType(level, hit.getBlockPos(), player).getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                    }
                    return;
                }
            }
        }
        super.interact(level, controller, player, hit);
    }

    private static void giveBack(net.minecraft.world.entity.player.Player player, ItemStack back) {
        if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.screw_propeller").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.screw_propeller.skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}

package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class C4ChargeBlock extends MachineBlock {

    public static final MapCodec<C4ChargeBlock> CODEC = simpleCodec(C4ChargeBlock::new);
    private static final List<BlockPos> CELL = List.of(BlockPos.ZERO);

    public C4ChargeBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public boolean anyFace() { return true; }
    @Override protected List<BlockPos> footprint() { return CELL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new C4ChargeBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.C4_CHARGE_BE.get(), C4ChargeBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) level.playSound(null, pos, WmdSounds.C4_PLACE.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof C4ChargeBlockEntity be)) return;
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof DetonatorItem) { DetonatorItem.link(held, player, be); return; }
        if (!held.isEmpty()) return;
        if (player.isShiftKeyDown()) {
            if (be.armed()) { player.displayClientMessage(Component.translatable("message.wmd.c4.armed_no_take"), true); return; }
            level.removeBlock(controller, false);
            ItemStack s = new ItemStack(asItem());
            if (!player.addItem(s)) player.drop(s, false);
            return;
        }
        if (be.armed()) { be.disarm(); player.displayClientMessage(Component.translatable("message.wmd.c4.disarmed"), true); }
        else { be.arm(Balance.C4_TIMER); player.displayClientMessage(Component.translatable("message.wmd.c4.timer", Balance.C4_TIMER / 20), true); }
    }
}

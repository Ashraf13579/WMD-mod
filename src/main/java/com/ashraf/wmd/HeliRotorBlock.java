package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HeliRotorBlock extends HeliBlock implements WrenchUse {

    public static final MapCodec<HeliRotorBlock> CODEC = simpleCodec(HeliRotorBlock::new);

    public HeliRotorBlock(Properties properties) { super(properties, "heli_rotor", 1); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.HELI_ROTOR_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newHeliRotor(pos, state); }

    @Override
    protected boolean interactOwn(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof HeliRotorBlockEntity be)) return false;
        ItemStack held = player.getMainHandItem();
        String k;
        if (held.is(Wmd.WRENCH.get())) k = player.isShiftKeyDown() ? be.toggleFold() : be.nextLength();
        else if (held.isEmpty() && !player.isShiftKeyDown()) k = be.nextCount();
        else if (held.isEmpty()) {
            ItemStack back = be.copycat().set(null, ItemStack.EMPTY);
            if (back == null) return false;
            if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
            be.copycatChanged();
            player.displayClientMessage(Component.translatable("message.wmd.air.skin_reset"), true);
            return true;
        } else return false;
        player.displayClientMessage(Component.translatable(k, be.bladeCount(), be.bladeLength()), true);
        level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, Balance.SND_CLICK * 0.6F, 1.4F);
        return true;
    }
}

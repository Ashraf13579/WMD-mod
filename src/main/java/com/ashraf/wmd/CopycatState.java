package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class CopycatState {

    interface Host {
        CopycatState copycat();

        void copycatChanged();
    }

    private BlockState copy, shown;
    private ItemStack item = ItemStack.EMPTY;

    BlockState shown() { return shown; }

    boolean skinned() { return copy != null; }

    ItemStack set(BlockState st, ItemStack used) {
        if (st == null ? copy == null : st.equals(copy)) return null;
        ItemStack back = item;
        copy = st;
        item = st == null || used.isEmpty() ? ItemStack.EMPTY : used.copyWithCount(1);
        return back;
    }

    ItemStack takeItem() {
        ItemStack b = item;
        item = ItemStack.EMPTY;
        return b;
    }

    static boolean interact(Level level, Player player, BlockPos at, Host host) {
        ItemStack held = player.getMainHandItem();
        CopycatState cs = host.copycat();
        if (held.is(Wmd.WRENCH.get()) && player.isShiftKeyDown()) {
            ItemStack back = cs.set(null, ItemStack.EMPTY);
            if (back == null) return false;
            give(player, back);
            host.copycatChanged();
            player.displayClientMessage(Component.translatable("message.wmd.air.skin_reset"), true);
            level.playSound(null, at, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
            return true;
        }
        if (held.getItem() instanceof BlockItem bi) {
            BlockState st = bi.getBlock().defaultBlockState();
            if (!ScrewPropellerBlockEntity.copyable(st, level, at)) return false;
            ItemStack back = cs.set(st, held);
            if (back == null) return true;
            if (!player.getAbilities().instabuild) held.shrink(1);
            give(player, back);
            host.copycatChanged();
            player.displayClientMessage(Component.translatable("message.wmd.air.skin", st.getBlock().getName()), true);
            level.playSound(null, at, st.getSoundType(level, at, player).getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
            return true;
        }
        return false;
    }

    private static void give(Player player, ItemStack back) {
        if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        putSync(tag);
        if (!item.isEmpty()) tag.put("ccItem", item.save(registries));
    }

    void putSync(CompoundTag tag) {
        if (copy != null) tag.put("ccSkin", NbtUtils.writeBlockState(copy));
    }

    void load(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        BlockState st = tag.contains("ccSkin") ? NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("ccSkin")) : null;
        if (st != null && st.isAir()) st = null;
        shown = st;
        if (client) return;
        copy = st;
        item = tag.contains("ccItem") ? ItemStack.parseOptional(registries, tag.getCompound("ccItem")) : ItemStack.EMPTY;
    }
}

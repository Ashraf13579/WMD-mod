package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class CopycatHull {

    private BlockState copy;
    private ItemStack item = ItemStack.EMPTY;

    private BlockState shown;

    interface Owner {
        CopycatHull hull();
        void hullChanged();
    }

    BlockState shown() { return shown; }

    ItemStack set(BlockState st, ItemStack used) {
        if (st == null ? copy == null : st.equals(copy)) return null;
        ItemStack back = item;
        copy = st;
        item = st == null || used.isEmpty() ? ItemStack.EMPTY : used.copyWithCount(1);
        shown = st;
        return back;
    }

    ItemStack take() {
        ItemStack t = item;
        item = ItemStack.EMPTY;
        return t;
    }

    static boolean interact(Level level, BlockPos hitPos, Player player, Owner owner, boolean onHullCell) {
        ItemStack held = player.getMainHandItem();
        CopycatHull h = owner.hull();
        if (held.is(Wmd.WRENCH.get()) && player.isShiftKeyDown()) {
            ItemStack back = h.set(null, ItemStack.EMPTY);
            if (back != null) {
                giveBack(player, back);
                owner.hullChanged();
                player.displayClientMessage(Component.translatable("message.wmd.screw.skin_reset"), true);
                level.playSound(null, hitPos, net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.4F);
            }
            return true;
        }
        if (!onHullCell || !(held.getItem() instanceof BlockItem bi)) return false;
        BlockState st = bi.getBlock().defaultBlockState();
        if (!ScrewPropellerBlockEntity.copyable(st, level, hitPos)) return false;
        ItemStack back = h.set(st, held);
        if (back != null) {
            if (!player.getAbilities().instabuild) held.shrink(1);
            giveBack(player, back);
            owner.hullChanged();
            player.displayClientMessage(Component.translatable("message.wmd.screw.skin", st.getBlock().getName()), true);
            level.playSound(null, hitPos, st.getSoundType(level, hitPos, player).getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return true;
    }

    private static void giveBack(Player player, ItemStack back) {
        if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
    }

    void putSync(CompoundTag tag) {
        if (copy != null) tag.put("ccSkin", net.minecraft.nbt.NbtUtils.writeBlockState(copy));
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        putSync(tag);
        if (!item.isEmpty()) tag.put("ccItem", item.save(registries));
    }

    void load(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        BlockState cp = tag.contains("ccSkin")
                ? net.minecraft.nbt.NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("ccSkin")) : null;
        if (cp != null && cp.isAir()) cp = null;
        shown = cp;
        if (client) return;
        copy = cp;
        item = tag.contains("ccItem") ? ItemStack.parseOptional(registries, tag.getCompound("ccItem")) : ItemStack.EMPTY;
    }
}

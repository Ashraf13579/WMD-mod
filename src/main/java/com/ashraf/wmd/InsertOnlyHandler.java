package com.ashraf.wmd;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public record InsertOnlyHandler(IItemHandler inner) implements IItemHandler {

    @Override public int getSlots() { return inner.getSlots(); }
    @Override public ItemStack getStackInSlot(int slot) { return inner.getStackInSlot(slot); }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return inner.insertItem(slot, stack, simulate); }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
    @Override public int getSlotLimit(int slot) { return inner.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return inner.isItemValid(slot, stack); }
}

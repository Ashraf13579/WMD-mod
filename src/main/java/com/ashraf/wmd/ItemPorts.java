package com.ashraf.wmd;

import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public final class ItemPorts {

    private ItemPorts() {}

    private static boolean feeding;

    private static boolean open() { return feeding || Config.Server.openItemPorts(); }

    public static <T> T asWmd(Supplier<T> action) {
        boolean was = feeding;
        feeding = true;
        try { return action.get(); } finally { feeding = was; }
    }

    public static IItemHandler wmdOnly(IItemHandler h) {
        return h == null ? null : new IItemHandler() {
            @Override public int getSlots() { return h.getSlots(); }
            @Override public ItemStack getStackInSlot(int i) { return h.getStackInSlot(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return open() ? h.insertItem(i, s, sim) : s; }
            @Override public ItemStack extractItem(int i, int n, boolean sim) { return open() ? h.extractItem(i, n, sim) : ItemStack.EMPTY; }
            @Override public int getSlotLimit(int i) { return h.getSlotLimit(i); }
            @Override public boolean isItemValid(int i, ItemStack s) { return open() && h.isItemValid(i, s); }
        };
    }
}

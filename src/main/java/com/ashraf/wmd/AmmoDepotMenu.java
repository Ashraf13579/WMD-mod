package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AmmoDepotMenu extends AbstractContainerMenu {

    static final int SLOTS_Y = 26, INV_Y = 152, HOTBAR_Y = 210;
    private final Container store;
    private final BlockPos depot;

    public AmmoDepotMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) { this(id, inv, new SimpleContainer(Balance.AD_SLOTS) {
        @Override public boolean canPlaceItem(int slot, ItemStack s) { return Ammo.isAmmo(s); }
    }, buf.readBlockPos()); }

    public AmmoDepotMenu(int id, Inventory inv, Container store, BlockPos depot) {
        super(AirBlocks.AMMO_DEPOT_MENU.get(), id);
        this.store = store;
        this.depot = depot;
        store.startOpen(inv.player);
        for (int r = 0; r < 6; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(store, c + r * 9, 8 + c * 18, SLOTS_Y + r * 18) {
            @Override public boolean mayPlace(ItemStack s) { return Ammo.isAmmo(s); }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, HOTBAR_Y));
    }

    public BlockPos depot() { return depot; }

    @Override
    public boolean stillValid(Player player) { return store.stillValid(player); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        store.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        int n = Balance.AD_SLOTS;
        if (index < n) {
            if (!moveItemStackTo(s, n, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!Ammo.isAmmo(s) || !moveItemStackTo(s, 0, n, false)) return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}

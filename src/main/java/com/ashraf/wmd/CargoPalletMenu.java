package com.ashraf.wmd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class CargoPalletMenu extends AbstractContainerMenu {

    static final int VIEW = 54, SLOTS_Y = 26, B_COMP = 0, B_UNPACK = 9;

    interface Opener {
        void open(ServerPlayer sp, int compartment);
        boolean stillValid(Player player);
        Vec3 where();
    }

    private final Container window;
    private final Opener opener;
    private final int compartment, comps, rows, total;
    private final boolean heavy;

    static int compartments(int total) { return Math.max(1, (total + VIEW - 1) / VIEW); }
    static int viewSize(int total) { return Math.min(VIEW, total); }
    int rows() { return rows; }
    int compartment() { return compartment; }
    int compartmentCount() { return comps; }
    boolean heavy() { return heavy; }
    int invY() { return SLOTS_Y + rows * 18 + 16; }

    static void writeOpen(RegistryFriendlyByteBuf buf, int total, int compartment, boolean heavy) { buf.writeVarInt(total); buf.writeVarInt(compartment); buf.writeBoolean(heavy); }

    public CargoPalletMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, clientStore(buf.readVarInt()), buf.readVarInt(), buf.readBoolean(), null);
    }

    private static SimpleContainer clientStore(int total) { return new SimpleContainer(Math.max(1, Math.min(Balance.PALLET_SLOTS[1], total))); }

    public CargoPalletMenu(int id, Inventory inv, Container store, int compartment, boolean heavy, Opener opener) {
        super(CargoBlocks.CARGO_PALLET_MENU.get(), id);
        this.total = store.getContainerSize();
        this.comps = compartments(total);
        this.compartment = Math.max(0, Math.min(comps - 1, compartment));
        this.heavy = heavy;
        this.opener = opener;
        int off = this.compartment * VIEW, n = Math.min(VIEW, total - off);
        this.window = opener == null ? new SimpleContainer(n) : new Window(store, off, n);
        this.rows = (n + 8) / 9;
        window.startOpen(inv.player);
        for (int i = 0; i < n; i++) addSlot(new Slot(window, i, 8 + (i % 9) * 18, SLOTS_Y + (i / 9) * 18) {
            @Override public boolean mayPlace(ItemStack s) { return CargoPalletItem.fits(s); }
        });
        int iy = invY();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, iy + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, iy + 58));
    }

    @Override
    public boolean stillValid(Player player) { return opener == null || opener.stillValid(player); }

    @Override
    public void removed(Player player) {
        super.removed(player);
        window.stopOpen(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (opener == null || !(player instanceof ServerPlayer sp) || !opener.stillValid(player)) return false;
        if (id >= B_COMP && id < B_COMP + comps && id - B_COMP != compartment) { opener.open(sp, id - B_COMP); return true; }
        if (id == B_UNPACK) { unpack(sp); return true; }
        return false;
    }

    private void unpack(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel sl)) return;
        Vec3 at = opener.where();
        AmmoDepotBlockEntity d = at == null ? null : AmmoDepots.nearest(sl, at, sp.getUUID());
        if (d == null) { sp.displayClientMessage(Component.translatable("message.wmd.pallet.no_depot", (int) Balance.AD_RANGE), true); return; }
        int moved = 0;
        for (int i = 0; i < window.getContainerSize(); i++) {
            ItemStack s = window.getItem(i);
            if (!Ammo.isAmmo(s)) continue;
            int before = s.getCount();
            ItemStack rest = s.copy();
            if (d.giveBack(rest)) rest = ItemStack.EMPTY;
            moved += before - rest.getCount();
            window.setItem(i, rest);
        }
        sp.displayClientMessage(Component.translatable(moved > 0 ? "message.wmd.pallet.unpacked" : "message.wmd.pallet.no_ammo", moved), true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        int n = window.getContainerSize();
        if (index < n) {
            if (!moveItemStackTo(s, n, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!CargoPalletItem.fits(s) || !moveItemStackTo(s, 0, n, false)) return ItemStack.EMPTY;
        }
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    static final class Window implements Container {
        private final Container base;
        private final int off, size;
        Window(Container base, int off, int size) { this.base = base; this.off = off; this.size = size; }
        @Override public int getContainerSize() { return size; }
        @Override public boolean isEmpty() { for (int i = 0; i < size; i++) if (!base.getItem(off + i).isEmpty()) return false; return true; }
        @Override public ItemStack getItem(int i) { return i < 0 || i >= size ? ItemStack.EMPTY : base.getItem(off + i); }
        @Override public ItemStack removeItem(int i, int n) { return i < 0 || i >= size ? ItemStack.EMPTY : base.removeItem(off + i, n); }
        @Override public ItemStack removeItemNoUpdate(int i) { return i < 0 || i >= size ? ItemStack.EMPTY : base.removeItemNoUpdate(off + i); }
        @Override public void setItem(int i, ItemStack s) { if (i >= 0 && i < size) base.setItem(off + i, s); }
        @Override public void setChanged() { base.setChanged(); }
        @Override public boolean stillValid(Player p) { return true; }
        @Override public boolean canPlaceItem(int i, ItemStack s) { return CargoPalletItem.fits(s); }
        @Override public void clearContent() { for (int i = 0; i < size; i++) base.setItem(off + i, ItemStack.EMPTY); }
    }
}

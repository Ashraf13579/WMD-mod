package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SuitRackMenu extends AbstractContainerMenu {

    public static final int AMMO = 9, SLOTS = 10;

    static final int[][] POS = { { 62, 27 }, { 82, 27 }, { 62, 51 }, { 82, 51 }, { 102, 51 }, { 122, 51 }, { 142, 51 }, { 62, 75 }, { 62, 99 }, { 166, 51 } };

    final SuitRackBlockEntity rack;
    private final BlockPos pos;

    public SuitRackMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    private SuitRackMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof SuitRackBlockEntity be ? be : null, pos);
    }

    SuitRackMenu(int id, Inventory inv, SuitRackBlockEntity rack, BlockPos pos) {
        super(ExoSuitBlocks.SUIT_RACK_MENU.get(), id);
        this.rack = rack;
        this.pos = pos;
        Container c = new Modules();
        for (int i = 0; i < SLOTS; i++) {
            final int k = i;
            addSlot(new Slot(c, i, POS[i][0], POS[i][1]) {
                @Override public boolean mayPlace(ItemStack s) { return accepts(k, s); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean isActive() { return piecePresent(k); }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 128 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 186));
    }

    boolean piecePresent(int k) {
        if (rack == null) return false;
        return !rack.piece(k == AMMO ? 1 : ExoModule.ALL.get(k).piece()).isEmpty();
    }

    boolean accepts(int k, ItemStack s) {
        if (!piecePresent(k) || s.isEmpty()) return false;
        if (k == AMMO) return s.is(ExoSuitBlocks.MICRO_MISSILES.get()) && ExoModule.MISSILES.on(rack.piece(1));
        return s.getItem() instanceof ExoModuleItem mi && mi.module == ExoModule.ALL.get(k);
    }

    private final class Modules implements Container {
        @Override public int getContainerSize() { return SLOTS; }
        @Override public boolean isEmpty() { for (int i = 0; i < SLOTS; i++) if (!getItem(i).isEmpty()) return false; return true; }

        @Override
        public ItemStack getItem(int i) {
            if (rack == null || !piecePresent(i)) return ItemStack.EMPTY;
            if (i == AMMO) return rack.piece(1).getOrDefault(ExoSuitBlocks.EXO_AMMO.get(), 0) > 0 ? new ItemStack(ExoSuitBlocks.MICRO_MISSILES.get()) : ItemStack.EMPTY;
            ExoModule m = ExoModule.ALL.get(i);
            return m.on(rack.piece(m.piece())) ? new ItemStack(ExoSuitBlocks.MODULES.get(m).get()) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            ItemStack had = getItem(i);
            if (had.isEmpty() || n <= 0) return ItemStack.EMPTY;
            setItem(i, ItemStack.EMPTY);
            return had;
        }

        @Override public ItemStack removeItemNoUpdate(int i) { return removeItem(i, 1); }

        @Override
        public void setItem(int i, ItemStack s) {
            if (rack == null || !piecePresent(i)) return;
            if (i == AMMO) {
                ItemStack chest = rack.piece(1);
                if (s.isEmpty()) chest.remove(ExoSuitBlocks.EXO_AMMO.get());
                else if (s.is(ExoSuitBlocks.MICRO_MISSILES.get())) chest.set(ExoSuitBlocks.EXO_AMMO.get(), Balance.EXO_MSL_SALVO);
            } else {
                ExoModule m = ExoModule.ALL.get(i);
                ExoModule.set(rack.piece(m.piece()), m, !s.isEmpty() && s.getItem() instanceof ExoModuleItem mi && mi.module == m);
            }
            setChanged();
        }

        @Override public void setChanged() { if (rack != null) rack.piecesChanged(); }
        @Override public boolean stillValid(Player p) { return SuitRackMenu.this.stillValid(p); }
        @Override public void clearContent() {}
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (rack == null || rack.piece(1).isEmpty() || !ExoModule.RF.on(rack.piece(1))) return false;
        ItemStack chest = rack.piece(1);
        int ch = chest.getOrDefault(ExoSuitBlocks.EXO_RF.get(), 0);
        int[] step = { -10, -1, 1, 10 };
        if (id < 0 || id >= step.length) return false;
        ch = Math.max(0, Math.min(9999, ch + step[id]));
        if (ch == 0) chest.remove(ExoSuitBlocks.EXO_RF.get()); else chest.set(ExoSuitBlocks.EXO_RF.get(), ch);
        rack.piecesChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < SLOTS) {
            Slot sl = slots.get(index);
            ItemStack s = sl.getItem();
            if (s.isEmpty()) return ItemStack.EMPTY;
            ItemStack copy = s.copy();
            if (!moveItemStackTo(copy, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
            sl.set(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        Slot from = slots.get(index);
        ItemStack s = from.getItem();
        for (int k = 0; k < SLOTS; k++) {
            if (!accepts(k, s) || !slots.get(k).getItem().isEmpty()) continue;
            slots.get(k).set(s.split(1));
            from.setChanged();
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return rack != null && !rack.isRemoved() && Reach.within(player, pos, 8.0) && Access.mayUse(rack, player);
    }
}

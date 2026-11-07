package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class RackMenu extends AbstractContainerMenu {

    public static final int RACKS = BayLine.RACKS;
    public static final int SLOT_X = 16, SLOT_STEP = 24;
    public static final int[] ROW_Y = { 32, 72, 112, 152 };
    public static final int HINT_Y = 184, INV_Y = 206, HOTBAR_Y = 264;
    public static final int PER_RACK = BayModuleBlockEntity.RACK_SLOTS, TOTAL = PER_RACK * RACKS, PLAYER_END = TOTAL + 36;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final boolean[] has;

    public RackMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, empties(), buf.readBlockPos(), readHas(buf));
    }

    private static IItemHandler[] empties() {
        IItemHandler[] h = new IItemHandler[RACKS];
        for (int i = 0; i < RACKS; i++) h[i] = new ItemStackHandler(PER_RACK);
        return h;
    }

    private static boolean[] readHas(RegistryFriendlyByteBuf buf) {
        boolean[] b = new boolean[RACKS];
        for (int i = 0; i < RACKS; i++) b[i] = buf.readBoolean();
        return b;
    }

    public RackMenu(int id, Inventory inventory, BayModuleBlockEntity rack) {
        this(id, inventory, handlers(rack), rack.getBlockPos(), present(rack));
    }

    private static BayModuleBlockEntity rackAt(BayModuleBlockEntity clicked, int index) {
        if (clicked.rackIndex() == index) return clicked;
        BayModuleBlockEntity core = clicked.core();
        return core == null ? null : BayLine.rackOf(core, index);
    }

    private static IItemHandler[] handlers(BayModuleBlockEntity clicked) {
        IItemHandler[] h = new IItemHandler[RACKS];
        for (int i = 0; i < RACKS; i++) { BayModuleBlockEntity r = rackAt(clicked, i); h[i] = r == null ? new ItemStackHandler(PER_RACK) : r.rackHandler(); }
        return h;
    }

    private static boolean[] present(BayModuleBlockEntity clicked) {
        boolean[] b = new boolean[RACKS];
        for (int i = 0; i < RACKS; i++) b[i] = rackAt(clicked, i) != null;
        return b;
    }

    private RackMenu(int id, Inventory inventory, IItemHandler[] racks, BlockPos pos, boolean[] has) {
        super(Wmd.RACK_MENU.get(), id);
        this.pos = pos;
        this.has = has;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int row = 0; row < RACKS; row++) {
            final boolean on = has[row];
            for (int i = 0; i < PER_RACK; i++) addSlot(new SlotItemHandler(racks[row], i, SLOT_X + i * SLOT_STEP, ROW_Y[row]) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean isActive() { return on; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
    }

    public static void open(Player player, BayModuleBlockEntity rack) {
        if (!(player instanceof ServerPlayer sp)) return;
        final boolean[] has = present(rack);
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.bay_rack"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new RackMenu(id, inv, rack); }
        }, buf -> { buf.writeBlockPos(rack.getBlockPos()); for (boolean b : has) buf.writeBoolean(b); });
    }

    public BlockPos getPos() { return pos; }

    public boolean has(int i) { return i >= 0 && i < RACKS && has[i]; }

    public int capacity() { int n = 0; for (boolean b : has) if (b) n += PER_RACK; return n; }

    public int count() {
        int n = 0;
        for (int i = 0; i < TOTAL; i++) if (slots.get(i).isActive() && slots.get(i).hasItem()) n++;
        return n;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= TOTAL) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, TOTAL, PLAYER_END, true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof BayModuleBlock.Rack
                && level.getBlockEntity(p) instanceof BayModuleBlockEntity rack && BayLine.nearModule(player, rack), true);
    }
}

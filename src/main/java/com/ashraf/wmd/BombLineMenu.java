package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BombLineMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 14;
    public static final int GRID_X = 8, GRID_Y = 36, PAL_X = 12, PAL_Y = 40, CRADLE_X = 128, CRADLE_Y = 66, INV_Y = 152, HOTBAR_Y = 210;
    private static final int PALLETS = BombLineBlockEntity.PALLETS;
    private static final int MACHINE_SLOTS = 9 + PALLETS + 1, PLAYER_END = MACHINE_SLOTS + 36;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public int tab;
    private int[] lastResult;

    public BombLineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(9), readOnly(PALLETS), readOnly(1), new SimpleContainerData(DATA_COUNT), buf.readBlockPos());
    }

    private static ItemStackHandler readOnly(int n) {
        return new ItemStackHandler(n) { @Override public boolean isItemValid(int slot, ItemStack s) { return false; } };
    }

    public BombLineMenu(int id, Inventory inventory, BombLineBlockEntity core) { this(id, inventory, core, new int[1]); }

    public BlockPos getPos() { return pos; }

    public static int palX(int i) { return PAL_X + (i % 2) * 40; }
    public static int palY(int i) { return PAL_Y + (2 - i / 2) * 26; }

    private static IItemHandlerModifiable view(BombLineBlockEntity core, BombLineKind k, int n) {
        return new IItemHandlerModifiable() {
            private ItemStackHandler h() { BombLineBlockEntity m = BombLine.module(core, k); return m == null ? null : k == BombLineKind.STORAGE ? m.racked : m.parked; }
            @Override public int getSlots() { return n; }
            @Override public ItemStack getStackInSlot(int i) { var h = h(); return h == null ? ItemStack.EMPTY : h.getStackInSlot(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return s; }
            @Override public ItemStack extractItem(int i, int amount, boolean sim) { var h = h(); return h == null ? ItemStack.EMPTY : h.extractItem(i, amount, sim); }
            @Override public void setStackInSlot(int i, ItemStack s) { var h = h(); if (h != null) h.setStackInSlot(i, s); }
            @Override public int getSlotLimit(int i) { return 64; }
            @Override public boolean isItemValid(int i, ItemStack s) { return false; }
        };
    }

    private static int count(BombLineBlockEntity core, BombLineKind k) {
        BombLineBlockEntity m = BombLine.module(core, k);
        if (m == null) return 0;
        ItemStackHandler h = k == BombLineKind.STORAGE ? m.racked : m.parked;
        int c = 0;
        for (int i = 0; i < h.getSlots(); i++) c += h.getStackInSlot(i).getCount();
        return c;
    }

    private BombLineMenu(int id, Inventory inventory, BombLineBlockEntity core, int[] result) {
        this(id, inventory, core.inputs, view(core, BombLineKind.STORAGE, PALLETS), view(core, BombLineKind.CRADLE, 1), new ContainerData() {
            @Override public int get(int i) {
                var level = core.getLevel();
                return switch (i) {
                    case 0 -> core.stage().ordinal();
                    case 1 -> level == null ? 0 : (int) (core.runProgress(level) * 1000);
                    case 2 -> core.typeIndex();
                    case 3 -> core.energy() & 0xFFFF;
                    case 4 -> core.energy() >>> 16;
                    case 5 -> BombLine.mask(core);
                    case 6 -> core.blocker();
                    case 7 -> BombLineBuilder.placeableMask(core);
                    case 8 -> BombLineBuilder.removableMask(core);
                    case 9 -> result[0];
                    case 10 -> count(core, BombLineKind.STORAGE);
                    case 11 -> count(core, BombLineKind.CRADLE);
                    case 12 -> core.auto() ? 1 : 0;
                    default -> core.filling() == null ? -1 : core.filling().naphthaMb();
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, core.getBlockPos());
        this.lastResult = result;
    }

    private BombLineMenu(int id, Inventory inventory, IItemHandler inputs, IItemHandler pallets, IItemHandler cradle, ContainerData data, BlockPos pos) {
        super(Wmd.BOMB_LINE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlot(new SlotItemHandler(inputs, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18) {
            @Override public boolean isActive() { return tab == 0; }
        });
        for (int i = 0; i < PALLETS; i++) addSlot(new SlotItemHandler(pallets, i, palX(i), palY(i)) {
            @Override public boolean isActive() { return tab == 2; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new SlotItemHandler(cradle, 0, CRADLE_X, CRADLE_Y) {
            @Override public boolean isActive() { return tab == 2; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, BombLineBlockEntity core) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.bomb_line_core"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new BombLineMenu(id, inv, core); }
        }, buf -> buf.writeBlockPos(core.getBlockPos()));
    }

    public BombLineBlockEntity.Stage stage() { return BombLineBlockEntity.Stage.values()[Math.max(0, Math.min(data.get(0), BombLineBlockEntity.Stage.values().length - 1))]; }
    public float progress() { return data.get(1) / 1000.0F; }
    public BombType type() { return BombType.ofOrdinal(data.get(2)); }
    public int energy() { return (data.get(3) & 0xFFFF) | (data.get(4) << 16); }
    public int lineMask() { return data.get(5); }
    public int blocker() { return data.get(6); }
    public int placeableMask() { return data.get(7); }
    public int removableMask() { return data.get(8); }
    public boolean running() { return stage() != BombLineBlockEntity.Stage.IDLE; }
    public LabBuilder.Result result() { return LabBuilder.Result.of(data.get(9) & 255); }
    public int resultArg() { return data.get(9) >> 8 & 127; }
    public int onPallets() { return data.get(10); }
    public int onCradle() { return data.get(11); }
    public boolean auto() { return data.get(12) != 0; }

    public int naphtha() { return (short) data.get(13); }

    public int have(BombLineRecipes.Need n) {
        int sum = 0;
        for (int i = 0; i < 9; i++) { ItemStack s = slots.get(i).getItem(); if (n.test().test(s)) sum += s.getCount(); }
        return sum;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof BombLineBlockEntity core) || core.kind() != BombLineKind.CORE) return false;
        if (id >= 8) {
            int s = id < 16 ? id - 8 : id - 16;
            if (s < 0 || s >= BombLineBuilder.Socket.values().length) return false;
            if (id >= 16 && !Access.mayMine(core, sp)) { Access.deny(core, sp, CcAlarms.BREAK); return false; }
            BombLineBuilder.Socket sock = BombLineBuilder.Socket.values()[s];
            LabBuilder.Outcome o = id < 16 ? BombLineBuilder.place(this, sp, core, sock) : BombLineBuilder.remove(sp, core, sock);
            if (lastResult != null) lastResult[0] = o.result().ordinal() | o.arg() << 8;
            return true;
        }
        switch (id) {
            case 0 -> core.cycleType(true);
            case 1 -> core.cycleType(false);
            case 2 -> core.startRun(player);
            case 3 -> core.toggleAuto();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (!BombLineRecipes.accepts(stack) || !moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof BombLineBlock.Core
                && level.getBlockEntity(p) instanceof BombLineBlockEntity c && BombLine.near(c, player), true);
    }
}

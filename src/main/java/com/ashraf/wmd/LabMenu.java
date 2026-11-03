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

public class LabMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 13;
    public static final int GRID_X = 8, GRID_Y = 36, STORE_X = 12, STORE_Y = 40, INV_Y = 152, HOTBAR_Y = 210;
    private static final int MACHINE_SLOTS = 19, PLAYER_END = MACHINE_SLOTS + 36;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public int tab;

    private int[] lastResult;

    public LabMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(9), new ItemStackHandler(LabModuleBlockEntity.SLOTS) {
            @Override public boolean isItemValid(int slot, ItemStack s) { return false; }
        }, new SimpleContainerData(DATA_COUNT), buf.readBlockPos());
    }

    public LabMenu(int id, Inventory inventory, LabModuleBlockEntity core) {
        this(id, inventory, core, new int[1]);
    }

    public BlockPos getPos() { return pos; }

    private LabMenu(int id, Inventory inventory, LabModuleBlockEntity core, int[] result) {
        this(id, inventory, core.inputs, new IItemHandlerModifiable() {

            private IItemHandler st() { return LabLine.module(core, LabKind.STORAGE) instanceof LabModuleBlockEntity s ? s.racked : null; }
            @Override public int getSlots() { return LabModuleBlockEntity.SLOTS; }
            @Override public ItemStack getStackInSlot(int i) { var h = st(); return h == null ? ItemStack.EMPTY : h.getStackInSlot(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return s; }
            @Override public ItemStack extractItem(int i, int n, boolean sim) { var h = st(); return h == null ? ItemStack.EMPTY : h.extractItem(i, n, sim); }
            @Override public void setStackInSlot(int i, ItemStack s) { if (st() instanceof IItemHandlerModifiable m) m.setStackInSlot(i, s); }
            @Override public int getSlotLimit(int i) { return 1; }
            @Override public boolean isItemValid(int i, ItemStack s) { return false; }
        }, new ContainerData() {
            @Override public int get(int i) {
                var level = core.getLevel();
                return switch (i) {
                    case 0 -> core.stage().ordinal();
                    case 1 -> level == null ? 0 : (int) (core.progress(level) * 1000);
                    case 2 -> core.typeIndex();
                    case 3 -> core.energy() & 0xFFFF;
                    case 4 -> core.energy() >>> 16;
                    case 5 -> LabLine.mask(core);
                    case 6 -> core.blocker();
                    case 7 -> LabBuilder.placeableMask(core);
                    case 8 -> LabBuilder.removableMask(core);
                    case 9 -> result[0];
                    case 11 -> (core.auto() ? 1 : 0) | (core.autoRun() ? 2 : 0) | core.autoBlocker() << 4;
                    case 12 -> core.heavyWater();
                    default -> {
                        int n = 0;
                        if (LabLine.module(core, LabKind.STORAGE) instanceof LabModuleBlockEntity s) for (int k = 0; k < LabModuleBlockEntity.SLOTS; k++) if (!s.cradle(k).isEmpty()) n++;
                        yield n;
                    }
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, core.getBlockPos());
        this.lastResult = result;
    }

    private LabMenu(int id, Inventory inventory, IItemHandler inputs, IItemHandler storage, ContainerData data, BlockPos pos) {
        super(Wmd.LAB_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlot(new SlotItemHandler(inputs, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18) {
            @Override public boolean isActive() { return tab == 0; }
        });
        for (int i = 0; i < LabModuleBlockEntity.SLOTS; i++) addSlot(new SlotItemHandler(storage, i, STORE_X + (i % 5) * 30, STORE_Y + (i / 5) * 30) {
            @Override public boolean isActive() { return tab == 2; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, LabModuleBlockEntity core) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.lab_core"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new LabMenu(id, inv, core); }
        }, buf -> buf.writeBlockPos(core.getBlockPos()));
    }

    public LabModuleBlockEntity.Stage stage() { return LabModuleBlockEntity.Stage.values()[Math.max(0, Math.min(data.get(0), LabModuleBlockEntity.Stage.values().length - 1))]; }
    public float progress() { return data.get(1) / 1000.0F; }
    public int typeIndex() { return data.get(2); }
    public WarheadType type() { return WarheadType.ofOrdinal(data.get(2)); }
    public int energy() { return (data.get(3) & 0xFFFF) | (data.get(4) << 16); }

    public int lineMask() { return data.get(5); }
    public int blocker() { return data.get(6); }
    public int placeableMask() { return data.get(7); }
    public int removableMask() { return data.get(8); }
    public boolean running() { return stage() != LabModuleBlockEntity.Stage.IDLE; }
    public LabBuilder.Result result() { return LabBuilder.Result.of(data.get(9) & 255); }
    public int resultArg() { return data.get(9) >> 8 & 127; }
    public int stored() { return data.get(10); }
    public boolean auto() { return (data.get(11) & 1) != 0; }
    public boolean autoRun() { return (data.get(11) & 2) != 0; }
    public int autoBlocker() { return data.get(11) >> 4 & 15; }
    public int heavyWater() { return data.get(12); }

    public int have(LabRecipes.Need n) {
        int sum = 0;
        for (int i = 0; i < 9; i++) { ItemStack s = slots.get(i).getItem(); if (n.test().test(s)) sum += s.getCount(); }
        return sum;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof LabModuleBlockEntity core) || core.kind() != LabKind.CORE) return false;
        if (id >= 8) {
            int s = id < 16 ? id - 8 : id - 16;
            if (s < 0 || s >= LabBuilder.Socket.values().length) return false;
            if (id >= 16 && !Access.mayMine(core, sp)) { Access.deny(core, sp, CcAlarms.BREAK); return false; }
            LabBuilder.Socket sock = LabBuilder.Socket.values()[s];
            LabBuilder.Outcome o = id < 16 ? LabBuilder.place(this, sp, core, sock) : LabBuilder.remove(sp, core, sock);
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
        } else if (!moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof LabModuleBlock.Core
                && level.getBlockEntity(p) instanceof LabModuleBlockEntity c && LabLine.near(c, player), true);
    }
}

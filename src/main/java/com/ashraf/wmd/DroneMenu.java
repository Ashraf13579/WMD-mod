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

public class DroneMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 13;
    public static final int GRID_X = 8, GRID_Y = 36, SHELF_X = 12, SHELF_Y = 40, BAY_X = 116, BAY_Y = 40, INV_Y = 152, HOTBAR_Y = 210;
    private static final int SHELVES = DroneModuleBlockEntity.SHELVES, BAYS = DroneModuleBlockEntity.BAYS;
    private static final int MACHINE_SLOTS = 9 + SHELVES + BAYS, PLAYER_END = MACHINE_SLOTS + 36;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public int tab;
    private int[] lastResult;

    public DroneMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(9), readOnly(SHELVES), readOnly(BAYS), new SimpleContainerData(DATA_COUNT), buf.readBlockPos());
    }

    private static ItemStackHandler readOnly(int n) {
        return new ItemStackHandler(n) { @Override public boolean isItemValid(int slot, ItemStack s) { return false; } };
    }

    public DroneMenu(int id, Inventory inventory, DroneModuleBlockEntity core) { this(id, inventory, core, new int[1]); }

    public BlockPos getPos() { return pos; }

    private static IItemHandlerModifiable view(DroneModuleBlockEntity core, DroneKind k, int n) {
        return new IItemHandlerModifiable() {
            private ItemStackHandler h() { DroneModuleBlockEntity m = DroneLine.module(core, k); return m == null ? null : k == DroneKind.STORAGE ? m.racked : m.parked; }
            @Override public int getSlots() { return n; }
            @Override public ItemStack getStackInSlot(int i) { var h = h(); return h == null ? ItemStack.EMPTY : h.getStackInSlot(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return s; }
            @Override public ItemStack extractItem(int i, int amount, boolean sim) { var h = h(); return h == null ? ItemStack.EMPTY : h.extractItem(i, amount, sim); }
            @Override public void setStackInSlot(int i, ItemStack s) { var h = h(); if (h != null) h.setStackInSlot(i, s); }
            @Override public int getSlotLimit(int i) { return 1; }
            @Override public boolean isItemValid(int i, ItemStack s) { return false; }
        };
    }

    private static int used(DroneModuleBlockEntity core, DroneKind k) {
        DroneModuleBlockEntity m = DroneLine.module(core, k);
        if (m == null) return 0;
        ItemStackHandler h = k == DroneKind.STORAGE ? m.racked : m.parked;
        int c = 0;
        for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) c++;
        return c;
    }

    private DroneMenu(int id, Inventory inventory, DroneModuleBlockEntity core, int[] result) {
        this(id, inventory, core.inputs, view(core, DroneKind.STORAGE, SHELVES), view(core, DroneKind.HANGAR, BAYS), new ContainerData() {
            @Override public int get(int i) {
                var level = core.getLevel();
                return switch (i) {
                    case 0 -> core.stage().ordinal();
                    case 1 -> level == null ? 0 : (int) (core.runProgress(level) * 1000);
                    case 2 -> core.typeIndex();
                    case 3 -> core.energy() & 0xFFFF;
                    case 4 -> core.energy() >>> 16;
                    case 5 -> DroneLine.mask(core);
                    case 6 -> core.blocker();
                    case 7 -> DroneBuilder.placeableMask(core);
                    case 8 -> DroneBuilder.removableMask(core);
                    case 9 -> result[0];
                    case 10 -> used(core, DroneKind.STORAGE);
                    case 11 -> used(core, DroneKind.HANGAR);
                    default -> core.auto() ? 1 : 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, core.getBlockPos());
        this.lastResult = result;
    }

    private DroneMenu(int id, Inventory inventory, IItemHandler inputs, IItemHandler shelves, IItemHandler bays, ContainerData data, BlockPos pos) {
        super(Wmd.DRONE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlot(new SlotItemHandler(inputs, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18) {
            @Override public boolean isActive() { return tab == 0; }
        });
        for (int i = 0; i < SHELVES; i++) addSlot(new SlotItemHandler(shelves, i, SHELF_X + (i % 3) * 26, SHELF_Y + (2 - i / 3) * 26) {
            @Override public boolean isActive() { return tab == 2; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int i = 0; i < BAYS; i++) addSlot(new SlotItemHandler(bays, i, BAY_X + i * 26, BAY_Y + 26) {
            @Override public boolean isActive() { return tab == 2; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, DroneModuleBlockEntity core) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.drone_core"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new DroneMenu(id, inv, core); }
        }, buf -> buf.writeBlockPos(core.getBlockPos()));
    }

    public DroneModuleBlockEntity.Stage stage() { return DroneModuleBlockEntity.Stage.values()[Math.max(0, Math.min(data.get(0), DroneModuleBlockEntity.Stage.values().length - 1))]; }
    public float progress() { return data.get(1) / 1000.0F; }
    public DroneType type() { return DroneType.of(data.get(2)); }
    public int energy() { return (data.get(3) & 0xFFFF) | (data.get(4) << 16); }
    public int lineMask() { return data.get(5); }
    public int blocker() { return data.get(6); }
    public int placeableMask() { return data.get(7); }
    public int removableMask() { return data.get(8); }
    public boolean running() { return stage() != DroneModuleBlockEntity.Stage.IDLE; }
    public LabBuilder.Result result() { return LabBuilder.Result.of(data.get(9) & 255); }
    public int resultArg() { return data.get(9) >> 8 & 127; }
    public int shelvesUsed() { return data.get(10); }
    public int baysUsed() { return data.get(11); }
    public boolean auto() { return data.get(12) != 0; }

    public int have(DroneRecipes.Need n) {
        int sum = 0;
        for (int i = 0; i < 9; i++) { ItemStack s = slots.get(i).getItem(); if (n.test(s)) sum += s.getCount(); }
        return sum;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof DroneModuleBlockEntity core) || core.kind() != DroneKind.CORE) return false;
        if (id >= 8) {
            int s = id < 16 ? id - 8 : id - 16;
            if (s < 0 || s >= DroneBuilder.Socket.values().length) return false;
            if (id >= 16 && !Access.mayMine(core, sp)) { Access.deny(core, sp, CcAlarms.BREAK); return false; }
            DroneBuilder.Socket sock = DroneBuilder.Socket.values()[s];
            LabBuilder.Outcome o = id < 16 ? DroneBuilder.place(this, sp, core, sock) : DroneBuilder.remove(sp, core, sock);
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
        } else if (!DroneRecipes.accepts(stack) || !moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof DroneModuleBlock.Core
                && level.getBlockEntity(p) instanceof DroneModuleBlockEntity c && DroneLine.near(c, player), true);
    }
}

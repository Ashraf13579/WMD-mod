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

public class TorpedoLineMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 12;
    public static final int GRID_X = 8, GRID_Y = 36, RACK_X = 40, RACK_Y = 52, INV_Y = 152, HOTBAR_Y = 210;
    private static final int RACK = Balance.TLINE_RACK;
    private static final int MACHINE_SLOTS = 9 + RACK, PLAYER_END = MACHINE_SLOTS + 36;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public int tab;
    private int[] lastResult;

    public TorpedoLineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(9), readOnly(RACK), new SimpleContainerData(DATA_COUNT), buf.readBlockPos());
    }

    private static ItemStackHandler readOnly(int n) {
        return new ItemStackHandler(n) { @Override public boolean isItemValid(int slot, ItemStack s) { return false; } };
    }

    public TorpedoLineMenu(int id, Inventory inventory, TorpedoLineBlockEntity core) { this(id, inventory, core, new int[1]); }

    public BlockPos getPos() { return pos; }

    public static int rackX(int i) { return RACK_X + (i % 2) * 40; }
    public static int rackY(int i) { return RACK_Y + (1 - i / 2) * 26; }

    private static IItemHandlerModifiable view(TorpedoLineBlockEntity core) {
        return new IItemHandlerModifiable() {
            private ItemStackHandler h() { TorpedoLineBlockEntity m = TorpedoLine.module(core, TorpedoLineKind.TEST); return m == null ? null : m.racked; }
            @Override public int getSlots() { return RACK; }
            @Override public ItemStack getStackInSlot(int i) { var h = h(); return h == null ? ItemStack.EMPTY : h.getStackInSlot(i); }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return s; }
            @Override public ItemStack extractItem(int i, int amount, boolean sim) { var h = h(); return h == null ? ItemStack.EMPTY : h.extractItem(i, amount, sim); }
            @Override public void setStackInSlot(int i, ItemStack s) { var h = h(); if (h != null) h.setStackInSlot(i, s); }
            @Override public int getSlotLimit(int i) { return 1; }
            @Override public boolean isItemValid(int i, ItemStack s) { return false; }
        };
    }

    private static int count(TorpedoLineBlockEntity core) {
        TorpedoLineBlockEntity m = TorpedoLine.module(core, TorpedoLineKind.TEST);
        if (m == null) return 0;
        int c = 0;
        for (int i = 0; i < RACK; i++) c += m.racked.getStackInSlot(i).getCount();
        return c;
    }

    private TorpedoLineMenu(int id, Inventory inventory, TorpedoLineBlockEntity core, int[] result) {
        this(id, inventory, core.inputs, view(core), new ContainerData() {
            @Override public int get(int i) {
                var level = core.getLevel();
                return switch (i) {
                    case 0 -> core.stage().ordinal();
                    case 1 -> level == null ? 0 : (int) (core.runProgress(level) * 1000);
                    case 2 -> core.typeIndex();
                    case 3 -> core.energy() & 0xFFFF;
                    case 4 -> core.energy() >>> 16;
                    case 5 -> TorpedoLine.mask(core);
                    case 6 -> core.blocker();
                    case 7 -> TorpedoLineBuilder.placeableMask(core);
                    case 8 -> TorpedoLineBuilder.removableMask(core);
                    case 9 -> result[0];
                    case 10 -> count(core);
                    default -> core.auto() ? 1 : 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, core.getBlockPos());
        this.lastResult = result;
    }

    private TorpedoLineMenu(int id, Inventory inventory, IItemHandler inputs, IItemHandler rack, ContainerData data, BlockPos pos) {
        super(Wmd.TORPEDO_LINE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlot(new SlotItemHandler(inputs, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18) {
            @Override public boolean isActive() { return tab == 0; }
        });
        for (int i = 0; i < RACK; i++) addSlot(new SlotItemHandler(rack, i, rackX(i), rackY(i)) {
            @Override public boolean isActive() { return tab == 2; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, TorpedoLineBlockEntity core) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.torpedo_line_shop"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new TorpedoLineMenu(id, inv, core); }
        }, buf -> buf.writeBlockPos(core.getBlockPos()));
    }

    public TorpedoLineBlockEntity.Stage stage() { return TorpedoLineBlockEntity.Stage.values()[Math.max(0, Math.min(data.get(0), TorpedoLineBlockEntity.Stage.values().length - 1))]; }
    public float progress() { return data.get(1) / 1000.0F; }
    public TorpedoKind type() { return TorpedoKind.of(data.get(2)); }
    public int energy() { return (data.get(3) & 0xFFFF) | (data.get(4) << 16); }
    public int lineMask() { return data.get(5); }
    public int blocker() { return data.get(6); }
    public int placeableMask() { return data.get(7); }
    public int removableMask() { return data.get(8); }
    public boolean running() { return stage() != TorpedoLineBlockEntity.Stage.IDLE; }
    public LabBuilder.Result result() { return LabBuilder.Result.of(data.get(9) & 255); }
    public int resultArg() { return data.get(9) >> 8 & 127; }
    public int inRack() { return data.get(10); }
    public boolean auto() { return data.get(11) != 0; }

    public int have(BombLineRecipes.Need n) {
        int sum = 0;
        for (int i = 0; i < 9; i++) { ItemStack s = slots.get(i).getItem(); if (n.test().test(s)) sum += s.getCount(); }
        return sum;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof TorpedoLineBlockEntity core) || core.kind() != TorpedoLineKind.SHOP) return false;
        if (id >= 8) {
            int s = id < 16 ? id - 8 : id - 16;
            if (s < 0 || s >= TorpedoLineBuilder.Socket.values().length) return false;
            if (id >= 16 && !Access.mayMine(core, sp)) { Access.deny(core, sp, CcAlarms.BREAK); return false; }
            TorpedoLineBuilder.Socket sock = TorpedoLineBuilder.Socket.values()[s];
            LabBuilder.Outcome o = id < 16 ? TorpedoLineBuilder.place(this, sp, core, sock) : TorpedoLineBuilder.remove(sp, core, sock);
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
        } else if (!TorpedoLineRecipes.accepts(stack) || !moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof TorpedoLineBlock.Shop
                && level.getBlockEntity(p) instanceof TorpedoLineBlockEntity c && TorpedoLine.near(c, player), true);
    }
}

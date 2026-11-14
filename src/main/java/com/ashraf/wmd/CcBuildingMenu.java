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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CcBuildingMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 10;

    public static final int COLS = 6, SLOT_X0 = 15, SLOT_DX = 26, SLOT_Y0 = 64, SLOT_DY = 26;
    public static final int INV_Y = 162, HOTBAR_Y = 220;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public static int slotX(int i) { return SLOT_X0 + (i % COLS) * SLOT_DX; }
    public static int slotY(int i) { return SLOT_Y0 + (i / COLS) * SLOT_DY; }

    public CcBuildingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(CcBuildingBlockEntity.N) {
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return CcBuildingBlockEntity.accepts(slot, stack); }
        }, new SimpleContainerData(DATA_COUNT), buf.readBlockPos());
    }

    public CcBuildingMenu(int id, Inventory inventory, CcBuildingBlockEntity be) {
        this(id, inventory, be.moduleSlots(), new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> be.bufferStored() & 0xFFFF;
                    case 1 -> be.bufferStored() >>> 16;
                    case 2 -> be.bufferCapacity() & 0xFFFF;
                    case 3 -> be.bufferCapacity() >>> 16;
                    case 4 -> Math.min(32767, be.draw());
                    case 5 -> (be.isPowered() ? 1 : 0) | (be.isDoorOpen() ? 2 : 0) | (be.satsControlled() ? 4 : 0) | (be.isCreative() ? 8 : 0);
                    case 6 -> be.placedMask();
                    case 7 -> be.onlineMask();
                    case 8 -> be.blockedMask();

                    default -> ((be.placedMask() >>> 16) & 0x1F) | (((be.onlineMask() >>> 16) & 0x1F) << 5) | (((be.blockedMask() >>> 16) & 0x1F) << 10);
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, be.getBlockPos());
    }

    private CcBuildingMenu(int id, Inventory inventory, IItemHandler slots, ContainerData data, BlockPos pos) {
        super(Wmd.CC_BUILDING_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < CcBuildingBlockEntity.N; i++) addSlot(new SlotItemHandler(slots, i, slotX(i), slotY(i)) {
            @Override
            public boolean mayPickup(Player player) {
                if (player.level().getBlockEntity(pos) instanceof CcBuildingBlockEntity be && !Access.mayMine(be, player)) return false;
                return super.mayPickup(player);
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, CcBuildingBlockEntity be) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.cc_building"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new CcBuildingMenu(id, inv, be); }
        }, buf -> buf.writeBlockPos(be.getBlockPos()));
    }

    public BlockPos getPos() { return pos; }
    private int pair(int lo) { return (data.get(lo) & 0xFFFF) | ((data.get(lo + 1) & 0xFFFF) << 16); }
    public int energy() { return pair(0); }
    public int energyCap() { return pair(2); }
    public int draw() { return data.get(4); }
    public boolean powered() { return (data.get(5) & 1) != 0; }
    public boolean doorOpen() { return (data.get(5) & 2) != 0; }
    public boolean sats() { return (data.get(5) & 4) != 0; }
    public boolean placed(int k) { return bit(6, 0, k); }
    public boolean online(int k) { return bit(7, 5, k); }
    public boolean blocked(int k) { return bit(8, 10, k); }

    private boolean bit(int lo, int hi, int k) {
        return k < 16 ? (data.get(lo) >> k & 1) != 0 : (data.get(9) >> (hi + k - 16) & 1) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int n = CcBuildingBlockEntity.N;
        if (index < n) {
            if (!moveItemStackTo(stack, n, n + 36, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, n, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockEntity(p) instanceof CcBuildingBlockEntity
                && Reach.within(player, p, 24.0), true);
    }
}

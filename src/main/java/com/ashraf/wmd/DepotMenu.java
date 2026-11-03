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

public class DepotMenu extends AbstractContainerMenu {

    public static final int CELLS = Balance.DEPOT_CELLS, PER_ROW = 4;
    public static final int SLOT_X = 26, SLOT_STEP = 30;
    public static final int[] ROW_Y = { 34, 64 };
    public static final int STATUS_Y = 90, HINT_Y = 102, INV_Y = 126, HOTBAR_Y = 184;
    public static final int PLAYER_END = CELLS + 36;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public DepotMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(CELLS), buf.readBlockPos(), new SimpleContainerData(1));
    }

    public DepotMenu(int id, Inventory inventory, MissileDepotBlockEntity be) {
        this(id, inventory, be.cells(), be.getBlockPos(), new ContainerData() {
            @Override public int get(int i) { return be.energy() / 10; }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 1; }
        });
    }

    private DepotMenu(int id, Inventory inventory, IItemHandler cells, BlockPos pos, ContainerData data) {
        super(Wmd.DEPOT_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < CELLS; i++) addSlot(new SlotItemHandler(cells, i, SLOT_X + (i % PER_ROW) * SLOT_STEP, ROW_Y[i / PER_ROW]));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, MissileDepotBlockEntity be) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.missile_depot"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new DepotMenu(id, inv, be); }
        }, buf -> buf.writeBlockPos(be.getBlockPos()));
    }

    public BlockPos getPos() { return pos; }
    public int energy() { return data.get(0) * 10; }

    public int count() {
        int n = 0;
        for (int i = 0; i < CELLS; i++) if (slots.get(i).hasItem()) n++;
        return n;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CELLS) { if (!moveItemStackTo(stack, CELLS, PLAYER_END, true)) return ItemStack.EMPTY; }
        else if (!MissileDepotBlockEntity.storable(stack) || !moveItemStackTo(stack, 0, CELLS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof MissileDepotBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 400.0, true);
    }
}

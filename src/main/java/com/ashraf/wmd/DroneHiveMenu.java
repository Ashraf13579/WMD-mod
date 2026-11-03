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
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class DroneHiveMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 10, ROW_Y = 18, ROW_H = 28, INV_Y = 140, HOTBAR_Y = 198;
    private static final int BAYS = DroneHiveBlockEntity.BAYS, PLAYER_END = BAYS + 36;

    private final BlockPos pos;
    private final ContainerData data;
    private final boolean remote;

    public DroneHiveMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(BAYS), new SimpleContainerData(DATA_COUNT), buf.readBlockPos(), buf.readBoolean());
    }

    public DroneHiveMenu(int id, Inventory inventory, DroneHiveBlockEntity h, boolean remote) {
        this(id, inventory, h.bays, new ContainerData() {
            @Override public int get(int i) {
                if (i < BAYS) return h.charge(i) * 1000 / Balance.DRONE_CHARGE_FE;
                if (i < 2 * BAYS) {
                    int b = i - BAYS;
                    DroneType t = h.docked(b);
                    return (h.out(b) != 0 ? 1 : 0) | h.mode(b) << 1 | (t == null ? 0 : t.tier) << 3;
                }
                return i == 2 * BAYS ? h.energy() & 0xFFFF : h.energy() >>> 16;
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, h.getBlockPos(), remote);
    }

    private DroneHiveMenu(int id, Inventory inventory, IItemHandler bays, ContainerData data, BlockPos pos, boolean remote) {
        super(Wmd.DRONE_HIVE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.remote = remote;
        for (int i = 0; i < BAYS; i++) addSlot(new SlotItemHandler(bays, i, 8, ROW_Y + i * ROW_H + 4));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, DroneHiveBlockEntity h, boolean remote) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (h.getOwner() != null && !Access.mayUse(h, sp)) { Access.deny(h, sp); return; }
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.drone_hive"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new DroneHiveMenu(id, inv, h, remote); }
        }, buf -> { buf.writeBlockPos(h.getBlockPos()); buf.writeBoolean(remote); });
    }

    public BlockPos getPos() { return pos; }
    public int charge(int bay) { return data.get(bay); }
    public boolean out(int bay) { return (data.get(BAYS + bay) & 1) != 0; }
    public int mode(int bay) { return data.get(BAYS + bay) >> 1 & 3; }
    public int dockedTier(int bay) { return data.get(BAYS + bay) >> 3 & 3; }
    public int energy() { return (data.get(2 * BAYS) & 0xFFFF) | (data.get(2 * BAYS + 1) << 16); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof DroneHiveBlockEntity h)) return false;
        int bay = id / 2;
        if (bay < 0 || bay >= BAYS) return false;
        if (id % 2 == 0) h.launchOrRecall(bay, sp); else h.cycleMode(bay);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < BAYS) {
            if (!moveItemStackTo(stack, BAYS, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, BAYS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(pos) instanceof DroneHiveBlockEntity)) return false;
        double r = remote ? Balance.DRONE_T2_RANGE : 8.0;
        return Reach.within(player, pos, r);
    }
}

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

public class BigDroneMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 5, SLOT_Y = 28, INV_Y = 175, HOTBAR_Y = 233;

    private final BlockPos pos;
    private final ContainerData data;
    private final boolean hangar, remote;
    private final int siteSlots;

    public BigDroneMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), null, new SimpleContainerData(DATA_COUNT));
    }

    private BigDroneMenu(int id, Inventory inventory, BigDroneSite s, boolean remote) {
        this(id, inventory, s.sitePos(), s.isHangar(), remote, s, new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> s.siteStatus();
                    case 1 -> s.outId();
                    case 2 -> s instanceof DroneCatapultBlockEntity c ? c.energy() & 0xFFFF : 0;
                    case 3 -> s instanceof DroneCatapultBlockEntity c ? c.energy() >>> 16 : 0;
                    default -> s instanceof DroneModuleBlockEntity h && h.runway() ? 1 : 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        });
    }

    private BigDroneMenu(int id, Inventory inventory, BlockPos pos, boolean hangar, boolean remote, BigDroneSite s, ContainerData data) {
        super(Wmd.BIG_DRONE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.hangar = hangar;
        this.remote = remote;
        IItemHandler drones = s != null ? s.droneSlots() : new ItemStackHandler(hangar ? 2 : 1);
        IItemHandler bombs = s != null ? s.bombSlots() : new ItemStackHandler(hangar ? DroneModuleBlockEntity.PYLONS : DroneCatapultBlockEntity.BOMBS);
        for (int i = 0; i < drones.getSlots(); i++) addSlot(new SlotItemHandler(drones, i, droneX(i), SLOT_Y));
        for (int i = 0; i < bombs.getSlots(); i++) addSlot(new SlotItemHandler(bombs, i, bombX(i), SLOT_Y));
        siteSlots = drones.getSlots() + bombs.getSlots();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    static int droneX(int i) { return 8 + i * 18; }
    static int bombX(int i) { return 66 + i * 18; }

    public static void open(Player player, BigDroneSite s) { open(player, s, false); }

    public static void open(Player player, BigDroneSite s, boolean remote) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (s.getOwner() != null && !Access.mayUse(s, sp)) { Access.deny(s, sp); return; }
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable(s.isHangar() ? "gui.wmd.big_drone.hangar" : "block.wmd.drone_catapult"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new BigDroneMenu(id, inv, s, remote); }
        }, buf -> { buf.writeBlockPos(s.sitePos()); buf.writeBoolean(s.isHangar()); buf.writeBoolean(remote); });
    }

    public BlockPos getPos() { return pos; }
    public boolean hangar() { return hangar; }
    public int droneSlotCount() { return hangar ? 2 : 1; }
    public int bombSlotCount() { return siteSlots - droneSlotCount(); }
    public int status() { return data.get(0); }
    public int outId() { return data.get(1); }
    public int energy() { return (data.get(2) & 0xFFFF) | (data.get(3) << 16); }
    public boolean runway() { return data.get(4) != 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof BigDroneSite s)) return false;
        if (id < BigDroneSite.A_LAUNCH || id > BigDroneSite.A_DESIGNATED) return false;
        s.act(id, new int[0], sp);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < siteSlots) {
            if (!moveItemStackTo(stack, siteSlots, siteSlots + 36, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, siteSlots, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(pos) instanceof BigDroneSite s)) return false;
        double r = remote ? s.siteRange() : 15.0;
        return player.distanceToSqr(SableBridge.worldPos(player.level(), pos)) <= r * r;
    }
}

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

public class LauncherMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 8;
    public static final int MAG_X = 44, MAG_Y = 64, INV_Y = 158, HOTBAR_Y = 216;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public LauncherMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, slotsOf(buf), new SimpleContainerData(DATA_COUNT), buf.readBlockPos());
    }

    public LauncherMenu(int id, Inventory inventory, InterceptorSite be) {
        this(id, inventory, be.magazineHandler(), new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> be.getFrequency();
                    case 1 -> be.energy() & 0xFFFF;
                    case 2 -> be.energy() >>> 16;
                    case 3 -> be.cooldownTicks();
                    case 4 -> be.trackedCount();
                    case 5 -> be.loaded();
                    case 6 -> be.getOwner() == null ? 0 : 1;
                    default -> be.linkState();
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, be.getBlockPos());
    }

    private final int slotCount;

    private static ItemStackHandler slotsOf(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();

        return new ItemStackHandler(n) {
            @Override public int getSlotLimit(int slot) { return n == 1 ? 64 : 1; }
            @Override public boolean isItemValid(int slot, ItemStack s) { return n == 1 ? s.getItem() instanceof CiwsAmmoItem : s.getItem() instanceof InterceptorItem; }
        };
    }

    private LauncherMenu(int id, Inventory inventory, IItemHandler magazine, ContainerData data, BlockPos pos) {
        super(Wmd.LAUNCHER_MENU.get(), id);
        this.slotCount = magazine.getSlots();
        this.pos = pos;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < slotCount; i++) addSlot(new SlotItemHandler(magazine, i, MAG_X + (i % 4) * 22, MAG_Y + (i / 4) * 22));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, InterceptorSite be, String titleKey) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable(titleKey); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new LauncherMenu(id, inv, be); }
        }, buf -> { buf.writeVarInt(be.slotCount()); buf.writeBlockPos(be.getBlockPos()); });
    }

    public int frequency() { return data.get(0); }
    public int energy() { return (data.get(1) & 0xFFFF) | (data.get(2) << 16); }
    public int cooldown() { return data.get(3); }
    public int tracked() { return data.get(4); }
    public int loaded() { return data.get(5); }
    public boolean claimed() { return data.get(6) != 0; }
    public int link() { return data.get(7); }
    public BlockPos getPos() { return pos; }
    public int slotCount() { return slotCount; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int mag = slotCount;
        if (index < mag) {
            if (!moveItemStackTo(stack, mag, mag + 36, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, mag, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockEntity(p) instanceof InterceptorSite
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
    }
}

package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

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

public class TeleporterMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 11, NO_LINK = 15;
    public static final int SLOT_X = 16, SLOT_Y = 34, INV_Y = 140, HOTBAR_Y = 198;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final List<BlockPos> links;
    private final int[] state;

    public TeleporterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(1), new SimpleContainerData(DATA_COUNT), buf.readBlockPos(), readLinks(buf), new int[2]);
    }

    private static List<BlockPos> readLinks(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<BlockPos> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(buf.readBlockPos());
        return l;
    }

    public TeleporterMenu(int id, Inventory inventory, MissileTeleporterBlockEntity be, int[] state) {
        this(id, inventory, be.chamber(), new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> be.phase();
                    case 1 -> (int) (be.progress(0.0F) * 1000);
                    case 2 -> be.energy() & 0xFFFF;
                    case 3 -> be.energy() >>> 16;
                    case 4 -> be.cooldownSeconds();
                    case 5 -> state[0];
                    case 6, 7, 8, 9 -> i - 6 < be.links().size() ? be.linkState(i - 6) : NO_LINK;
                    default -> state[1];
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA_COUNT; }
        }, be.getBlockPos(), new ArrayList<>(be.links()), state);
    }

    private TeleporterMenu(int id, Inventory inventory, IItemHandler chamber, ContainerData data, BlockPos pos, List<BlockPos> links, int[] state) {
        super(Wmd.TELEPORTER_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.links = links;
        this.state = state;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addSlot(new SlotItemHandler(chamber, 0, SLOT_X, SLOT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return phase() == MissileTeleporterBlockEntity.EMPTY && super.mayPlace(s); }
            @Override public boolean mayPickup(Player p) { return phase() == MissileTeleporterBlockEntity.READY; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, MissileTeleporterBlockEntity be) {
        if (!(player instanceof ServerPlayer sp)) return;
        int[] state = { be.target() >= 0 ? be.target() : 0, -1 };
        List<BlockPos> links = new ArrayList<>(be.links());
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.missile_teleporter"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new TeleporterMenu(id, inv, be, state); }
        }, buf -> { buf.writeBlockPos(be.getBlockPos()); buf.writeVarInt(links.size()); for (BlockPos p : links) buf.writeBlockPos(p); });
    }

    public BlockPos getPos() { return pos; }
    public List<BlockPos> links() { return links; }
    public int phase() { return data.get(0); }
    public float progress() { return data.get(1) / 1000.0F; }
    public int energy() { return (data.get(2) & 0xFFFF) | (data.get(3) << 16); }
    public int cooldown() { return data.get(4); }
    public int selected() { return data.get(5); }
    public int linkState(int i) { return data.get(6 + i); }
    public int lastResult() { return data.get(10); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || !(player.level().getBlockEntity(pos) instanceof MissileTeleporterBlockEntity be)) return false;
        if (!Access.mayUse(be, sp)) { Access.deny(be, sp); return false; }
        if (id >= 0 && id < 4) { state[0] = id; return true; }
        if (id == 4) { state[1] = be.jump(sp, state[0]); return true; }
        if (id >= 8 && id < 12) { be.removeLink(id - 8); if (id - 8 < links.size()) links.remove(id - 8); state[1] = -1; return true; }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) { if (!slot.mayPickup(player) || !moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY; }
        else if (!slots.get(0).mayPlace(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof MissileTeleporterBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 400.0, true);
    }
}

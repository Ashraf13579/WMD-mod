package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

public class GantryMenu extends AbstractContainerMenu {

    public static final int PARTS = GantryCabinetBlockEntity.PARTS, COLS = 9;
    public static final int PARTS_X = 8, PARTS_Y = 18, INV_Y = 140, HOTBAR_Y = 198;
    public static final int PLAYER_END = PARTS + 36;
    static final int D_STATUS = 0, D_FIX = 1, D_MISSING = 2, D_TOTE = 3, D_RUN = 4, D_LEN = 5, D_SPAN = 6, D_FE = 7, D_N = 8;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final GantryCabinetBlockEntity be;

    public GantryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(PARTS), buf.readBlockPos(), new SimpleContainerData(D_N), null);
    }

    public GantryMenu(int id, Inventory inventory, GantryCabinetBlockEntity be) {
        this(id, inventory, be.parts(), be.getBlockPos(), new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case D_STATUS -> be.statusCode();
                    case D_FIX -> be.toFix();
                    case D_MISSING -> be.missing();
                    case D_TOTE -> be.tote();
                    case D_RUN -> be.running() ? 1 : 0;
                    case D_LEN -> be.len();
                    case D_SPAN -> be.span();
                    default -> Math.min(32767, be.energyFe() / 10);
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return D_N; }
        }, be);
        be.viewer(true);
    }

    private GantryMenu(int id, Inventory inventory, IItemHandler parts, BlockPos pos, ContainerData data, GantryCabinetBlockEntity be) {
        super(AirBlocks.GANTRY_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.be = be;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < PARTS; i++) addSlot(new SlotItemHandler(parts, i, PARTS_X + (i % COLS) * 18, PARTS_Y + (i / COLS) * 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public static void open(Player player, GantryCabinetBlockEntity be) {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.wmd.gantry_cabinet"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new GantryMenu(id, inv, be); }
        }, buf -> buf.writeBlockPos(be.getBlockPos()));
    }

    public BlockPos getPos() { return pos; }
    public int get(int i) { return data.get(i); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null || !(player.level() instanceof ServerLevel sl) || !AirGate.on()) return false;
        if (id == 0) { be.toggle(sl); return true; }
        if (id == 1 && player instanceof ServerPlayer sp) { be.openAirframe(sp); return true; }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (be != null) be.viewer(false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PARTS) { if (!moveItemStackTo(stack, PARTS, PLAYER_END, true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 0, PARTS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, p) -> level.getBlockState(p).getBlock() instanceof GantryCabinetBlock
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
    }
}

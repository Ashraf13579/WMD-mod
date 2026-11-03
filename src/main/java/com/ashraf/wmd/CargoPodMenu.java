package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CargoPodMenu extends AbstractContainerMenu {

    public static final int SLOT_X0 = 8, SLOT_Y0 = 22, INV_Y = 106, HOTBAR_Y = 164;
    private static final int N = Balance.CARGO_SLOTS;

    private final boolean block, remote, owner;
    private final BlockPos pos;
    private final InteractionHand hand;
    private final int locked;
    private final CargoPodBlockEntity be;
    private final ItemStack podStack;

    public CargoPodMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new ItemStackHandler(N) { @Override public boolean isItemValid(int s, ItemStack st) { return CargoPodItem.fits(st); } },
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBlockPos(), InteractionHand.values()[buf.readVarInt()], null, null);
    }

    private CargoPodMenu(int id, Inventory inv, IItemHandler h, boolean block, boolean remote, boolean owner, BlockPos pos, InteractionHand hand, CargoPodBlockEntity be, ItemStack podStack) {
        super(Wmd.CARGO_POD_MENU.get(), id);
        this.block = block; this.remote = remote; this.owner = owner; this.pos = pos; this.hand = hand; this.be = be; this.podStack = podStack;
        this.locked = !block && hand == InteractionHand.MAIN_HAND ? inv.selected : -1;
        for (int i = 0; i < N; i++) addSlot(new SlotItemHandler(h, i, SLOT_X0 + (i % 9) * 18, SLOT_Y0 + (i / 9) * 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) {
            final int k = col;
            addSlot(new Slot(inv, col, 8 + col * 18, HOTBAR_Y) {
                @Override public boolean mayPickup(Player p) { return k != locked && super.mayPickup(p); }
                @Override public boolean mayPlace(ItemStack s) { return k != locked && super.mayPlace(s); }
            });
        }
        if (be != null) be.view(true);
    }

    static void openItem(ServerPlayer p, InteractionHand hand) {
        ItemStack pod = p.getItemInHand(hand);
        if (!(pod.getItem() instanceof CargoPodItem)) return;
        ItemStackHandler h = new ItemStackHandler(N) {
            @Override public boolean isItemValid(int s, ItemStack st) { return CargoPodItem.fits(st); }
            @Override protected void onContentsChanged(int s) {

                java.util.List<ItemStack> l = new java.util.ArrayList<>();
                for (int i = 0; i < getSlots(); i++) l.add(getStackInSlot(i).copy());
                pod.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(l));
            }
        };
        var have = Cargo.contents(pod);
        for (int i = 0; i < N; i++) h.setStackInSlot(i, have.get(i).copy());
        p.openMenu(provider(Component.translatable("item.wmd.cargo_pod"), (id, inv) -> new CargoPodMenu(id, inv, h, false, false, false, p.blockPosition(), hand, null, pod)),
                buf -> { buf.writeBoolean(false); buf.writeBoolean(false); buf.writeBoolean(false); buf.writeBlockPos(p.blockPosition()); buf.writeVarInt(hand.ordinal()); });
    }

    static void openBlock(ServerPlayer p, CargoPodBlockEntity be, boolean remote) {
        boolean own = be.owner() == null || be.owner().equals(p.getUUID());
        p.openMenu(provider(Component.translatable("block.wmd.cargo_pod_hull"), (id, inv) -> new CargoPodMenu(id, inv, be.items(), true, remote, own, be.getBlockPos(), InteractionHand.MAIN_HAND, be, null)),
                buf -> { buf.writeBoolean(true); buf.writeBoolean(remote); buf.writeBoolean(own); buf.writeBlockPos(be.getBlockPos()); buf.writeVarInt(0); });
    }

    private interface Make { AbstractContainerMenu make(int id, Inventory inv); }

    private static MenuProvider provider(Component title, Make make) {
        return new MenuProvider() {
            @Override public Component getDisplayName() { return title; }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return make.make(id, inv); }
        };
    }

    public boolean isBlock() { return block; }
    public boolean isOwner() { return owner; }
    public boolean isRemote() { return remote; }
    public BlockPos getPos() { return pos; }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && locked >= 0 && button == locked) return;
        if (type == ClickType.SWAP && button == 40 && !block && hand == InteractionHand.OFF_HAND) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < N) {
            if (!moveItemStackTo(stack, N, N + 36, true)) return ItemStack.EMPTY;
        } else if (!CargoPodItem.fits(stack) || !moveItemStackTo(stack, 0, N, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!block) return podStack == null || player.getItemInHand(hand) == podStack;
        if (player.level().isClientSide) return true;
        if (be == null || be.isRemoved() || player.level().getBlockEntity(pos) != be) return false;
        if (remote) return Orbit.isOrbit(player.level()) && Orbit.slotAt(player.getX(), player.getZ()) == Orbit.slotAt(pos.getX() + 0.5, pos.getZ() + 0.5);
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5) <= 8.0 * 8.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (be != null && !player.level().isClientSide) be.view(false);
    }
}

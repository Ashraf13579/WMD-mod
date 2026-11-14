package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CargoPalletBlockEntity extends BlockEntity implements CargoPalletMenu.Opener {

    private final SimpleContainer store;
    private boolean released;

    public CargoPalletBlockEntity(BlockPos pos, BlockState state) {
        super(CargoBlocks.CARGO_PALLET_BE.get(), pos, state);
        boolean heavy = state.getBlock() instanceof CargoPalletBlock b && b.heavy();
        store = new SimpleContainer(Balance.PALLET_SLOTS[heavy ? 1 : 0]) {
            @Override public boolean canPlaceItem(int slot, ItemStack s) { return CargoPalletItem.fits(s); }
            @Override public void setChanged() { super.setChanged(); CargoPalletBlockEntity.this.setChanged(); }
        };
    }

    public SimpleContainer store() { return store; }
    boolean heavy() { return getBlockState().getBlock() instanceof CargoPalletBlock b && b.heavy(); }
    boolean released() { return released; }

    List<ItemStack> items() {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < store.getContainerSize(); i++) l.add(store.getItem(i));
        return l;
    }

    List<ItemStack> takeAllForRelease() {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < store.getContainerSize(); i++) l.add(store.getItem(i).copy());
        store.clearContent();
        released = true;
        setChanged();
        return l;
    }

    @Override
    public void open(ServerPlayer sp, int compartment) {
        int comps = CargoPalletMenu.compartments(store.getContainerSize());
        int c = Math.max(0, Math.min(comps - 1, compartment));
        boolean hv = heavy();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new CargoPalletMenu(id, inv, store, c, hv, this),
                Component.translatable(hv ? "block.wmd.cargo_pallet_heavy" : "block.wmd.cargo_pallet")),
                buf -> CargoPalletMenu.writeOpen(buf, store.getContainerSize(), c, hv));
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && !released && level != null && level.getBlockEntity(worldPosition) == this && Reach.within(player, worldPosition, 8.0);
    }

    @Override
    public net.minecraft.world.phys.Vec3 where() { return SableBridge.worldPos(level, worldPosition); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> l = NonNullList.withSize(store.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < l.size(); i++) l.set(i, store.getItem(i));
        ContainerHelper.saveAllItems(tag, l, registries);
        if (released) tag.putBoolean("released", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> l = NonNullList.withSize(store.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, l, registries);
        for (int i = 0; i < l.size(); i++) store.setItem(i, l.get(i));
        released = tag.getBoolean("released");
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        NonNullList<ItemStack> l = NonNullList.withSize(store.getContainerSize(), ItemStack.EMPTY);
        input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(l);
        for (int i = 0; i < l.size(); i++) store.setItem(i, l.get(i));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Items");
    }
}

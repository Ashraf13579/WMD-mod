package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class SupplyInterfaceBlockEntity extends BlockEntity implements Owned {

    public static final TagKey<Item> RAW = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "supply_raw"));

    private final SecurityData security = new SecurityData();
    private UUID owner;
    private String ownerName = "";
    private final ItemStackHandler items = new ItemStackHandler(9) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(RAW); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };

    public SupplyInterfaceBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SUPPLY_INTERFACE_BE.get(), pos, state); }

    @Override public SecurityData security() { return security; }
    @Override public UUID getOwner() { return owner; }
    @Override public String getOwnerName() { return ownerName; }

    @Override
    public void claim(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public ItemStackHandler items() { return items; }

    int waiting() {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) n += items.getStackInSlot(i).getCount();
        return n;
    }

    List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < items.getSlots(); i++) { if (!items.getStackInSlot(i).isEmpty()) l.add(items.getStackInSlot(i).copy()); items.setStackInSlot(i, ItemStack.EMPTY); }
        return l;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        security.write(tag);
        tag.put("items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        security.read(tag);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
    }
}

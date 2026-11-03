package com.ashraf.wmd;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CargoPodBlockEntity extends BlockEntity {

    private UUID owner;
    private int port;
    private long arrive;
    private int viewers;
    private final ItemStackHandler items = new ItemStackHandler(Balance.CARGO_SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return CargoPodItem.fits(stack); }
    };

    public CargoPodBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CARGO_POD_BE.get(), pos, state); }

    void setUp(UUID owner, int port, NonNullList<ItemStack> stacks, long now) {
        this.owner = owner; this.port = port; this.arrive = now;
        for (int i = 0; i < Math.min(stacks.size(), items.getSlots()); i++) items.setStackInSlot(i, stacks.get(i).copy());
        sync();
    }

    public UUID owner() { return owner; }
    public int port() { return port; }
    public long arrive() { return arrive; }
    public boolean open() { return viewers > 0; }
    public ItemStackHandler items() { return items; }

    public boolean arriving(long now) { return arrive > 0L && now < arrive + Balance.CARGO_ARRIVE_TICKS; }

    public int stacks() {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) if (!items.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    void view(boolean open) {
        int before = viewers;
        viewers = Math.max(0, viewers + (open ? 1 : -1));
        if ((before > 0) != (viewers > 0)) sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("port", port);
        tag.putLong("arrive", arrive);
        tag.put("items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        port = tag.getInt("port");
        arrive = tag.getLong("arrive");
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        if (tag.contains("open")) viewers = tag.getBoolean("open") ? 1 : 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("port", port);
        t.putLong("arrive", arrive);
        t.putBoolean("open", viewers > 0);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SorterBlockEntity extends BlockEntity implements Owned {

    public static final int FILTER_SLOTS = 9;

    private final SecurityData security = new SecurityData();
    private UUID owner;
    private String ownerName = "";
    private ItemStack buffer = ItemStack.EMPTY;
    private final ItemStack[][] filters = new ItemStack[6][FILTER_SLOTS];
    private final boolean[] allow = { true, true, true, true, true, true };
    private final int[] priority = new int[6];
    private boolean roundRobin = true;
    private int rr;

    public SorterBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SORTER_BE.get(), pos, state);
        for (ItemStack[] f : filters) java.util.Arrays.fill(f, ItemStack.EMPTY);
    }

    @Override public SecurityData security() { return security; }
    @Override public UUID getOwner() { return owner; }
    @Override public String getOwnerName() { return ownerName; }

    @Override
    public void claim(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public ItemStack buffer() { return buffer; }
    public ItemStack filter(Direction f, int i) { return filters[f.ordinal()][i]; }
    public boolean allowList(Direction f) { return allow[f.ordinal()]; }
    public int priority(Direction f) { return priority[f.ordinal()]; }
    public boolean roundRobin() { return roundRobin; }
    public SorterBlock.Face mode(Direction f) { return getBlockState().getValue(SorterBlock.prop(f)); }

    boolean passes(Direction f, ItemStack s) {
        boolean listed = false, any = false;
        for (ItemStack g : filters[f.ordinal()]) {
            if (g.isEmpty()) continue;
            any = true;
            if (g.is(s.getItem())) { listed = true; break; }
        }
        return allow[f.ordinal()] ? (!any || listed) : !listed;
    }

    ItemStack accept(ItemStack s, boolean simulate) {
        if (s.isEmpty()) return s;
        boolean out = false;
        for (Direction f : Direction.values()) if (mode(f) == SorterBlock.Face.OUTPUT && passes(f, s)) { out = true; break; }
        if (!out) return s;
        if (!buffer.isEmpty() && !ItemStack.isSameItemSameComponents(buffer, s)) return s;
        int room = Math.min(Balance.SORTER_BUFFER, s.getMaxStackSize()) - buffer.getCount();
        int n = Math.min(room, s.getCount());
        if (n <= 0) return s;
        if (!simulate) { if (buffer.isEmpty()) buffer = s.copyWithCount(n); else buffer.grow(n); setChanged(); }
        return n == s.getCount() ? ItemStack.EMPTY : s.copyWithCount(s.getCount() - n);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SorterBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || be.buffer.isEmpty() || level.getGameTime() % Balance.DUCT_INTERVAL != 0 || level.hasNeighborSignal(pos)) return;
        be.push(sl);
    }

    private void push(ServerLevel level) {
        List<Direction> faces = new ArrayList<>();
        for (Direction f : Direction.values()) if (mode(f) == SorterBlock.Face.OUTPUT && passes(f, buffer)) faces.add(f);
        if (faces.isEmpty()) return;
        faces.sort((a, b) -> Integer.compare(priority[b.ordinal()], priority[a.ordinal()]));
        if (roundRobin) {
            int top = priority[faces.get(0).ordinal()], same = 0;
            while (same < faces.size() && priority[faces.get(same).ordinal()] == top) same++;
            List<Direction> head = new ArrayList<>(faces.subList(0, same));
            java.util.Collections.rotate(head, -(rr++ % Math.max(1, same)));
            for (int i = 0; i < same; i++) faces.set(i, head.get(i));
        }
        for (Direction f : faces) {
            int before = buffer.getCount();
            BlockPos n = worldPosition.relative(f);
            if (level.getBlockEntity(n) instanceof ItemDuctBlockEntity duct) {
                if (duct.net == null || !duct.net.valid) DuctNet.build(level, n);
                if (duct.net != null) duct.net.injectFromSorter(level, n, f.getOpposite(), buffer);
            } else {
                ItemStack rest = DuctNet.insert(level, new DuctNet.End(worldPosition, f, false), buffer, false, owner);
                buffer = rest;
            }
            if (buffer.getCount() != before) {
                if (buffer.isEmpty()) buffer = ItemStack.EMPTY;
                setChanged();
                return;
            }
        }
    }

    void setMode(Direction f, SorterBlock.Face m) {
        if (level != null) level.setBlock(worldPosition, getBlockState().setValue(SorterBlock.prop(f), m), 3);
        changed();
    }

    void toggleAllow(Direction f) { allow[f.ordinal()] = !allow[f.ordinal()]; changed(); }
    void addPriority(Direction f, int d) { priority[f.ordinal()] = Mth.clamp(priority[f.ordinal()] + d, -9, 9); changed(); }
    void toggleRoundRobin() { roundRobin = !roundRobin; changed(); }
    void setFilter(Direction f, int i, ItemStack s) { if (i >= 0 && i < FILTER_SLOTS) { filters[f.ordinal()][i] = s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1); changed(); } }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        for (int f = 0; f < 6; f++) {
            for (int i = 0; i < FILTER_SLOTS; i++) if (!filters[f][i].isEmpty()) tag.put("f" + f + "_" + i, filters[f][i].save(registries));
            tag.putBoolean("allow" + f, allow[f]);
            tag.putInt("prio" + f, priority[f]);
        }
        tag.putBoolean("rr", roundRobin);
        if (!buffer.isEmpty()) tag.put("buffer", buffer.save(registries));
    }

    private void readSettings(CompoundTag tag, HolderLookup.Provider registries) {
        for (int f = 0; f < 6; f++) {
            for (int i = 0; i < FILTER_SLOTS; i++) filters[f][i] = ItemStack.parseOptional(registries, tag.getCompound("f" + f + "_" + i));
            allow[f] = !tag.contains("allow" + f) || tag.getBoolean("allow" + f);
            priority[f] = tag.getInt("prio" + f);
        }
        roundRobin = !tag.contains("rr") || tag.getBoolean("rr");
        buffer = ItemStack.parseOptional(registries, tag.getCompound("buffer"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        security.write(tag);
        writeSettings(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ownerName")) {
            owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
            ownerName = tag.getString("ownerName");
            security.read(tag);
        }
        readSettings(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeSettings(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

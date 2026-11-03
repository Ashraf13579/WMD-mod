package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ItemDuctBlockEntity extends BlockEntity implements Owned {

    record Transit(ItemStack stack, DuctNet.End from, DuctNet.End to, long arrive) {}

    private final SecurityData security = new SecurityData();
    private UUID owner;
    private String ownerName = "";
    private final List<Transit> transit = new ArrayList<>();

    DuctNet net;

    public ItemDuctBlockEntity(BlockPos pos, BlockState state) { super(Wmd.ITEM_DUCT_BE.get(), pos, state); }

    @Override public SecurityData security() { return security; }
    @Override public UUID getOwner() { return owner; }
    @Override public String getOwnerName() { return ownerName; }

    @Override
    public void claim(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    void addTransit(Transit t) { transit.add(t); setChanged(); }

    List<ItemStack> takeTransit() {
        List<ItemStack> l = new ArrayList<>();
        for (Transit t : transit) l.add(t.stack());
        transit.clear();
        return l;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ItemDuctBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.net == null || !be.net.valid) DuctNet.build(level, pos);
        long now = level.getGameTime();
        if (be.net != null && pos.equals(be.net.anchor) && now % Balance.DUCT_INTERVAL == 0) be.net.tick(sl);
        if (!be.transit.isEmpty()) be.deliver(sl, now);
    }

    private void deliver(ServerLevel level, long now) {
        boolean changed = false;
        for (int i = 0; i < transit.size(); i++) {
            Transit t = transit.get(i);
            if (t.arrive() > now) continue;
            ItemStack rest = DuctNet.insert(level, t.to(), t.stack(), false, owner);
            if (!rest.isEmpty()) rest = DuctNet.insert(level, t.from(), rest, false, owner);
            changed = true;
            if (rest.isEmpty()) { transit.remove(i--); continue; }
            transit.set(i, new Transit(rest, t.from(), t.to(), now + 20));
        }
        if (changed) setChanged();
    }

    Component info() {
        if (net == null || !net.valid) net = level != null ? DuctNet.build(level, worldPosition) : null;
        if (net == null) return Component.empty();
        int moving = 0;
        for (BlockPos m : net.members) if (level.getBlockEntity(m) instanceof ItemDuctBlockEntity d) moving += d.transit.size();
        return Component.translatable("message.wmd.duct.info", net.members.size(), net.sources.size(), net.dests.size(), moving);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) DuctNet.invalidateAround(level, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (net != null) net.valid = false;
    }

    private static CompoundTag end(DuctNet.End e) {
        CompoundTag t = new CompoundTag();
        t.putLong("d", e.duct().asLong());
        t.putInt("s", e.side().ordinal());
        t.putBoolean("q", e.sorter());
        return t;
    }

    private static DuctNet.End end(CompoundTag t) { return new DuctNet.End(BlockPos.of(t.getLong("d")), Direction.from3DDataValue(t.getInt("s")), t.getBoolean("q")); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        security.write(tag);
        ListTag l = new ListTag();
        long now = level != null ? level.getGameTime() : 0;
        for (Transit t : transit) {
            CompoundTag c = new CompoundTag();
            c.put("item", t.stack().save(registries));
            c.put("from", end(t.from()));
            c.put("to", end(t.to()));
            c.putLong("left", Math.max(0, t.arrive() - now));
            l.add(c);
        }
        tag.put("transit", l);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        security.read(tag);
        transit.clear();
        ListTag l = tag.getList("transit", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            ItemStack s = ItemStack.parseOptional(registries, c.getCompound("item"));
            if (!s.isEmpty()) transit.add(new Transit(s, end(c.getCompound("from")), end(c.getCompound("to")), c.getLong("left")));
        }
    }
}

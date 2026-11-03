package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

final class DuctNet {

    record End(BlockPos duct, Direction side, boolean sorter) {
        BlockPos target() { return duct.relative(side); }
    }

    final UUID owner;
    final Set<BlockPos> members = new HashSet<>();
    final List<End> sources = new ArrayList<>(), dests = new ArrayList<>();
    BlockPos anchor;
    boolean valid = true;
    private int destRr;
    private final Map<BlockPos, Integer> slotRr = new HashMap<>();

    private DuctNet(UUID owner) { this.owner = owner; }

    static DuctNet build(Level level, BlockPos start) {
        DuctNet net = new DuctNet(level.getBlockEntity(start) instanceof ItemDuctBlockEntity d ? d.getOwner() : null);
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        net.members.add(start);
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof ItemDuctBlock)) continue;
            for (Direction d : Direction.values()) {
                ItemDuctBlock.Conn c = s.getValue(ItemDuctBlock.prop(d));
                BlockPos n = p.relative(d);
                if (c == ItemDuctBlock.Conn.EXTRACT) net.sources.add(new End(p, d, false));
                else if (c == ItemDuctBlock.Conn.INSERT) net.dests.add(new End(p, d, false));
                else if (c == ItemDuctBlock.Conn.DUCT && level.isLoaded(n)) {
                    BlockState ns = level.getBlockState(n);
                    if (ns.getBlock() instanceof SorterBlock) {
                        if (ns.getValue(SorterBlock.prop(d.getOpposite())) == SorterBlock.Face.INPUT) net.dests.add(new End(p, d, true));
                    } else if (ns.getBlock() instanceof ItemDuctBlock && net.members.size() < Balance.DUCT_MAX && net.members.add(n)) {
                        todo.add(n);
                    }
                }
            }
        }
        for (BlockPos m : net.members) {
            if (net.anchor == null || m.asLong() < net.anchor.asLong()) net.anchor = m;
            if (level.getBlockEntity(m) instanceof ItemDuctBlockEntity be) be.net = net;
        }
        return net;
    }

    static void invalidateAround(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof ItemDuctBlockEntity be && be.net != null) be.net.valid = false;
        for (Direction d : Direction.values())
            if (level.isLoaded(pos.relative(d)) && level.getBlockEntity(pos.relative(d)) instanceof ItemDuctBlockEntity be && be.net != null) be.net.valid = false;
    }

    void tick(ServerLevel level) {
        for (End src : sources) {
            if (level.hasNeighborSignal(src.duct())) continue;
            IItemHandler h = handler(level, src);
            if (h == null) continue;
            int slots = h.getSlots(), start = slotRr.getOrDefault(src.target(), 0);
            for (int k = 0; k < slots; k++) {
                int i = (start + k) % slots;
                final IItemHandler fh = h;
                ItemStack can = ItemPorts.asWmd(() -> fh.extractItem(i, Balance.DUCT_BATCH, true));
                if (can.isEmpty()) continue;
                Object[] pick = route(level, can, src.target());
                if (pick == null) continue;
                int n = (int) pick[1];
                ItemStack moved = ItemPorts.asWmd(() -> fh.extractItem(i, n, false));
                if (moved.isEmpty()) continue;
                launch(level, src, (End) pick[0], moved);
                slotRr.put(src.target(), i + 1);
                break;
            }
        }
    }

    boolean injectFromSorter(ServerLevel level, BlockPos duct, Direction towardsSorter, ItemStack stack) {
        Object[] pick = route(level, stack, duct.relative(towardsSorter));
        if (pick == null) return false;
        launch(level, new End(duct, towardsSorter, true), (End) pick[0], stack.copyWithCount((int) pick[1]));
        stack.shrink((int) pick[1]);
        return true;
    }

    private Object[] route(ServerLevel level, ItemStack stack, BlockPos notInto) {
        int n = dests.size();
        for (int k = 0; k < n; k++) {
            End e = dests.get((destRr + k) % n);
            if (e.target().equals(notInto)) continue;
            ItemStack rest = insert(level, e, stack, true);
            int took = stack.getCount() - rest.getCount();
            if (took > 0) { destRr = (destRr + k + 1) % n; return new Object[] { e, took }; }
        }
        return null;
    }

    private void launch(ServerLevel level, End from, End to, ItemStack stack) {
        List<BlockPos> path = path(level, from.duct(), to.duct());
        int ticks = (path.size() + 1) * Balance.DUCT_TICKS_PER_BLOCK;
        if (level.getBlockEntity(from.duct()) instanceof ItemDuctBlockEntity be) be.addTransit(new ItemDuctBlockEntity.Transit(stack, from, to, level.getGameTime() + ticks));
        long[] pts = new long[path.size()];
        for (int i = 0; i < pts.length; i++) pts[i] = path.get(i).asLong();
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(from.duct()),
                new DuctTransitPayload(pts, from.side().ordinal(), to.side().ordinal(), stack.copyWithCount(1), ticks));
    }

    private List<BlockPos> path(Level level, BlockPos a, BlockPos b) {
        Map<BlockPos, BlockPos> prev = new HashMap<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(a);
        prev.put(a, a);
        while (!todo.isEmpty() && !prev.containsKey(b)) {
            BlockPos p = todo.poll();
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof ItemDuctBlock)) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (s.getValue(ItemDuctBlock.prop(d)) == ItemDuctBlock.Conn.DUCT && members.contains(n) && !prev.containsKey(n)) { prev.put(n, p); todo.add(n); }
            }
        }
        List<BlockPos> out = new ArrayList<>();
        if (!prev.containsKey(b)) { out.add(a); if (!a.equals(b)) out.add(b); return out; }
        for (BlockPos p = b; ; p = prev.get(p)) { out.add(0, p); if (p.equals(a)) break; }
        return out;
    }

    IItemHandler handler(Level level, End e) { return handler(level, e, owner); }

    static IItemHandler handler(Level level, End e, UUID owner) {
        if (e.sorter() || !level.isLoaded(e.target())) return null;
        Owned o = Access.ownerAt(level, e.target());
        if (o != null && !Access.trusts(o, owner)) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, e.target(), e.side().getOpposite());
    }

    static ItemStack insert(Level level, End e, ItemStack stack, boolean simulate, UUID owner) {
        if (e.sorter()) {
            if (!(level.getBlockEntity(e.target()) instanceof SorterBlockEntity s) || (owner != null && s.getOwner() != null && !owner.equals(s.getOwner()))) return stack;
            return s.accept(stack, simulate);
        }
        IItemHandler h = handler(level, e, owner);
        if (h == null) return stack;
        return ItemPorts.asWmd(() -> ItemHandlerHelper.insertItem(h, stack.copy(), simulate));
    }

    private ItemStack insert(Level level, End e, ItemStack stack, boolean simulate) { return insert(level, e, stack, simulate, owner); }
}

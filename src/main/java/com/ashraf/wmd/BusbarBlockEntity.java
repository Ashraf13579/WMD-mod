package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class BusbarBlockEntity extends BlockEntity {

    public static final int CAPACITY = 400_000;
    public static final int RATE = Balance.BUS_NET_MAX_FLOW;
    static final int MAX_NET = 8_192;

    private int stored;
    Net net;

    static final class Net {
        final List<BusbarBlockEntity> members = new ArrayList<>();
        final List<BlockPos> epPos = new ArrayList<>();
        final List<Direction> epDir = new ArrayList<>();
        long pool, cap;
        long lastTick = Long.MIN_VALUE, endpointsAt = -1_000L;
        boolean dead;
        int activity, rr;

        void kill() {
            if (dead) return;
            dead = true;
            long share = members.isEmpty() ? 0 : pool / members.size();
            for (BusbarBlockEntity b : members) if (!b.isRemoved()) b.stored = (int) Math.min(CAPACITY, share);
        }

        void refreshEndpoints(Level level) {
            epPos.clear(); epDir.clear();
            for (BusbarBlockEntity b : members) {
                if (b.isRemoved()) continue;
                BlockState st = level.getBlockState(b.worldPosition);
                if (!(st.getBlock() instanceof BusbarBlock)) continue;
                for (Direction d : Direction.values()) {
                    if (!st.getValue(BusbarBlock.prop(d))) continue;
                    BlockPos np = b.worldPosition.relative(d);
                    if (level.getBlockState(np).getBlock() instanceof BusbarBlock) continue;
                    epPos.add(np); epDir.add(d);
                }
            }
        }

        void exchange(Level level, long now) {
            if (now - endpointsAt >= 10) { refreshEndpoints(level); endpointsAt = now; }
            int k = epPos.size();
            long moved = 0;
            boolean[] pulled = new boolean[k];
            Grid.pushing = true;
            try {

                IEnergyStorage[] st = new IEnergyStorage[k];
                int[] kind = new int[k];
                for (int i = 0; i < k; i++) {
                    IEnergyStorage s = level.getCapability(Capabilities.EnergyStorage.BLOCK, epPos.get(i), epDir.get(i).getOpposite());
                    st[i] = s;
                    if (s == null) continue;
                    boolean wmd = Grid.isWmd(level, epPos.get(i));
                    if (wmd && s.canExtract()) kind[i] = s.canReceive() ? 2 : 1;
                    else if (s.canReceive()) kind[i] = 3;
                }

                long inBudget = Balance.BUS_NET_MAX_FLOW;
                for (int i = 0; i < k && pool < cap && inBudget > 0; i++) {
                    if (kind[i] != 1) continue;
                    int got = st[i].extractEnergy((int) Math.min(Integer.MAX_VALUE, Math.min(inBudget, cap - pool)), false);
                    if (got > 0) { pool += got; inBudget -= got; moved += got; pulled[i] = true; }
                }

                long demand = 0;
                for (int i = 0; i < k && demand < Balance.BUS_NET_MAX_FLOW; i++)
                    if (kind[i] == 3) demand += st[i].receiveEnergy((int) Math.min(Integer.MAX_VALUE, Balance.BUS_NET_MAX_FLOW - demand), true);
                for (int i = 0; i < k && pool < demand && inBudget > 0; i++) {
                    if (kind[i] != 2) continue;
                    int got = st[i].extractEnergy((int) Math.min(Integer.MAX_VALUE, Math.min(inBudget, demand - pool)), false);
                    if (got > 0) { pool += got; inBudget -= got; moved += got; pulled[i] = true; }
                }

                long budget = Math.min(pool, Balance.BUS_NET_MAX_FLOW);
                for (int pass = 0; pass < 2 && budget > 0 && k > 0; pass++) {
                    int start = Math.floorMod(rr, k);
                    for (int j = 0; j < k && budget > 0; j++) {
                        int i = (start + j) % k;
                        if (pulled[i] || kind[i] != (pass == 0 ? 3 : 2)) continue;
                        int sent = st[i].receiveEnergy((int) Math.min(Integer.MAX_VALUE, budget), false);
                        if (sent > 0) { budget -= sent; pool -= sent; moved += sent; }
                    }
                }
                rr++;
            } finally {
                Grid.pushing = false;
            }
            activity = moved > 0 ? 12 : Math.max(0, activity - 1);
        }
    }

    private final IEnergyStorage buffer = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            Net n = net;
            if (n == null || n.dead) {
                int x = Mth.clamp(Math.min(toReceive, CAPACITY - stored), 0, RATE);
                if (!simulate) stored += x;
                return x;
            }
            int x = (int) Mth.clamp(Math.min(toReceive, n.cap - n.pool), 0, RATE);
            if (!simulate) n.pool += x;
            return x;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) { return 0; }

        @Override
        public int getEnergyStored() { return share(); }

        @Override
        public int getMaxEnergyStored() { return CAPACITY; }

        @Override
        public boolean canExtract() { return false; }

        @Override
        public boolean canReceive() { return true; }
    };

    public BusbarBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.BUSBAR_BE.get(), pos, state);
    }

    public IEnergyStorage getEnergy() { return buffer; }

    private int share() {
        Net n = net;
        return n == null || n.dead || n.members.isEmpty() ? stored : (int) Math.min(CAPACITY, n.pool / n.members.size());
    }

    private static Net rebuild(Level level, BusbarBlockEntity start) {
        Net n = new Net();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start.worldPosition);
        q.add(start.worldPosition);
        while (!q.isEmpty() && n.members.size() < MAX_NET) {
            BlockPos p = q.poll();
            if (!(level.getBlockEntity(p) instanceof BusbarBlockEntity b) || b.isRemoved()) continue;
            if (b.net != null && b.net != n) b.net.kill();
            b.net = n;
            n.members.add(b);
            n.pool += b.stored;
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof BusbarBlock)) continue;
            for (Direction d : Direction.values()) {
                if (!st.getValue(BusbarBlock.prop(d))) continue;
                BlockPos np = p.relative(d);
                if (seen.contains(np) || !level.isLoaded(np) || !(level.getBlockState(np).getBlock() instanceof BusbarBlock)) continue;
                seen.add(np);
                q.add(np);
            }
        }
        n.cap = (long) CAPACITY * Math.max(1, n.members.size());
        n.pool = Math.min(n.pool, n.cap);
        return n;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BusbarBlockEntity be) {
        if (be.net == null || be.net.dead) rebuild(level, be);
        Net n = be.net;
        long now = level.getGameTime();
        if (n.lastTick != now) { n.lastTick = now; n.exchange(level, now); }
        boolean lit = n.activity > 0;
        if (state.getValue(BusbarBlock.LIT) != lit) level.setBlock(pos, state.setValue(BusbarBlock.LIT, lit), 2);
        if (now % 20 == 0) be.setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;
        for (Direction d : Direction.values()) {
            BlockPos np = worldPosition.relative(d);
            if (level.isLoaded(np) && level.getBlockEntity(np) instanceof BusbarBlockEntity o && o.net != null) o.net.kill();
        }
    }

    @Override
    public void setRemoved() {
        if (net != null && !net.dead) stored = share();
        super.setRemoved();
        if (net != null) net.kill();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", share());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Mth.clamp(tag.getInt("stored"), 0, CAPACITY);
    }
}

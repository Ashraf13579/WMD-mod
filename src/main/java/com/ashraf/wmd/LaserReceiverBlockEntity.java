package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class LaserReceiverBlockEntity extends BlockEntity implements Owned, MachinePorts {

    private static class Buffer extends EnergyStorage {
        Buffer(int cap, int receive) { super(cap, receive, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private static final Map<UUID, Map<Integer, long[]>> SEEN = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final Buffer store = new Buffer(Balance.LWR_BUFFER, Balance.LWR_RECEIVE);
    private final Set<Integer> known = new HashSet<>();
    private int ignored;

    public LaserReceiverBlockEntity(BlockPos pos, BlockState state) { super(Wmd.LASER_RECEIVER_BE.get(), pos, state); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    static synchronized List<int[]> enemySpots(UUID owner, long now) {
        List<int[]> l = new ArrayList<>();
        Map<Integer, long[]> m = SEEN.get(owner);
        if (m == null) return l;
        m.values().removeIf(v -> now - v[2] > 200);
        for (long[] v : m.values()) l.add(new int[] { (int) v[0], (int) v[1] });
        return l;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        Direction f = getBlockState().getValue(MachineBlock.FACING), m = Machines.mount(getBlockState());
        BlockPos l = Machines.toLocal(worldPosition, f, m, part);
        return l.equals(BlockPos.ZERO) && side == Machines.dirToWorld(f, m, Direction.SOUTH) ? store : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserReceiverBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || (sl.getGameTime() + pos.asLong()) % 20 != 0) return;
        UUID owner = be.getOwner();
        boolean powered = owner != null && be.store.getEnergyStored() >= Balance.LWR_FE_PER_TICK * 20 && !EmpField.jammed(sl, pos);
        boolean alert = false;
        be.ignored = 0;
        if (powered) {
            Vec3 at = SableBridge.worldPos(sl, pos);
            be.store.use(Balance.LWR_FE_PER_TICK * 20);
            Set<Integer> now = new HashSet<>();
            for (LaserSpots.Spot s : LaserSpots.all()) {
                if (!s.dim().equals(sl.dimension()) || Math.hypot(s.pos().x - at.x, s.pos().z - at.z) > Balance.LWR_RADIUS) continue;
                if (LaserSpots.get(sl, s.id()) == null) continue;
                if (s.owner().equals(owner)) { be.ignored++; continue; }
                alert = true;
                now.add(s.id());
                synchronized (LaserReceiverBlockEntity.class) {
                    SEEN.computeIfAbsent(owner, k -> new HashMap<>()).put(s.id(), new long[] { (long) Math.floor(s.pos().x), (long) Math.floor(s.pos().z), sl.getGameTime() });
                }
                if (!be.known.contains(s.id())) {
                    String from = "?";
                    if (s.origin() != null) {
                        Vec3 d = s.origin().subtract(s.pos());
                        String[] c = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
                        from = c[Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-d.x, d.z)) / 45.0F), 8)];
                    }
                    CcDefcon.banner(sl.getServer(), owner, CcDefcon.B_INCOMING, true, "message.wmd.lwr.alert",
                            Integer.toString((int) Math.floor(s.pos().x)), Integer.toString((int) Math.floor(s.pos().z)), from);
                    sl.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8F, 1.8F);
                }
            }
            be.known.retainAll(now);
            be.known.addAll(now);
        } else be.known.clear();
        if (state.getValue(LaserReceiverBlock.ALERT) != alert) {
            sl.setBlock(pos, state.setValue(LaserReceiverBlock.ALERT, alert), 3);
            sl.updateNeighborsAt(pos, state.getBlock());
        }
        if (sl.getGameTime() % 100 == 0) be.setChanged();
    }

    public Component info() {
        return Component.translatable("message.wmd.lwr.info", String.format("%,d", store.getEnergyStored()), String.format("%,d", store.getMaxEnergyStored()), known.size(), Balance.LWR_RADIUS, ignored);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("energy", store.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        store.set(tag.getInt("energy"));
    }
}

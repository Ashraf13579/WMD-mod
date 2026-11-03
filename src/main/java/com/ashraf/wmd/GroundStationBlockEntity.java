package com.ashraf.wmd;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class GroundStationBlockEntity extends ProcessUnitBlockEntity implements Owned {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 6, Z0 = 0, Z1 = 4;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 4, Direction.SOUTH, false);

    private static final float EASE = 0.12F, MAX_STEP = 3.0F;

    private final OwnerData ownerData = new OwnerData();

    private static final class JamBuffer extends EnergyStorage {
        JamBuffer(int capacity) { super(capacity, Math.max(1, capacity / 20), 0); }
        boolean take(int n) { if (energy < n) return false; energy -= n; return true; }
        void set(int e) { energy = Math.max(0, Math.min(capacity, e)); }
    }
    private final JamBuffer jamBuf = new JamBuffer(Balance.GROUND_STATION_JAM_FE * 200);
    private boolean jamming, jamWanted;

    private final JamBuffer hackBuf = new JamBuffer(Balance.SAT_HACK_FE_PER_TICK * 200);
    private int hackSat, hackTicks;
    private int[] hackTried = new int[0];

    private final IEnergyStorage port = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) {
            int a = energy().receiveEnergy(n, sim);
            int b = n > a ? jamBuf.receiveEnergy(n - a, sim) : 0;
            int c = n > a + b ? hackBuf.receiveEnergy(n - a - b, sim) : 0;
            if (b + c > 0 && !sim) setChanged();
            return a + b + c;
        }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return energy().getEnergyStored() + jamBuf.getEnergyStored() + hackBuf.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return energy().getMaxEnergyStored() + jamBuf.getMaxEnergyStored() + hackBuf.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    private float az, el = 90.0F, prevAz, prevEl = 90.0F;
    private boolean tracking;

    public GroundStationBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.GROUND_STATION_BE.get(), pos, state, Balance.GROUND_STATION_FE_PER_TICK);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static final Set<GroundStationBlockEntity> STATIONS = Collections.newSetFromMap(new WeakHashMap<>());
    static void clearStatics() { synchronized (STATIONS) { STATIONS.clear(); } }
    private boolean registered;

    public static boolean hasPoweredStation(UUID owner) {
        if (owner == null) return false;
        boolean loaded = false;
        synchronized (STATIONS) {
            for (GroundStationBlockEntity s : STATIONS) {
                if (s.isRemoved() || s.level == null || !owner.equals(s.getOwner())) continue;
                loaded = true;
                if (s.isRunning()) { LAST_RUNNING.put(owner, true); return true; }
            }
        }

        if (loaded) { LAST_RUNNING.put(owner, false); return false; }
        return LAST_RUNNING.getOrDefault(owner, false);
    }

    private static final java.util.Map<UUID, Boolean> LAST_RUNNING = Statics.server(new java.util.concurrent.ConcurrentHashMap<>());

    static void forget(UUID owner) { if (owner != null) LAST_RUNNING.remove(owner); }

    public static java.util.List<BlockPos> stationsOf(Level level, UUID owner) {
        java.util.List<BlockPos> l = new java.util.ArrayList<>();
        if (owner == null) return l;
        synchronized (STATIONS) {
            for (GroundStationBlockEntity s : STATIONS)
                if (!s.isRemoved() && s.level == level && owner.equals(s.getOwner())) l.add(s.getBlockPos());
        }
        return l;
    }

    public static void serverTickStation(Level level, BlockPos pos, BlockState state, GroundStationBlockEntity be) {
        if (!be.registered) {
            synchronized (STATIONS) { STATIONS.add(be); }
            be.registered = true;
        }
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
        be.jamTick(level, state);
    }

    private void jamTick(Level level, BlockState state) {
        if ((level.getGameTime() + (worldPosition.asLong() & 15L)) % 20 == 0) jamWanted = CcJammer.rackOn(getOwner());
        boolean j = jamWanted && isRunning() && jamBuf.take(Balance.GROUND_STATION_JAM_FE);
        if (j != jamming) {
            jamming = j;
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    boolean takeHack(int fe) {
        if (!hackBuf.take(fe)) return false;
        setChanged();
        return true;
    }

    void setHackView(int sat, int ticks, int[] tried) {
        if (sat == hackSat && ticks == hackTicks && java.util.Arrays.equals(tried, hackTried)) return;
        hackSat = sat;
        hackTicks = ticks;
        hackTried = tried == null ? new int[0] : tried;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int hackSat() { return hackSat; }
    public int hackTicks() { return hackTicks; }
    public boolean triedSat(int id) { for (int t : hackTried) if (t == id) return true; return false; }

    public boolean isJamming() { return jamming; }

    public static boolean anyJamming(UUID owner) {
        if (owner == null) return false;
        synchronized (STATIONS) {
            for (GroundStationBlockEntity s : STATIONS) if (!s.isRemoved() && owner.equals(s.getOwner()) && s.jamming) return true;
        }
        return false;
    }

    public static GroundStationBlockEntity jammerNear(Level level, UUID attacker, double x, double z, double r) {
        if (level == null) return null;
        GroundStationBlockEntity best = null;
        double bd = r * r;
        synchronized (STATIONS) {
            for (GroundStationBlockEntity s : STATIONS) {
                if (s.isRemoved() || s.level != level || !s.jamming || s.getOwner() == null) continue;
                if (attacker != null && (attacker.equals(s.getOwner()) || s.security().trusted().containsKey(attacker))) continue;
                net.minecraft.world.phys.Vec3 sw = SableBridge.worldPos(level, s.worldPosition);
                double dx = sw.x - x, dz = sw.z - z, d = dx * dx + dz * dz;
                if (d <= bd) { bd = d; best = s; }
            }
        }
        return best;
    }

    private boolean unloading;

    @Override
    public void onChunkUnloaded() { unloading = true; super.onChunkUnloaded(); }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (STATIONS) { STATIONS.remove(this); }
        if (!unloading && level != null && !level.isClientSide) forget(getOwner());
        registered = false;
    }

    @Override
    protected boolean work() {
        reason = 0;
        return true;
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? port : null; }

    public static void clientTickStation(Level level, BlockPos pos, BlockState state, GroundStationBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.tickDish();
    }

    private void tickDish() {
        prevAz = az;
        prevEl = el;
        float tAz = az, tEl = 90.0F;
        net.minecraft.world.phys.Vec3 me = SableBridge.worldPos(level, worldPosition);
        OrbitMath.Sat s = isRunning() ? OrbitView.trackedBy(getOwner(), me.x, me.z) : null;
        tracking = s != null;
        if (s != null) {
            double[] sky = OrbitMath.sky(s, me.x, me.z, OrbitView.now());
            tAz = (float) sky[0];
            tEl = (float) sky[1];
        }
        az += Mth.clamp(Mth.wrapDegrees(tAz - az) * EASE, -MAX_STEP, MAX_STEP);
        el += Mth.clamp((tEl - el) * EASE, -MAX_STEP, MAX_STEP);
        if (az >= 360.0F) { az -= 360.0F; prevAz -= 360.0F; }
        if (az < 0.0F) { az += 360.0F; prevAz += 360.0F; }
    }

    public float dishAz(float pt) { return prevAz + Mth.wrapDegrees(az - prevAz) * pt; }

    public float dishEl(float pt) { return prevEl + (el - prevEl) * pt; }

    public boolean isTracking() { return tracking; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override public int guiFeUse() { return Balance.GROUND_STATION_FE_PER_TICK + (jamming ? Balance.GROUND_STATION_JAM_FE : 0) + (hackSat > 0 ? Balance.SAT_HACK_FE_PER_TICK : 0); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.GROUND_STATION_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.ground_station"));
    }

    @Override
    protected String detail() {
        int n = level != null && level.isClientSide ? OrbitView.count() : level != null && level.getServer() != null ? OrbitData.get(level.getServer()).count() : 0;
        return "satellites in orbit " + n + " / " + Balance.SAT_MAX + (isRunning() ? ", in control" : ", no control") + (jamming ? ", JAMMING uplinks" : "")
                + (hackSat > 0 ? ", HACKING satellite #" + hackSat : "");
    }

    @Override protected SoundEvent runSound() { return WmdSounds.GS_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }
    @Override protected float soundVolume() { return 0.25F; }

    @Override
    protected void clientEffects(Level level) {
        double[] w = worldPoint(0.0, 1.2, 0.2);
        VeilBridge.machineGlow(getBlockPos().asLong() ^ 0x4753_0000_0000_0000L, w[0], w[1], w[2], 0.35F, 0.6F, 1.0F, 3.0F, 0.5F, level.getGameTime());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("jamFE", jamBuf.getEnergyStored());
        tag.putInt("hackFE", hackBuf.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        jamBuf.set(tag.getInt("jamFE"));
        jamming = tag.getBoolean("jamming");
        if (tag.contains("hackFE")) hackBuf.set(tag.getInt("hackFE"));
        hackSat = tag.getInt("hackSat");
        hackTicks = tag.getInt("hackTicks");
        hackTried = tag.getIntArray("hackTried");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putBoolean("jamming", jamming);
        tag.putInt("hackSat", hackSat);
        tag.putInt("hackTicks", hackTicks);
        tag.putIntArray("hackTried", hackTried);
        return tag;
    }
}

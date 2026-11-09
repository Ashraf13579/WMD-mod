package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RadarBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, EmpVulnerable {
    private final SecurityData security = new SecurityData();
    @Override public SecurityData security() { return security; }

    public static final int CAPACITY = 1_000_000;
    public static final int FE_PER_TICK = 200;
    public static final int MAX_RECEIVE = 10_000;
    public static final float DEGREES_PER_TICK = 0.45F;
    public static final float HALF_ARC = 90.0F;
    public static final int DETECT_RANGE = (int) Balance.RADAR_DETECT_RANGE;
    public static final int DATA_COUNT = 10;
    public static final int MODE_TARGET = 0, MODE_SEEK = 1;
    public static final float SWEEP_PER_TICK = 0.9F;
    public static final float BEAM_HALF = 15.0F;
    public static final int SCAN_INTERVAL = 4;
    public static final int LOCK_LOST_TICKS = 40;

    private float bearing, targetBearing;
    private boolean powered;
    private int mode = MODE_TARGET;
    private int frequency = 1;
    private UUID owner;
    private String ownerName = "";

    private final Set<Long> locks = new HashSet<>();
    static final int MAX_LOCKS = 4;
    private long lockedFlight;
    private int lockLost;

    private static final int BEARING_SYNC = 10;
    private long lastSyncT = -100L;
    private boolean bearingDirty;
    private long syncT = -1L;
    private float baseBearing;

    public record ShipContact(UUID id, double x, double y, double z, float hx, float hz, int blocks, float size, int rel, String name, boolean locked, boolean designated) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putDouble("x", x); t.putDouble("y", y); t.putDouble("z", z);
            t.putFloat("hx", hx); t.putFloat("hz", hz);
            t.putInt("b", blocks); t.putFloat("s", size); t.putByte("r", (byte) rel);
            t.putString("n", name); t.putBoolean("l", locked); t.putBoolean("d", designated);
            return t;
        }
        static ShipContact load(CompoundTag t) {
            String n = t.getString("n");
            return new ShipContact(t.hasUUID("id") ? t.getUUID("id") : new UUID(0L, 0L), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("hx"),
                    t.getFloat("hz"), t.getInt("b"), t.getFloat("s"), t.getByte("r"), n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n, t.getBoolean("l"), t.getBoolean("d"));
        }
        public boolean hostile() { return rel == 0; }
        public boolean friendly() { return rel == 2; }
    }

    public static final int SHIP_SCAN_INTERVAL = 20;
    static final int NAME_MAX = 32;
    private List<ShipContact> ships = List.of();

    private final Set<UUID> shipLocks = new HashSet<>();

    public List<ShipContact> shipContacts() { return ships; }

    private float shown, prevShown;
    private boolean shownReady;

    private static class Store extends EnergyStorage {
        Store() { super(CAPACITY, MAX_RECEIVE, 0); }
        int drain(int n) { int d = Math.min(n, energy); energy -= d; return d; }
        void set(int v) { energy = Mth.clamp(v, 0, capacity); }
    }

    private final Store store = new Store();

    @Override
    public void empDrain() { store.set(0); setChanged(); }
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.RADAR_BE.get(), pos, state);
    }

    public static float norm(float deg) { return ((deg % 360.0F) + 360.0F) % 360.0F; }

    public static float delta(float deg) {
        float d = norm(deg);
        return d > 180.0F ? d - 360.0F : d;
    }

    public float bearingTo(int x, int z) {
        Vec3 c = SableBridge.worldPos(level, worldPosition);
        double dx = x + 0.5 - c.x;
        double dz = z + 0.5 - c.z;
        return norm((float) Math.toDegrees(Math.atan2(dx, -dz)));
    }

    public boolean isInArc(int x, int z) {
        return Math.abs(delta(bearingTo(x, z) - bearing)) <= HALF_ARC + 0.01F;
    }

    public boolean isFormed() { return getBlockState().getValue(RadarStationBlock.FORMED); }
    public boolean isOnline() { return isFormed() && powered && !(level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, worldPosition)); }

    public boolean isTargeting() { return isOnline() && mode == MODE_TARGET; }
    public float getBearing() { return bearing; }
    public float getTargetBearing() { return targetBearing; }
    public IEnergyStorage getEnergy() { return store; }

    public int getMode() { return mode; }
    public int getFrequency() { return frequency; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public boolean isLocked() { return lockedFlight != 0; }

    public void claim(Player player) {
        owner = player.getUUID();
        ownerName = player.getName().getString();
        setChanged();
    }

    public void setMode(int m) {
        int nm = m == MODE_SEEK ? MODE_SEEK : MODE_TARGET;
        if (nm == mode) return;
        mode = nm;
        lockedFlight = 0;
        locks.clear();
        ships = List.of();
        shipLocks.clear();
        lockLost = 0;
        targetBearing = bearing;
        sync();
    }

    public void setFrequency(int f) {
        int nf = Mth.clamp(f, 1, 9999);
        if (nf == frequency) return;
        frequency = nf;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); lastSyncT = level.getGameTime(); bearingDirty = false; }
    }

    public void setTargetBearing(float deg) {
        targetBearing = norm(deg);
        setChanged();
    }

    public void aimAt(int x, int z) {
        setTargetBearing(bearingTo(x, z));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadarBlockEntity be) {
        RadarEmit.note(be);
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) {
            if (be.powered || !be.ships.isEmpty()) { be.powered = false; be.ships = List.of(); be.shipLocks.clear(); be.sync(); }
            return;
        }
        if (!state.getValue(RadarStationBlock.FORMED)) return;
        boolean wasPowered = be.powered;
        be.powered = be.store.getEnergyStored() >= FE_PER_TICK;
        if (be.powered) be.store.drain(FE_PER_TICK);
        if (be.powered && be.owner != null && level instanceof ServerLevel hs) TrackNet.heartbeat(hs, be.owner, be.frequency, be.mode == MODE_SEEK, level.getGameTime(), pos);

        boolean changed = wasPowered != be.powered;
        if (!(be.powered && be.mode == MODE_SEEK) && !be.ships.isEmpty()) {
            be.ships = List.of();
            be.shipLocks.clear();
            changed = true;
        }
        float b0 = be.bearing;
        long lf0 = be.lockedFlight;
        boolean moved = false;
        if (be.powered && be.mode == MODE_SEEK && level instanceof ServerLevel sl) {
            changed |= be.seek(sl);
            changed |= be.lockedFlight != lf0;
            moved = be.bearing != b0;
        } else if (be.powered) {
            float diff = delta(be.targetBearing - be.bearing);
            if (Math.abs(diff) > 0.001F) {
                boolean arrives = Math.abs(diff) <= DEGREES_PER_TICK;
                be.bearing = arrives ? be.targetBearing : norm(be.bearing + Math.signum(diff) * DEGREES_PER_TICK);
                moved = true;
                changed |= arrives;
            }
        }
        long now = level.getGameTime();
        boolean dirty = be.bearingDirty | moved;
        if (changed || dirty && (!moved || now - be.lastSyncT >= BEARING_SYNC)) {
            be.sync();
        } else {
            be.bearingDirty = dirty;
            if (moved || now % 20 == 0) be.setChanged();
        }
    }

    private boolean seek(ServerLevel level) {
        long now = level.getGameTime();
        if (now % SCAN_INTERVAL == 0) scan(level, now);
        boolean shipsChanged = now % SHIP_SCAN_INTERVAL == 7 && scanShips(level, now);

        float before = bearing;
        Flight locked = null;
        if (lockedFlight != 0) {
            for (Flight f : FlightManager.get(level).flights()) {
                if (f.id() == lockedFlight) { locked = f; break; }
            }
        }
        if (locked != null) {
            Vec3 p = locked.position(now);
            float want = bearingToPoint(p.x, p.z);
            float diff = delta(want - bearing);
            bearing = norm(bearing + Mth.clamp(diff, -SWEEP_PER_TICK, SWEEP_PER_TICK));
        } else {
            bearing = norm(bearing + SWEEP_PER_TICK);
        }
        targetBearing = bearing;
        return shipsChanged;
    }

    private boolean scanShips(ServerLevel level, long now) {
        if (!SableBridge.active() || owner == null) {
            boolean had = !ships.isEmpty();
            ships = List.of();
            shipLocks.clear();
            return had;
        }
        Vec3 wc = SableBridge.worldPos(level, worldPosition);
        UUID own = SableBridge.shipIdAt(level, worldPosition);
        FlightManager fm = FlightManager.get(level);
        java.util.List<SableBridge.ShipInfo> near = new java.util.ArrayList<>(SableBridge.largeShipsNear(level, wc.x, wc.z, DETECT_RANGE));
        near.sort(java.util.Comparator.comparingDouble(si -> si.worldCenter().distanceToSqr(wc)));
        java.util.List<SableBridge.ShipInfo> shown = new java.util.ArrayList<>();
        java.util.List<ShipFaction.Relation> rels = new java.util.ArrayList<>();
        for (SableBridge.ShipInfo si : near) {
            if (si.id().equals(own)) continue;
            Vec3 p = si.worldCenter();
            if (fm.chaffHides(wc.x, wc.z, p.x, p.z)) continue;
            shown.add(si);
            rels.add(ShipFaction.relation(level, si.id(), owner, this));
            if (shown.size() >= Balance.RADAR_MAX_SHIP_CONTACTS) break;
        }
        Set<UUID> keep = new HashSet<>();
        for (int i = 0; i < shown.size(); i++)
            if (rels.get(i) == ShipFaction.Relation.HOSTILE && shipLocks.contains(shown.get(i).id())) keep.add(shown.get(i).id());
        for (int i = 0; i < shown.size() && keep.size() < Balance.RADAR_MAX_SHIP_LOCKS; i++) {
            SableBridge.ShipInfo si = shown.get(i);
            if (rels.get(i) != ShipFaction.Relation.HOSTILE || keep.contains(si.id())) continue;
            if (Math.abs(delta(bearingToPoint(si.worldCenter().x, si.worldCenter().z) - bearing)) <= BEAM_HALF) keep.add(si.id());
        }
        shipLocks.clear();
        shipLocks.addAll(keep);
        java.util.List<ShipContact> out = new java.util.ArrayList<>(shown.size());
        for (int i = 0; i < shown.size(); i++) {
            SableBridge.ShipInfo si = shown.get(i);
            Vec3 c = si.worldCenter(), v = si.linearVelocity();
            double hl = Math.sqrt(v.x * v.x + v.z * v.z);
            float hx = hl > 1.0E-3 ? (float) (v.x / hl) : 0.0F, hz = hl > 1.0E-3 ? (float) (v.z / hl) : 0.0F;
            String n = si.name() == null ? "" : si.name();
            boolean locked = keep.contains(si.id());
            boolean mast = Masts.mastOnly(level, si);
            out.add(new ShipContact(si.id(), Math.round(c.x * 10.0) / 10.0, Math.round(c.y * 10.0) / 10.0, Math.round(c.z * 10.0) / 10.0,
                    Math.round(hx * 100.0F) / 100.0F, Math.round(hz * 100.0F) / 100.0F, mast ? 1 : si.blockCount(), mast ? 1.0F : (float) Math.round(si.maxSize()),
                    rels.get(i).code(), n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n, locked, ShipTargets.isDesignated(owner, si.id(), now)));
            if (locked) TrackNet.reportShip(level, owner, frequency, si.id(), now);
        }
        if (out.equals(ships)) return false;
        ships = List.copyOf(out);
        return true;
    }

    private void scan(ServerLevel level, long now) {
        if (lockedFlight != 0 && FlightManager.get(level).find(lockedFlight) == null) { locks.remove(lockedFlight); lockedFlight = locks.isEmpty() ? 0 : locks.iterator().next(); lockLost = 0; }
        Vec3 wc = SableBridge.worldPos(level, worldPosition);
        double cx = wc.x, cz = wc.z;
        boolean lockSeen = false;
        Set<Long> seenNow = new HashSet<>();
        for (Flight f : FlightManager.get(level).flights()) {
            if (now < f.start() || now > f.endTime()) continue;
            if (f.isOwnedBy(owner, ownerName)) continue;
            if (Hypersonic.hidden(f, now)) continue;
            Vec3 p = f.position(now);
            double dx = p.x - cx, dz = p.z - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > CruiseRoute.radarRange(f, DETECT_RANGE)) continue;
            if (FlightManager.get(level).chaffHides(cx, cz, p.x, p.z)) continue;
            boolean held = locks.contains(f.id()) || f.id() == lockedFlight;
            if (!held && Math.abs(delta(bearingToPoint(p.x, p.z) - bearing)) > BEAM_HALF) continue;
            if (!held && locks.size() >= MAX_LOCKS) continue;
            if (lockedFlight == 0) lockedFlight = f.id();
            locks.add(f.id());
            seenNow.add(f.id());
            if (f.id() == lockedFlight) lockSeen = true;
            if (owner != null) {
                TrackNet.report(level, owner, frequency, f.id(), now);
                AlarmNet.raise(level, owner, frequency);
            }
        }
        locks.retainAll(seenNow);
        if (lockedFlight != 0) {
            if (lockSeen) lockLost = 0;
            else if (++lockLost * SCAN_INTERVAL > LOCK_LOST_TICKS) { lockedFlight = locks.isEmpty() ? 0 : locks.iterator().next(); lockLost = 0; }
        }
    }

    private float bearingToPoint(double x, double z) {
        Vec3 c = SableBridge.worldPos(level, worldPosition);
        return norm((float) Math.toDegrees(Math.atan2(x - c.x, -(z - c.z))));
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RadarBlockEntity be) {
        if (be.isOnline()) MachineLoop.ensure(be, WmdSounds.RADAR_RUN.get(), be::isOnline, 3.0, Balance.SND_MACHINE, false);
        if (!be.shownReady) {
            be.shown = be.prevShown = be.bearing;
            be.shownReady = true;
        }

        if (be.powered && be.mode == MODE_SEEK && be.lockedFlight == 0 && be.syncT >= 0) { be.bearing = norm(be.baseBearing + SWEEP_PER_TICK * (float) Math.max(0L, level.getGameTime() - be.syncT)); be.targetBearing = be.bearing; }
        else if (be.powered && be.mode == MODE_TARGET) { float df = delta(be.targetBearing - be.bearing); if (Math.abs(df) > 0.001F) be.bearing = Math.abs(df) <= DEGREES_PER_TICK ? be.targetBearing : norm(be.bearing + Math.signum(df) * DEGREES_PER_TICK); }
        be.prevShown = be.shown;
        be.shown = norm(be.shown + delta(be.bearing - be.shown) * 0.4F);
    }

    public float getShownBearing(float partialTick) {
        return norm(prevShown + delta(shown - prevShown) * partialTick);
    }

    private static int lo(int v) { return v & 0xFFFF; }
    private static int hi(int v) { return (v >>> 16) & 0xFFFF; }

    public ContainerData getContainerData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> isFormed() ? 1 : 0;
                    case 1 -> Math.round(bearing * 10.0F) % 3600;
                    case 2 -> Math.round(targetBearing * 10.0F) % 3600;
                    case 3 -> powered ? 1 : 0;
                    case 4 -> lo(store.getEnergyStored());
                    case 5 -> hi(store.getEnergyStored());
                    case 6 -> mode;
                    case 7 -> frequency;
                    case 8 -> lockedFlight != 0 ? 1 : 0;
                    case 9 -> level instanceof ServerLevel sl && owner != null && mode == MODE_SEEK && isOnline() && TrackNet.satFeed(sl, owner, frequency, sl.getGameTime()) ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int value) {}

            @Override
            public int getCount() { return DATA_COUNT; }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        security.write(tag);
        tag.putFloat("bearing", bearing);
        tag.putFloat("target", targetBearing);
        tag.putInt("energy", store.getEnergyStored());
        tag.putBoolean("powered", powered);
        tag.putInt("mode", mode);
        tag.putInt("frequency", frequency);
        tag.putLong("locked", lockedFlight);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        security.read(tag);
        bearing = norm(tag.getFloat("bearing"));
        baseBearing = bearing;
        syncT = tag.contains("syncT") ? tag.getLong("syncT") : -1L;
        targetBearing = tag.contains("target") ? norm(tag.getFloat("target")) : bearing;
        store.set(tag.getInt("energy"));
        powered = tag.getBoolean("powered");
        mode = tag.getInt("mode") == MODE_SEEK ? MODE_SEEK : MODE_TARGET;
        frequency = tag.contains("frequency") ? Mth.clamp(tag.getInt("frequency"), 1, 9999) : 1;
        lockedFlight = tag.getLong("locked");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");

        java.util.List<ShipContact> sc = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag x : tag.getList("shipC", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (sc.size() >= Balance.RADAR_MAX_SHIP_CONTACTS) break;
            if (x instanceof CompoundTag c) sc.add(ShipContact.load(c));
        }
        ships = List.copyOf(sc);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        if (level != null) tag.putLong("syncT", level.getGameTime());
        if (!ships.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < ships.size() && i < Balance.RADAR_MAX_SHIP_CONTACTS; i++) l.add(ships.get(i).save());
            tag.put("shipC", l);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}

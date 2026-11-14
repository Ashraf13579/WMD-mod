package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class TurntableBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int S_IDLE = 0, S_TURNING = 1, S_BLOCKED = 2, S_NO_POWER = 3, S_NOT_SQUARE = 4;

    public static final int O_RIGHT = 0, O_LEFT = 1, O_HALF = 2, O_NORTH = 3, O_EAST = 4, O_SOUTH = 5, O_WEST = 6, A_FIRST = O_RIGHT, A_LAST = O_WEST;
    static final float TT_PITCH = 0.82F;

    private static final Map<ResourceKey<Level>, Set<Long>> ACTIVE = Statics.server(new HashMap<>());

    private static final Map<ResourceKey<Level>, Plate[]> PLATES = Statics.server(new java.util.concurrent.ConcurrentHashMap<>());

    public record Plate(BlockPos head, UUID ship, double cx, double cz, double top, double r, double from, double delta, long startAt, int ticks, UUID craft,
                        Map<Long, double[]> holds) {
        double t(double now) { return Math.max(0.0, Math.min(1.0, (now - startAt) / Math.max(1, ticks))); }

        public double angle(double now) { return from + delta * ease(t(now)); }

        public double rate(double now) {
            double t = (now - startAt) / Math.max(1, ticks);
            return t <= 0.0 || t >= 1.0 ? 0.0 : delta * 6.0 * t * (1.0 - t) / Math.max(1, ticks);
        }

        public Vec3 velocity(ServerLevel sl, double now, Vec3 w) {
            Vec3 l = ship == null ? w : SableBridge.toShip(sl, ship, w);
            if (l == null) return Vec3.ZERO;
            double rx = l.x - cx, rz = l.z - cz;
            if (rx * rx + rz * rz > (r + 0.3) * (r + 0.3)) return Vec3.ZERO;
            double om = -Math.toRadians(rate(now) * 20.0);
            Vec3 v = new Vec3(om * rz, 0.0, -om * rx);
            if (ship == null) return v;
            Vec3 a = SableBridge.toWorld(sl, l), b = SableBridge.toWorld(sl, l.add(v));
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, ship);
            Vec3 sv = si == null ? Vec3.ZERO : si.linearVelocity();
            return (a == null || b == null ? v : b.subtract(a)).add(sv);
        }

        public Vec3 hold(ServerLevel sl, double now, UUID id, BlockPos gear) {
            if (craft == null || !craft.equals(id)) return null;
            double[] a = holds.get(gear.asLong());
            if (a == null) return null;
            double d = Math.toRadians(angle(now) - from), c = Math.cos(d), s = Math.sin(d);
            Vec3 l = new Vec3(cx + a[0] * c - a[1] * s, top, cz + a[0] * s + a[1] * c);
            return ship == null ? l : SableBridge.toWorld(sl, l);
        }
    }

    static double ease(double t) { return t * t * (3.0 - 2.0 * t); }

    static double wrap180(double a) { a = a % 360.0; if (a > 180.0) a -= 360.0; if (a <= -180.0) a += 360.0; return a; }

    private volatile Plate plate;

    private Object platform;
    private long platformRetry;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int n, order, stat, ticks;
    private double heading, from, delta, yaw0;
    private boolean turning, sig, tilesChecked;
    private long startAt, nextScan, nextCheck, nextLook, workedAt = Long.MIN_VALUE;
    private UUID craft;

    private Map<Long, double[]> holds = Map.of();

    private String msg = "", craftName = "";
    private int[] msgArgs = new int[0];
    private int wheelsOn, wheelsAll;

    long coverAt = Long.MIN_VALUE;
    boolean covered;

    public TurntableBlockEntity(BlockPos pos, BlockState state) {
        super(TurntableBlocks.TURNTABLE_BE.get(), pos, state, 0, Balance.TT_BUFFER, Balance.TT_RECEIVE);
    }

    public int size() { return n; }
    public double radius() { return (n - 1) / 2.0; }
    public boolean turning() { return turning; }
    public int stat() { return stat; }
    public int order() { return order; }
    public String msg() { return msg; }
    public int[] msgArgs() { return msgArgs; }
    public String craftName() { return craftName; }
    public int wheelsOn() { return wheelsOn; }
    public int wheelsAll() { return wheelsAll; }

    public double cx() { return worldPosition.getX() + n / 2.0; }
    public double cz() { return worldPosition.getZ() + n / 2.0; }
    public double top() { return worldPosition.getY() + 1.0; }

    public double angle(double now) { return turning ? from + delta * ease(Math.max(0.0, Math.min(1.0, (now - startAt) / Math.max(1, ticks)))) : heading; }

    public double target() { return turning ? from + delta : heading; }
    public long tripFe(double deg) { return (long) Math.ceil((double) n * n * Math.abs(deg) / 90.0 * Balance.TT_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }
    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    boolean head() { return getBlockState().getValue(TurntableBlock.HEAD); }

    private boolean tile(BlockPos p) { return level != null && level.getBlockState(p).getBlock() instanceof TurntableBlock; }

    private boolean measure() {
        int nx = 0, nz = 0;
        while (nx < Balance.TT_MAX + 1 && tile(worldPosition.east(nx))) nx++;
        while (nz < Balance.TT_MAX + 1 && tile(worldPosition.south(nz))) nz++;
        boolean whole = nx == nz && nx >= Balance.TT_MIN && nx <= Balance.TT_MAX;
        for (int x = 0; whole && x < nx; x++) for (int z = 0; whole && z < nz; z++) if (!tile(worldPosition.offset(x, 0, z))) whole = false;
        int nn = whole ? nx : 0;
        if (nn != n) { n = nn; sync(); }
        if (n == 0 && !turning && stat != S_NOT_SQUARE) { stat = S_NOT_SQUARE; sync(); }
        else if (n > 0 && stat == S_NOT_SQUARE) { stat = S_IDLE; sync(); }
        return whole;
    }

    static boolean inside(int n, int x, int z) {
        double r = (n - 1) / 2.0, ox = Math.abs(x + 0.5 - n / 2.0) + 0.5, oz = Math.abs(z + 0.5 - n / 2.0) + 0.5;
        return ox * ox + oz * oz <= r * r + 1.0E-6;
    }

    private void setOpen(ServerLevel sl, boolean open) {
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) {
            if (!inside(n, x, z)) continue;
            BlockPos p = worldPosition.offset(x, 0, z);
            BlockState s = sl.getBlockState(p);
            if (s.getBlock() instanceof TurntableBlock && s.getValue(TurntableBlock.OPEN) != open) sl.setBlock(p, s.setValue(TurntableBlock.OPEN, open), 2);
        }
    }

    private AABB column(ServerLevel sl) {
        AABB plot = new AABB(worldPosition.getX(), worldPosition.getY() - 1, worldPosition.getZ(), worldPosition.getX() + n, worldPosition.getY() + 25, worldPosition.getZ() + n);
        Vec3 a = SableBridge.toWorld(sl, new Vec3(plot.minX, plot.minY, plot.minZ)), b = SableBridge.toWorld(sl, new Vec3(plot.maxX, plot.maxY, plot.maxZ));
        return a == null || b == null ? plot : new AABB(a, b);
    }

    private Vec3 local(ServerLevel sl, Vec3 w) {
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        return own == null || w == null ? w : SableBridge.toShip(sl, own, w);
    }

    private Vec3 localDir(ServerLevel sl, Vec3 a, Vec3 b) {
        Vec3 la = local(sl, a), lb = local(sl, b);
        return la == null || lb == null ? b.subtract(a) : lb.subtract(la);
    }

    static double compass(Vec3 d) { return Math.toDegrees(Math.atan2(d.x, -d.z)); }

    record Look(UUID id, String why, int on, int all, String name, List<LandingGearBlockEntity> gears) {}

    private Map<UUID, List<LandingGearBlockEntity>> gearsByCraft(ServerLevel sl) {
        Map<UUID, List<LandingGearBlockEntity>> by = new LinkedHashMap<>();
        Vec3 c = SableBridge.toWorld(sl, new Vec3(cx(), top(), cz()));
        if (c == null) c = new Vec3(cx(), top(), cz());
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        for (LandingGearBlockEntity g : LandingGearBlockEntity.gearsNear(sl, c, radius() + 64.0)) {
            UUID s = g.ship();
            if (s == null || s.equals(own)) continue;
            by.computeIfAbsent(s, k -> new ArrayList<>()).add(g);
        }
        return by;
    }

    private double[] onPlate(ServerLevel sl, LandingGearBlockEntity g) {
        Vec3 w = g.wheelWorld(sl), l = w == null ? null : local(sl, w);
        if (l == null) return new double[] { Double.NaN, 0.0 };
        return new double[] { Math.hypot(l.x - cx(), l.z - cz()), Math.abs(l.y - top()) < 1.0 ? 1.0 : 0.0 };
    }

    private Look look(ServerLevel sl) {
        double r = radius();
        List<UUID> cands = new ArrayList<>();
        Map<UUID, List<LandingGearBlockEntity>> by = gearsByCraft(sl);
        for (Map.Entry<UUID, List<LandingGearBlockEntity>> e : by.entrySet())
            for (LandingGearBlockEntity g : e.getValue()) {
                double[] d = onPlate(sl, g);
                if (g.wow && d[1] > 0.0 && d[0] <= r + 1.0) { cands.add(e.getKey()); break; }
            }
        if (cands.isEmpty()) return new Look(null, null, 0, 0, "", List.of());
        UUID id = cands.get(0);
        List<LandingGearBlockEntity> gs = by.get(id);
        SableBridge.ShipInfo si = SableBridge.shipInfo(sl, id);
        String name = si == null || si.name() == null ? "" : si.name();
        int on = 0;
        boolean off = false, air = false, throttle = false;
        for (LandingGearBlockEntity g : gs) {
            double[] d = onPlate(sl, g);
            if (!g.wow) air = true;
            if (Double.isNaN(d[0]) || d[0] > r - Balance.TT_EDGE || d[1] <= 0.0) off = true; else on++;
            if (!g.parked) throttle = true;
        }
        int all = Math.max(gs.size(), LandingGearBlockEntity.lockedAboard(sl, id));
        String why = cands.size() > 1 ? "two_crafts" : off || on < all ? "wheel_off" : air ? "not_on_wheels" : throttle ? "throttle" : null;
        return new Look(id, why, on, all, name, gs);
    }

    private double craftYaw(ServerLevel sl, UUID id) { return craftAxis(sl, id, new Vec3(1.0, 0.0, 0.0)); }

    private double craftAxis(ServerLevel sl, UUID id, Vec3 axis) {
        SableBridge.ShipInfo si = id == null ? null : SableBridge.shipInfo(sl, id);
        Vec3 p = si == null ? null : SableBridge.toShip(sl, id, si.worldCenter());
        if (p == null) return Double.NaN;
        Vec3 a = SableBridge.toWorld(sl, p), b = SableBridge.toWorld(sl, p.add(axis));
        if (a == null || b == null) return Double.NaN;
        return compass(localDir(sl, a, b));
    }

    private double noseHeading(ServerLevel sl, Look lk) {
        Vec3 nose = null, mains = Vec3.ZERO;
        int m = 0;
        for (LandingGearBlockEntity g : lk.gears()) {
            Vec3 w = g.wheelWorld(sl);
            if (w == null) continue;
            if (g.role() == LandingGearBlockEntity.NOSE) nose = w; else { mains = mains.add(w); m++; }
        }
        if (nose != null && m > 0) {
            Vec3 d = localDir(sl, mains.scale(1.0 / m), nose);
            if (d.x * d.x + d.z * d.z > 0.01) return compass(d);
        }
        return craftAxis(sl, lk.id(), new Vec3(0.0, 0.0, -1.0));
    }

    private double[] sweep(ServerLevel sl, UUID id) {
        int[] b = SableBridge.plotBox(sl, id);
        if (b == null) return null;
        long vol = (long) (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
        if (vol <= 0 || vol > 400_000) return null;
        double rs = 0.0, ymax = top();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = b[0]; x <= b[3]; x++) for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) {
            q.set(x, y, z);
            if (sl.getBlockState(q).isAir()) continue;
            for (int k = 0; k < 4; k++) {
                Vec3 w = SableBridge.toWorld(sl, new Vec3(x + (k & 1), y + 1.0, z + (k >> 1)));
                Vec3 l = w == null ? null : local(sl, w);
                if (l == null) continue;
                rs = Math.max(rs, Math.hypot(l.x - cx(), l.z - cz()));
                ymax = Math.max(ymax, l.y);
            }
        }
        return new double[] { rs, ymax };
    }

    private BlockPos blockedAt(ServerLevel sl, UUID id) {
        double[] sw = sweep(sl, id);
        if (sw == null) return null;
        double rs = Math.min(sw[0] + 0.3, Balance.TT_CLEAR_R);
        int y0 = worldPosition.getY() + 1, y1 = Math.min(worldPosition.getY() + 1 + 24, (int) Math.ceil(sw[1]));
        int x0 = (int) Math.floor(cx() - rs), x1 = (int) Math.ceil(cx() + rs), z0 = (int) Math.floor(cz() - rs), z1 = (int) Math.ceil(cz() + rs);
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            double dx = Math.max(Math.abs(x + 0.5 - cx()) - 0.5, 0.0), dz = Math.max(Math.abs(z + 0.5 - cz()) - 0.5, 0.0);
            if (dx * dx + dz * dz > rs * rs) continue;
            q.set(x, y, z);
            if (!sl.isLoaded(q)) continue;
            BlockState s = sl.getBlockState(q);
            if (s.isAir() || s.getCollisionShape(sl, q).isEmpty()) continue;
            return q.immutable();
        }
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        Vec3 c = SableBridge.toWorld(sl, new Vec3(cx(), top(), cz()));
        if (c == null) return null;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(c, c).inflate(rs, 12.0, rs))) {
            if (s.id().equals(id) || s.id().equals(own)) continue;
            Vec3 l = local(sl, s.worldCenter());
            if (l != null && Math.hypot(l.x - cx(), l.z - cz()) < rs) return BlockPos.containing(s.worldCenter());
        }
        return null;
    }

    String order(int o) {
        if (!(level instanceof ServerLevel sl)) return "busy";
        String k = start(sl, o);
        msg = k;
        sync();
        return k;
    }

    public Component answer(String key) {
        Object[] a = new Object[msgArgs.length];
        for (int i = 0; i < a.length; i++) a[i] = msgArgs[i];
        return Component.translatable("message.wmd.turntable." + (key == null || key.isEmpty() ? "idle" : key), a);
    }

    private String start(ServerLevel sl, int o) {
        msgArgs = new int[0];
        if (!AirGate.on()) return "off";
        if (o < A_FIRST || o > A_LAST) return "busy";
        if (turning) return "busy";
        order = o;
        if (!measure()) return "not_square";
        Look lk = look(sl);
        if (lk.why() != null) { stat = S_BLOCKED; return lk.why(); }
        double d;
        if (o == O_RIGHT) d = 90.0;
        else if (o == O_LEFT) d = -90.0;
        else if (o == O_HALF) d = 180.0;
        else {
            double nose = lk.id() != null ? noseHeading(sl, lk) : heading;
            if (Double.isNaN(nose)) return "no_nose";
            d = wrap180((o - O_NORTH) * 90.0 - nose);
            if (Math.abs(d) < 0.5) { stat = S_IDLE; return "already"; }
        }
        if (lk.id() != null) {
            BlockPos b = blockedAt(sl, lk.id());
            if (b != null) { stat = S_BLOCKED; msgArgs = new int[] { b.getX(), b.getY(), b.getZ() }; return "blocked"; }
        }
        long fe = tripFe(d);
        if (energy().getEnergyStored() < fe) { stat = S_NO_POWER; msgArgs = new int[] { (int) Math.min(Integer.MAX_VALUE, fe) }; return "no_power"; }
        useExtraEnergy((int) Math.min(Integer.MAX_VALUE, fe));
        long now = sl.getGameTime();
        double r = radius();
        ticks = Math.max(20, (int) Math.round(Balance.TT_TICKS_90 * Math.abs(d) / 90.0 * Math.max(1.0, r / Balance.TT_R_REF)));
        from = heading;
        delta = d;
        startAt = now;
        nextCheck = now + Balance.TT_CHECK_TICKS;
        craft = lk.id();
        yaw0 = craft == null ? 0.0 : craftYaw(sl, craft);
        Map<Long, double[]> hs = new HashMap<>();
        for (LandingGearBlockEntity g : lk.gears()) {
            Vec3 w = g.wheelWorld(sl), l = w == null ? null : local(sl, w);
            if (l != null) hs.put(g.getBlockPos().asLong(), new double[] { l.x - cx(), l.z - cz() });
        }
        holds = Map.copyOf(hs);
        turning = true;
        stat = S_TURNING;
        setOpen(sl, true);
        ACTIVE.computeIfAbsent(sl.dimension(), k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        refloor(sl, this);
        keepPlatform(sl);
        if (craft != null) SableBridge.wake(sl, craft);
        playOnce(sl, WmdSounds.LIFT_CLUNK.get(), Balance.SND_MECH, TT_PITCH);
        playOnce(sl, WmdSounds.LIFT_RAIL.get(), Balance.SND_CLICK, TT_PITCH);
        msgArgs = new int[] { (int) Math.round(d) };
        Wmd.LOGGER.info("Turntable at {}: turn {} deg ({} x {}, {} ticks, {} FE, craft {})", worldPosition, Math.round(d), n, n, ticks, fe, craft == null ? "none" : lk.name());
        return "turning";
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;

        BlockState ws = sl.getBlockState(worldPosition);
        if (ws != getBlockState() && ws.getBlock() == getBlockState().getBlock()) setBlockState(ws);
        if (!head()) return false;
        if (!AirGate.on()) return false;
        long now = sl.getGameTime();
        if (now == workedAt) return turning;
        workedAt = now;
        boolean s = sl.hasNeighborSignal(worldPosition);
        if (s && !sig) order(order);
        sig = s;
        if (!turning) {
            if (!tilesChecked) { tilesChecked = true; measure(); if (n > 0) setOpen(sl, false); }
            if (now >= nextScan) { nextScan = now + Balance.TT_SCAN_TICKS; measure(); }
            if (n > 0 && now >= nextLook) {
                nextLook = now + 20;
                Look lk = look(sl);
                String nm = lk.id() == null ? "" : lk.name().isEmpty() ? "?" : lk.name();
                if (!nm.equals(craftName) || lk.on() != wheelsOn || lk.all() != wheelsAll) { craftName = nm; wheelsOn = lk.on(); wheelsAll = lk.all(); sync(); }
            }
            return false;
        }
        if (plate == null || now % 40 == 0) refloor(sl, this);
        keepPlatform(sl);
        if (now % 10 == 0) wakeRiders(sl);
        carry(sl, now);
        if (now >= nextCheck) {
            nextCheck = now + Balance.TT_CHECK_TICKS;
            String why = check(sl, now);
            if (why != null) { end(sl, now, why); return false; }
        }
        if (now - startAt >= ticks) end(sl, now, null);
        return turning;
    }

    private String check(ServerLevel sl, long now) {
        if (craft == null) return null;
        if (SableBridge.shipInfo(sl, craft) == null) return "craft_gone";
        List<LandingGearBlockEntity> gs = gearsByCraft(sl).get(craft);
        if (gs == null || gs.isEmpty()) return "lifted";
        boolean any = false;
        double r = radius();
        for (LandingGearBlockEntity g : gs) {
            if (g.wow) any = true;
            double[] d = onPlate(sl, g);
            if (!Double.isNaN(d[0]) && d[0] > r + 0.1) return "wheel_left";
        }
        if (!any) return "lifted";
        double yaw = craftYaw(sl, craft);
        if (!Double.isNaN(yaw) && !Double.isNaN(yaw0)) {
            double turned = wrap180(yaw - yaw0), should = angle(now) - from;
            if (Math.abs(wrap180(turned - should)) > Balance.TT_LAG_DEG) return "lag";
        }
        return null;
    }

    private void end(ServerLevel sl, long now, String why) {
        heading = ((why == null ? from + delta : angle(now)) % 360.0 + 360.0) % 360.0;
        turning = false;
        setOpen(sl, false);
        Set<Long> a = ACTIVE.get(sl.dimension());
        if (a != null) a.remove(worldPosition.asLong());
        dropPlatform(sl);
        craft = null;
        holds = Map.of();
        refloor(sl, this);
        stat = why == null ? S_IDLE : S_BLOCKED;
        msg = why == null ? "done" : why;
        msgArgs = new int[] { (int) Math.round(heading) % 360 };
        playOnce(sl, WmdSounds.LIFT_CLUNK.get(), Balance.SND_MECH, TT_PITCH * 0.95F);
        playOnce(sl, WmdSounds.LIFT_RAIL.get(), Balance.SND_CLICK, TT_PITCH * 0.95F);
        if (why != null) Wmd.LOGGER.info("Turntable at {}: stopped at {} deg ({})", worldPosition, Math.round(heading), why);
        sync();
    }

    private void carry(ServerLevel sl, long now) {
        double dd = angle(now) - angle(now - 1);
        if (Math.abs(dd) < 1.0E-6) return;
        double phi = Math.toRadians(dd), c = Math.cos(phi), s = Math.sin(phi), r = radius(), top = top();
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        for (Entity e : sl.getEntities((Entity) null, column(sl), en -> !en.isSpectator() && !en.isPassenger() && !(en instanceof Player))) {
            Vec3 l = ship == null ? e.position() : SableBridge.toShip(sl, ship, e.position());
            if (l == null) continue;
            double dx = l.x - cx(), dz = l.z - cz();
            if (dx * dx + dz * dz > r * r || l.y < top - 1.2 || l.y > top + 1.0) continue;
            Vec3 nl = new Vec3(cx() + dx * c - dz * s, top, cz() + dx * s + dz * c), nw = ship == null ? nl : SableBridge.toWorld(sl, nl);
            if (nw == null) continue;
            e.teleportTo(nw.x, nw.y, nw.z);
            e.setYRot(e.getYRot() + (float) dd);
            e.setDeltaMovement(e.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            e.fallDistance = 0.0F;
            e.setOnGround(true);
        }
    }

    private void keepPlatform(ServerLevel sl) {
        if (!turning || n <= 0 || plate == null) { dropPlatform(sl); return; }
        if (platform != null || sl.getGameTime() < platformRetry) return;
        platform = SableBridge.turntableDisc(sl, this, n);
        if (platform == null) platformRetry = sl.getGameTime() + 20;
    }

    private void dropPlatform(ServerLevel sl) {
        if (platform == null) return;
        SableBridge.turntableDiscRemove(sl, platform);
        platform = null;
    }

    private void wakeRiders(ServerLevel sl) {
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, column(sl))) if (!s.id().equals(own)) SableBridge.wake(sl, s.id());
    }

    public Plate plate() { return plate; }

    private static void refloor(ServerLevel sl, TurntableBlockEntity me) {
        Set<Long> a = ACTIVE.get(sl.dimension());
        List<Plate> l = new ArrayList<>();
        if (me != null && !me.turning) me.plate = null;
        if (a != null) for (Long p : a) {
            BlockPos bp = BlockPos.of(p);
            TurntableBlockEntity t = me != null && me.worldPosition.equals(bp) ? me : sl.isLoaded(bp) && sl.getBlockEntity(bp) instanceof TurntableBlockEntity x ? x : null;
            if (t == null || t.isRemoved()) continue;
            t.plate = !t.turning || t.n <= 0 ? null : new Plate(bp.immutable(), SableBridge.shipIdAt(sl, bp), t.cx(), t.cz(), t.top(), t.radius(), t.from, t.delta, t.startAt, t.ticks, t.craft, t.holds);
            if (t.plate != null) l.add(t.plate);
        }
        if (l.isEmpty()) PLATES.remove(sl.dimension());
        else PLATES.put(sl.dimension(), l.toArray(new Plate[0]));
    }

    public static Plate plateAt(ServerLevel level, Vec3 q, double now) {
        Plate[] a = PLATES.get(level.dimension());
        if (a == null) return null;
        for (Plate f : a) {
            Vec3 l = f.ship() == null ? q : SableBridge.toShip(level, f.ship(), q);
            if (l == null) continue;
            double dx = l.x - f.cx(), dz = l.z - f.cz();
            if (dx * dx + dz * dz > f.r() * f.r()) continue;
            if (l.y <= f.top() && l.y >= f.top() - 1.0) return f;
        }
        return null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            Set<Long> a = ACTIVE.get(sl.dimension());
            if (a != null) a.remove(worldPosition.asLong());
            dropPlatform(sl);
            turning = false;
            refloor(sl, null);
        }
        super.setRemoved();
    }

    @Override public void ccAnimate(CcBones model, double tt, float pt) {}

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.LIFT_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected float soundPitch() { return TT_PITCH; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return head() && side == Direction.DOWN ? energy() : null; }

    @Override protected String detail() { return head() ? n + " x " + n + ", heading " + Math.round(heading) + ", " + (turning ? "turning" : "idle") + ", 90 deg " + tripFe(90.0) + " FE" : "tile"; }

    @Override
    public Component status() {
        String k = n == 0 ? "message.wmd.turntable.status_not_square" : turning ? "message.wmd.turntable.status_turning" : "message.wmd.turntable.status_idle";
        return Component.translatable("block.wmd.turntable").append(": ").append(Component.translatable(k, n, n, Math.round(heading) % 360, tripFe(90.0), energy().getEnergyStored(),
                Balance.TT_MIN, Balance.TT_MAX));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("ttN", n);
        tag.putInt("ttOrder", order);

        tag.putDouble("ttHeading", turning && level != null ? (angle(level.getGameTime()) % 360.0 + 360.0) % 360.0 : heading);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        n = tag.getInt("ttN");
        order = Math.max(A_FIRST, Math.min(A_LAST, tag.getInt("ttOrder")));
        heading = tag.getDouble("ttHeading");
        if (tag.contains("ttTurning")) {
            turning = tag.getBoolean("ttTurning");
            from = tag.getDouble("ttFrom"); delta = tag.getDouble("ttDelta"); startAt = tag.getLong("ttAt"); ticks = tag.getInt("ttTicks");
            stat = tag.getInt("ttStat");
            msg = tag.getString("ttMsg");
            msgArgs = tag.getIntArray("ttArgs");
            craftName = tag.getString("ttCraft");
            wheelsOn = tag.getInt("ttOn"); wheelsAll = tag.getInt("ttAll");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("ttN", n);
        tag.putInt("ttOrder", order);
        tag.putDouble("ttHeading", heading);
        tag.putBoolean("ttTurning", turning);
        tag.putDouble("ttFrom", from); tag.putDouble("ttDelta", delta); tag.putLong("ttAt", startAt); tag.putInt("ttTicks", ticks);
        tag.putInt("ttStat", stat);
        tag.putString("ttMsg", msg == null ? "" : msg);
        tag.putIntArray("ttArgs", msgArgs);
        tag.putString("ttCraft", craftName == null ? "" : craftName);
        tag.putInt("ttOn", wheelsOn); tag.putInt("ttAll", wheelsAll);
        return tag;
    }
}

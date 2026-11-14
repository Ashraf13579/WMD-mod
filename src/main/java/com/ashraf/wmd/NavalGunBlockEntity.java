package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class NavalGunBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host, GunnerGun {

    static final float YAW_SIGN = 1.0F, ELEV_SIGN = 1.0F, RECOIL_SIGN = 1.0F, DRUM_SIGN = 1.0F, HOIST_SIGN = 1.0F, FLAP_SIGN = 1.0F, CAP_SIGN = 1.0F;

    static final int SHOT_KIND = 20, HEAVY_KIND = 30;

    static final int SUPER = 100;

    static final int ST_READY = 0, ST_LOADING = 1, ST_LAYING = 2, ST_EMPTY = 3, ST_MANNED = 4, ST_NO_CRAFT = 5, ST_NO_POWER = 6, ST_BLOCKED = 7, ST_OUT = 8,
            ST_NO_CHARGE = 9;
    private static final double DECK_RAIL_A = Math.toRadians(70.0), DECK_HOIST_L = 8.5, XL_HOIST_L = 16.0;

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> GUNS = Statics.server(new HashMap<>());

    private final NavalGuns.Spec spec;
    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private final int[] rack;
    private int rackN, charges, loaded = -1, seq, steps;
    private boolean heavyMode;
    private long cycleAt = -1L, lastShot = Long.MIN_VALUE / 2;
    private UUID ship;

    private float deploy, yaw, elev;
    private int claimKind;
    private long claimBy, lastClaim = Long.MIN_VALUE / 2, triggerUntil, nextShotAt, firingUntil, lastSyncAt, nextLineCheck;
    private Vec3 aimPoint;
    private boolean outOfField, lineBlocked, trainStop, outOfRange, moving, noPower;

    private Vec3 orderTarget, orderVel = Vec3.ZERO;
    private long orderAt, orderUntil;

    private int salvoK = -1, salvoN;
    private boolean salvoHeavy, salvoEye;
    private long salvoNext;
    private float syncedYaw = 999.0F, syncedElev, syncedDeploy = -1.0F;
    private int syncedFlags = -1;

    private float yawT, elevT, deployT, yawShown, elevShown, deployShown, spin;
    private final float[] kick = new float[3];
    private final long[] flashUntil = new long[3];
    private long flapUntil;
    private double lastT = Double.NaN;
    private boolean clientMoving;

    public NavalGunBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, NavalGuns.Spec spec) {
        super(type, pos, state, 0, 1, 0);
        this.spec = spec;
        this.rack = new int[spec.rack()];
    }

    NavalGuns.Spec spec() { return spec; }

    @Override public BlockPos gunPos() { return worldPosition; }
    @Override public boolean deployed() { return deploy >= 1.0F; }
    @Override public float deploy01() { return deploy; }
    @Override public boolean blockedNow() { return deploy >= 1.0F && (outOfField || lineBlocked || trainStop); }
    @Override public boolean firingNow() { return level != null && level.getGameTime() < firingUntil; }
    @Override public int rounds() { return rackN + (loaded >= 0 ? 1 : 0); }
    @Override public int roundsMax() { return spec.rack() + (spec.xl() ? 0 : 1); }
    @Override public boolean arcReady() { return false; }
    @Override public BitSet arcMap() { return new BitSet(); }
    @Override public float yawDeg() { return yaw; }
    @Override public float elevDeg() { return elev; }
    @Override public long lastHitAt() { return Long.MIN_VALUE / 2; }
    @Override public boolean heavy() { return true; }
    @Override public double eyeRange() { return spec.eyeRange(); }
    @Override public double eyeSpeed() { return spec.eyeSpeed(); }
    int rackCount() { return rackN; }
    int charges() { return charges; }
    boolean heavyMode() { return heavyMode; }
    UUID ship() { return ship; }

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
        for (int i = 0; i < rackN; i++) l.add(roundItem(rack[i]));
        if (loaded >= 0) l.add(roundItem(loaded));
        for (int c = charges; c > 0; c -= 64) l.add(new ItemStack(ItemProcessBlockEntity.wmd(SuperGun.CHARGE), Math.min(64, c)));
        rackN = 0; loaded = -1; charges = 0;
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    static ItemStack roundItem(int code) {
        return new ItemStack(code == SUPER ? ItemProcessBlockEntity.wmd(SuperGun.SHELL) : Artillery.shellItem(Artillery.HOWITZER, code));
    }

    static int howitzerCode(ItemStack s) {
        return s.getItem() instanceof ArtShellItem a && a.gun() == Artillery.HOWITZER && Artillery.canFire(Artillery.HOWITZER, a.type()) && a.type() != Artillery.CANISTER
                ? a.type() : -1;
    }

    static boolean isSuperShell(ItemStack s) { return !s.isEmpty() && s.is(ItemProcessBlockEntity.wmd(SuperGun.SHELL)); }

    static boolean isCharge(ItemStack s) { return !s.isEmpty() && s.is(ItemProcessBlockEntity.wmd(SuperGun.CHARGE)); }

    static boolean isRound(ItemStack s) { return howitzerCode(s) >= 0 || isSuperShell(s) || isCharge(s); }

    boolean accepts(ItemStack s) { return howitzerCode(s) >= 0 || spec.xl() && (isSuperShell(s) || isCharge(s)); }

    boolean room(ItemStack s) { return isCharge(s) ? charges < spec.charges() : rackN < rack.length; }

    int roomCount(ItemStack s) { return isCharge(s) ? spec.charges() - charges : rack.length - rackN; }

    boolean loadOne(ItemStack one) {
        if (!(level instanceof ServerLevel) || !AirGate.on() || !accepts(one) || !room(one)) return false;
        if (isCharge(one)) charges++;
        else rack[rackN++] = isSuperShell(one) ? SUPER : howitzerCode(one);
        sync();
        return true;
    }

    String handLoad(Player player, ItemStack held) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return "message.wmd.naval_gun.off";
        if (!accepts(held)) return spec.xl() ? "message.wmd.naval_gun.wrong_xl" : "message.wmd.naval_gun.wrong";
        if (!room(held)) return "message.wmd.naval_gun.full";
        loadOne(held.copyWithCount(1));
        playOnce(sl, WmdSounds.GPOD_RELOAD.get(), Balance.SND_CLICK, isCharge(held) ? 0.6F : 0.75F);
        if (!player.getAbilities().instabuild) held.shrink(1);
        return "message.wmd.naval_gun.loaded";
    }

    void setHeavyMode(boolean on) { if (spec.xl() && heavyMode != on) { heavyMode = on; sync(); } }

    private int countKind(boolean heavyRound) {
        int n = 0;
        for (int i = 0; i < rackN; i++) if ((rack[i] == SUPER) == heavyRound) n++;
        return n;
    }

    private int takeKind(boolean heavyRound) {
        for (int i = 0; i < rackN; i++) {
            if ((rack[i] == SUPER) != heavyRound) continue;
            int v = rack[i];
            System.arraycopy(rack, i + 1, rack, i, rackN - i - 1);
            rackN--;
            return v;
        }
        return -1;
    }

    int nextRound(boolean eye) {
        if (!spec.xl()) return loaded >= 0 ? loaded : rackN > 0 ? rack[0] : -1;
        boolean h = !eye && heavyMode && countKind(true) > 0;
        for (int i = 0; i < rackN; i++) if ((rack[i] == SUPER) == h) return rack[i];
        return -1;
    }

    static List<NavalGunBlockEntity> gunsOf(ServerLevel level, UUID ship) {
        List<NavalGunBlockEntity> out = new ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = GUNS.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof NavalGunBlockEntity g && !g.isRemoved() && ship.equals(g.ship)) out.add(g);
        }
        out.sort(Comparator.comparingLong(g -> g.worldPosition.asLong()));
        return out;
    }

    @Override
    public boolean stationCommand(long now, BlockPos station, Vec3 point, boolean trigger) {
        if (!AirGate.on()) return false;
        long by = station.asLong();
        if (claimKind == GunTurretBlockEntity.STATION && claimBy != by && now - lastClaim <= Balance.GTUR_CLAIM_TICKS) return false;
        claimKind = GunTurretBlockEntity.STATION; claimBy = by; lastClaim = now;
        orderTarget = null;
        if (point != null) aimPoint = point;
        triggerUntil = trigger ? now + 3 : 0;
        return true;
    }

    boolean manned(long now) { return claimKind == GunTurretBlockEntity.STATION && now - lastClaim <= Balance.GTUR_CLAIM_TICKS; }

    String consoleOrder(ServerLevel sl, Vec3 target, Vec3 vel) {
        if (!AirGate.on()) return "message.wmd.naval_gun.off";
        long now = sl.getGameTime();
        if (ship == null) return "gui.wmd.fcc.gun.st." + ST_NO_CRAFT;
        if (manned(now)) return "gui.wmd.fcc.gun.st." + ST_MANNED;
        if (nextRound(false) < 0) return "gui.wmd.fcc.gun.st." + ST_EMPTY;
        orderTarget = target; orderVel = vel == null ? Vec3.ZERO : vel; orderAt = now; orderUntil = now + Balance.NGUN_ORDER_TICKS;
        return "gui.wmd.fcc.gun.ordered";
    }

    int consoleStatus(long now) {
        if (ship == null) return ST_NO_CRAFT;
        if (manned(now)) return ST_MANNED;
        if (nextRound(false) < 0) return ST_EMPTY;
        if (spec.xl() && heavyMode && nextRound(false) == SUPER && charges <= 0) return ST_NO_CHARGE;
        if (noPower) return ST_NO_POWER;
        if (orderTarget != null && outOfRange) return ST_OUT;
        if (deploy >= 1.0F && (lineBlocked || trainStop)) return ST_BLOCKED;
        if (!spec.xl() && loaded < 0 || cycleAt >= 0 || now < nextShotAt) return ST_LOADING;
        if (orderTarget != null) return ST_LAYING;
        return ST_READY;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); deploy = 0.0F; claimKind = 0; orderTarget = null; return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; claimKind = 0; orderTarget = null; }
            if (ship != null) GUNS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        moving = false;
        if (ship == null) { deploy = Math.max(0.0F, deploy - 0.1F); salvoK = -1; syncAim(sl, now); return false; }
        if (orderTarget != null && now > orderUntil) orderTarget = null;
        boolean claimed = manned(now);
        boolean ordered = orderTarget != null;
        boolean want = (claimKind != 0 && now - lastClaim <= Balance.GTUR_STOW_DELAY) || ordered;
        boolean power = want && ShipPower.draw(sl, ship, spec.feLay());
        noPower = want && !power;
        if (want && deploy < 1.0F && power) {
            if (deploy <= 0.0F) playOnce(sl, WmdSounds.HG_PORT.get(), Balance.SND_MECH, 1.3F);
            deploy = Math.min(1.0F, deploy + 0.1F);
        } else if (!want && deploy > 0.0F && salvoK < 0) {
            if (Math.abs(yaw) > 0.5F || Math.abs(elev) > 0.5F) lay(sl, 0.0, 0.0);
            else deploy = Math.max(0.0F, deploy - 0.1F);
            if (deploy <= 0.0F) claimKind = 0;
        }
        boolean ready = deploy >= 1.0F;

        if (!spec.xl() && loaded < 0 && cycleAt < 0 && rackN > 0 && now >= lastShot + 6) {
            cycleAt = now;
            playOnce(sl, WmdSounds.DG_BREECH.get(), Balance.SND_MECH, 1.0F);
            sync();
        }
        if (cycleAt >= 0 && now - cycleAt >= spec.hoist()) {
            if (!spec.xl() && loaded < 0 && rackN > 0) {
                loaded = rack[0];
                System.arraycopy(rack, 1, rack, 0, rackN - 1);
                rackN--;
                steps++;
            } else if (spec.xl()) playOnce(sl, WmdSounds.XL_BREECH.get(), Balance.SND_MECH, 1.0F);
            cycleAt = -1L;
            sync();
        }

        if (salvoK >= 0 && now >= salvoNext) fireBarrel(sl, now);

        double[] tgt = null;
        outOfField = false; outOfRange = false;
        if (ready && power && salvoK < 0) {
            if (claimed && aimPoint != null) tgt = eyeSolution(sl, aimPoint);
            else if (ordered) { tgt = consoleSolution(sl, now); if (tgt == null) outOfRange = true; }
            if (tgt != null) {
                double cy = Mth.clamp(tgt[0], -spec.trainMax(), spec.trainMax()), ce = Mth.clamp(tgt[1], -spec.elevDown(), spec.elevUp());
                outOfField = Math.abs(cy - tgt[0]) > 0.5 || Math.abs(ce - tgt[1]) > 0.5;
                tgt[0] = cy; tgt[1] = ce;
                lay(sl, tgt[0], tgt[1]);
            }
        }
        if (ready && now >= nextLineCheck) {
            nextLineCheck = now + 5;
            lineBlocked = lineHits(sl, spec.barrels() / 2, yaw, elev);
        }
        boolean aligned = tgt != null && Math.abs(tgt[0] - yaw) <= spec.align() && Math.abs(tgt[1] - elev) <= spec.align();
        boolean canShoot = ready && aligned && salvoK < 0 && now >= nextShotAt && cycleAt < 0 && !outOfField && !lineBlocked;
        if (canShoot && claimed && now < triggerUntil) startSalvo(sl, now, true);
        else if (canShoot && ordered && !claimed) { if (startSalvo(sl, now, false)) orderTarget = null; }
        syncAim(sl, now);
        return want;
    }

    private Vec3 pivotPlot() { return plotPoint(0.0, spec.trunY() / 16.0, 0.0); }

    private double[] eyeSolution(ServerLevel sl, Vec3 aim) {
        Vec3 pw = SableBridge.toWorld(sl, pivotPlot());
        if (pw == null) return null;
        double dist = Math.min(pw.distanceTo(aim), spec.eyeRange());
        int dur = Math.max(2, (int) Math.ceil(dist / spec.eyeSpeed()));
        Vec3 up = aim.add(0.0, Balance.ART_GRAVITY * dur * dur * 0.5, 0.0);
        Vec3 ap = SableBridge.toShip(sl, ship, up);
        double[] a = ap == null ? null : anglesTo(ap);
        return a == null ? null : new double[] { a[0], a[1], dur, 0.0, 0.0 };
    }

    private double[] consoleSolution(ServerLevel sl, long now) {
        Vec3 pw = SableBridge.toWorld(sl, pivotPlot());
        if (pw == null || orderTarget == null) return null;
        boolean heavyRound = spec.xl() && heavyMode && nextRound(false) == SUPER;
        double flight = 0.0;
        Vec3 t = orderTarget;
        double[] out = null;
        for (int it = 0; it < 3; it++) {
            t = orderTarget.add(orderVel.scale(now - orderAt + flight));
            double dx = t.x - pw.x, dz = t.z - pw.z, x = Math.hypot(dx, dz), y = t.y - pw.y;
            if (x < (heavyRound ? Balance.XGUN_HEAVY_MIN : spec.eyeMin())) return null;
            int k = 0;
            double v;
            if (heavyRound) {
                k = SuperGun.autoCharges(x, y);
                if (k < 0 || k > Balance.XGUN_HEAVY_CHARGES_MAX) return null;
                v = SuperGun.velocity(k);
            } else v = Math.sqrt(Balance.ART_GRAVITY * spec.conRange() * 1.02);
            double e = Artillery.elevation(v, x, y, false);
            if (Double.isNaN(e) || e > spec.elevUp()) return null;
            flight = Artillery.flightTicks(v, x, e);
            double ce = Math.cos(Math.toRadians(e));
            Vec3 d = new Vec3(ce * dx / x, Math.sin(Math.toRadians(e)), ce * dz / x);
            Vec3 ap = SableBridge.toShip(sl, ship, pw.add(d.scale(16.0)));
            double[] a = ap == null ? null : anglesTo(ap);
            if (a == null) return null;
            out = new double[] { a[0], a[1], flight, v, k };
        }
        consoleAim = t;
        return out;
    }

    private Vec3 consoleAim;

    private void lay(ServerLevel sl, double ty, double te) {
        float ny = (float) (yaw + Mth.clamp(ty - yaw, -spec.trainRate(), spec.trainRate()));
        float ne = (float) (elev + Mth.clamp(te - elev, -spec.elevRate(), spec.elevRate()));
        if (ny == yaw && ne == elev) { trainStop = false; return; }
        if (barrelsClear(sl, ny, ne)) { yaw = ny; elev = ne; trainStop = false; moving = true; return; }
        if (ne != elev && barrelsClear(sl, yaw, ne)) { elev = ne; moving = true; }
        else if (ny != yaw && barrelsClear(sl, ny, elev)) { yaw = ny; moving = true; }
        trainStop = true;
    }

    private Vec3[] frame() {
        Vec3 o = plotPoint(0.0, 0.0, 0.0);
        return new Vec3[] { o, plotPoint(1.0, 0.0, 0.0).subtract(o), plotPoint(0.0, 1.0, 0.0).subtract(o), plotPoint(0.0, 0.0, 1.0).subtract(o) };
    }

    private Vec3 local(double lx, double ly, double lz) { return plotPoint(lx, ly, lz); }

    private static double[] dirLocal(double yawDeg, double elevDeg) {
        double t = Math.toRadians(elevDeg), p = Math.toRadians(yawDeg);
        return new double[] { -Math.cos(t) * Math.sin(p), Math.sin(t), -Math.cos(t) * Math.cos(p) };
    }

    private double[] barrelPoint(int k, double yawDeg, double elevDeg, double s) {
        double p = Math.toRadians(yawDeg), bx = spec.barrelX()[k] / 16.0, tz = spec.trunZ() / 16.0;
        double tx = bx * Math.cos(p) + tz * Math.sin(p), tzz = -bx * Math.sin(p) + tz * Math.cos(p);
        double[] d = dirLocal(yawDeg, elevDeg);
        return new double[] { tx + d[0] * s, spec.trunY() / 16.0 + d[1] * s, tzz + d[2] * s };
    }

    private double muzzleDist() { return (spec.trunZ() - spec.muzzleZ()) / 16.0; }

    private boolean ownLocal(double lx, double ly, double lz) {
        double h = spec.half() + 0.5;
        return Math.abs(lx) <= h && Math.abs(lz) <= h && ly >= 0.0 && ly < spec.height();
    }

    private boolean barrelsClear(ServerLevel sl, double yawDeg, double elevDeg) {
        double r0 = spec.gunhouseR() / 16.0, len = muzzleDist();
        for (int k = 0; k < spec.barrels(); k++) {
            for (double s = Math.max(0.5, r0 - 0.6); s <= len + 0.01; s += 0.5) {
                double[] q = barrelPoint(k, yawDeg, elevDeg, s);
                if (ownLocal(q[0], q[1], q[2])) continue;
                BlockPos bp = BlockPos.containing(local(q[0], q[1], q[2]));
                if (!sl.isLoaded(bp)) continue;
                BlockState st = sl.getBlockState(bp);
                if (!st.isAir() && st.blocksMotion()) return false;
            }
        }
        return true;
    }

    private boolean lineHits(ServerLevel sl, int k, double yawDeg, double elevDeg) {
        double[] m = barrelPoint(k, yawDeg, elevDeg, muzzleDist());
        double[] d = dirLocal(yawDeg, elevDeg);
        Vec3 mp = local(m[0], m[1], m[2]);
        Vec3 dp = local(m[0] + d[0], m[1] + d[1], m[2] + d[2]).subtract(mp);
        int[] box = SableBridge.plotBox(sl, ship);
        Vec3[] f = frame();
        return GunTurretBlockEntity.blockOnLine(sl, mp, dp, box, p -> {
            Vec3 c = Vec3.atCenterOf(p).subtract(f[0]);
            return ownLocal(c.dot(f[1]), c.dot(f[2]), c.dot(f[3]));
        });
    }

    private double[] anglesTo(Vec3 plot) {
        Vec3[] f = frame();
        Vec3 d = plot.subtract(f[0]);
        double lx = d.dot(f[1]), ly = d.dot(f[2]) - spec.trunY() / 16.0, lz = d.dot(f[3]);
        if (lx * lx + ly * ly + lz * lz < 1.0E-6) return null;
        return new double[] { Math.toDegrees(Math.atan2(-lx, -lz)), Math.toDegrees(Math.atan2(ly, Math.hypot(lx, lz))) };
    }

    @Override
    public Vec3 lensPlot(double yawDeg, double elevDeg, double deploy01) {
        double p = Math.toRadians(yawDeg), x = spec.lensX() / 16.0, z = spec.lensZ() / 16.0;
        return local(x * Math.cos(p) + z * Math.sin(p), spec.lensY() / 16.0, -x * Math.sin(p) + z * Math.cos(p));
    }

    private boolean startSalvo(ServerLevel sl, long now, boolean eye) {
        if (!AirGate.on()) return false;
        boolean h = spec.xl() && !eye && heavyMode && countKind(true) > 0;
        if (h && charges <= 0) return false;
        if (!spec.xl() && loaded < 0) return false;
        if (spec.xl() && countKind(h) <= 0) return false;
        salvoHeavy = h; salvoEye = eye; salvoK = 0; salvoN = 0; salvoNext = now;
        fireBarrel(sl, now);
        return salvoN > 0;
    }

    private void endSalvo(long now) {
        if (salvoN > 0) {
            lastShot = now;
            nextShotAt = now + (salvoHeavy ? spec.heavyReload() : spec.reload());
            firingUntil = now + 6;
            if (spec.xl()) { cycleAt = now; if (level != null) playOnce(level, WmdSounds.XL_RAM.get(), Balance.SND_MECH, 1.0F); }
        }
        salvoK = -1;
        sync();
    }

    private void fireBarrel(ServerLevel sl, long now) {
        int k = salvoK;
        if (k < 0) return;
        if (k >= spec.barrels() || ship == null) { endSalvo(now); return; }
        Vec3 target;
        double[] sol;
        if (salvoEye) {
            if (aimPoint == null) { endSalvo(now); return; }
            target = aimPoint;
            sol = eyeSolution(sl, aimPoint);
        } else {
            sol = consoleSolution(sl, now);
            target = consoleAim;
        }
        if (sol == null || target == null || lineHits(sl, k, yaw, elev)) { if (sol != null) lineBlocked = true; endSalvo(now); return; }
        double[] m = barrelPoint(k, yaw, elev, muzzleDist());
        Vec3 mw = SableBridge.toWorld(sl, local(m[0], m[1], m[2]));
        if (mw == null) { endSalvo(now); return; }
        double dist = mw.distanceTo(target);
        if (salvoEye) {
            if (dist < spec.eyeMin()) { endSalvo(now); return; }
            if (dist > spec.eyeRange()) { target = mw.add(target.subtract(mw).normalize().scale(spec.eyeRange())); dist = spec.eyeRange(); }
        }

        int round;
        int need = salvoHeavy ? (int) sol[4] : 0;
        if (salvoHeavy && charges < need) { endSalvo(now); return; }
        if (!ShipPower.draw(sl, ship, Math.max(1, spec.feShot() / spec.barrels()))) { noPower = true; endSalvo(now); return; }
        double spread;
        boolean guidedOk;
        double hx = Math.hypot(target.x - mw.x, target.z - mw.z);
        if (salvoEye) spread = Balance.ART_MIN_SPREAD;
        else {
            boolean spotted = Artillery.spotted(sl.getServer(), ArtilleryManager.get(sl), getOwner(), target.x, target.z, now);
            spread = Artillery.spread(hx, spotted);
            if (salvoHeavy) spread = SuperGun.spread(spread);
        }
        int peek = spec.xl() ? nextOfKind(salvoHeavy) : loaded;
        guidedOk = peek == Artillery.GUIDED && Mlrs.navGuided(sl.getServer(), getOwner());
        if (guidedOk) spread = Math.min(spread, Balance.GUIDED_SHELL_SPREAD);
        double a = sl.random.nextDouble() * Math.PI * 2.0, r = spread * sl.random.nextDouble();
        double ix = target.x + Math.cos(a) * r, iz = target.z + Math.sin(a) * r;
        if (OfflineGuard.refuse(sl, ix, iz) != null) { endSalvo(now); return; }
        int dur = salvoEye ? Math.max(2, (int) Math.ceil(dist / spec.eyeSpeed())) : (int) sol[2];
        BlockPos gp = BlockPos.containing(mw);

        if (ArtilleryManager.get(sl).shells().size() >= Config.Server.artMaxShells()) { endSalvo(now); return; }
        if (spec.xl()) { round = takeKind(salvoHeavy); if (round < 0) { endSalvo(now); return; } }
        else { round = loaded; if (round < 0) { endSalvo(now); return; } loaded = -1; }
        if (salvoHeavy) charges -= need;
        int gun = round == SUPER ? Artillery.SUPER_GUN : Artillery.HOWITZER, type = round == SUPER ? Artillery.HE : round;
        boolean last = rounds() == 0;
        ArtShell s = ArtilleryManager.get(sl).fire(sl, getOwner(), getOwnerName(), gun, type, mw, ix, target.y, iz, dur, gp, ++seq, last);
        if (s == null) {
            if (spec.xl()) { if (rackN < rack.length) { System.arraycopy(rack, 0, rack, 1, rackN); rack[0] = round; rackN++; } }
            else loaded = round;
            if (salvoHeavy) charges += need;
            endSalvo(now);
            return;
        }
        if (round == SUPER) SuperGunServer.onFiredBy(sl, gp, getOwner(), getOwnerName());
        salvoN++;
        double[] d = dirLocal(yaw, elev);
        Vec3 dw = SableBridge.toWorld(sl, local(m[0] + d[0], m[1] + d[1], m[2] + d[2]));
        Vec3 aimW = dw == null ? target : mw.add(dw.subtract(mw).scale(8.0));
        PacketDistributor.sendToPlayersNear(sl, null, mw.x, mw.y, mw.z, round == SUPER ? Balance.SUPER_GUN_HEARD : 600,
                new GunnerPayloads.Fire(worldPosition, mw.x, mw.y, mw.z, aimW.x, aimW.y, aimW.z, (round == SUPER ? HEAVY_KIND : SHOT_KIND) + k));
        salvoK++;
        salvoNext = now + Balance.XGUN_RIPPLE_TICKS;
        if (salvoK >= spec.barrels() || countKind(salvoHeavy) <= 0 && spec.xl() || !spec.xl()) endSalvo(now);
    }

    private int nextOfKind(boolean heavyRound) {
        for (int i = 0; i < rackN; i++) if ((rack[i] == SUPER) == heavyRound) return rack[i];
        return -1;
    }

    private void syncAim(ServerLevel sl, long now) {
        int flags = (firingNow() ? 1 : 0) | (blockedNow() ? 2 : 0) | (rounds() <= 0 ? 4 : 0) | (moving ? 8 : 0);
        boolean changed = Math.abs(yaw - syncedYaw) > 0.3F || Math.abs(elev - syncedElev) > 0.3F || Math.abs(deploy - syncedDeploy) > 0.05F || flags != syncedFlags
                || (deploy == 0.0F) != (syncedDeploy == 0.0F) || (deploy == 1.0F) != (syncedDeploy == 1.0F);
        if (!changed || now - lastSyncAt < Balance.GTUR_SYNC_TICKS) return;
        Vec3 w = SableBridge.toWorld(sl, Vec3.atCenterOf(worldPosition));
        if (w == null) return;
        syncedYaw = yaw; syncedElev = elev; syncedDeploy = deploy; syncedFlags = flags; lastSyncAt = now;
        PacketDistributor.sendToPlayersNear(sl, null, w.x, w.y, w.z, spec.xl() ? 320.0 : 256.0, new GunnerPayloads.Aim(worldPosition, yaw, elev, deploy, flags));
    }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = GUNS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) leave(sl);
        super.setRemoved();
    }

    @Override public void clientAim(float y, float e, float d, int flags) { yawT = y; elevT = e; deployT = d; clientMoving = (flags & 8) != 0; }
    @Override public float[] shownPose() { return new float[] { yawShown, elevShown, deployShown }; }

    void clientKick(int k) {
        if (k < 0 || k >= kick.length) return;
        kick[k] = 1.0F;
        if (level != null) { flashUntil[k] = level.getGameTime() + 2; flapUntil = level.getGameTime() + 12; }
    }

    @Override protected boolean soundAlive() { return isRunning() && (clientMoving || Math.abs(yawT - yawShown) > 0.6F || Math.abs(elevT - elevShown) > 0.6F); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (Double.isNaN(lastT)) { yawShown = yawT; elevShown = elevT; deployShown = deployT; }
        float dyaw = 0.0F;
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT);
            float y0 = yawShown;
            yawShown += (float) Mth.clamp(yawT - yawShown, -spec.trainRate() * dt, spec.trainRate() * dt);
            elevShown += (float) Mth.clamp(elevT - elevShown, -spec.elevRate() * dt, spec.elevRate() * dt);
            deployShown += (float) Mth.clamp(deployT - deployShown, -0.1 * dt, 0.1 * dt);
            for (int i = 0; i < kick.length; i++) kick[i] = Math.max(0.0F, kick[i] - (float) (dt / 14.0));
            dyaw = yawShown - y0;
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        spin += dyaw * 12.0F;
        long now = level == null ? 0L : level.getGameTime();
        double ct = cycleAt >= 0 ? (now - cycleAt) + pt : -1.0;
        float u = ct < 0 ? 0.0F : (float) Mth.clamp(ct / spec.hoist(), 0.0, 1.0);
        float drumStep = u < 0.25F ? u / 0.25F : 1.0F;
        float hoist = u < 0.25F ? 0.0F : (float) Math.sin(Math.PI * Math.min(1.0F, (u - 0.25F) / 0.75F));
        final float yr = (float) Math.toRadians(yawShown) * YAW_SIGN, er = (float) Math.toRadians(elevShown) * ELEV_SIGN;
        final float cap = (float) Math.toRadians(100.0 * Mth.clamp(deployShown, 0.0F, 1.0F)) * CAP_SIGN;
        model.getBone("ring").ifPresent(b -> b.setRotY(yr));
        final float sp = (float) Math.toRadians(spin);
        model.getBone("pinion").ifPresent(b -> b.setRotY(sp));
        final boolean flapOpen = now < flapUntil;
        if (!spec.xl()) {
            model.getBone("cradle").ifPresent(b -> b.setRotX(er));
            final float bz = (float) (spec.recoil() * Math.min(1.0F, kick[0] * 1.5F)) * RECOIL_SIGN;
            model.getBone("barrel").ifPresent(b -> b.setPosZ(bz));
            model.getBone("muzzlecap").ifPresent(b -> b.setRotX(cap));
            final boolean fl = now < flashUntil[0];
            model.getBone("flash").ifPresent(b -> b.setHidden(!fl));
            model.getBone("caseflap").ifPresent(b -> b.setRotX((float) Math.toRadians(flapOpen ? -60.0 : 0.0) * FLAP_SIGN));
            final float dr = (float) Math.toRadians(-30.0 * ((steps % 12) + (ct >= 0 ? drumStep : 0.0F))) * DRUM_SIGN;
            model.getBone("drum").ifPresent(b -> b.setRotY(dr));
            final float hy = (float) (Math.sin(DECK_RAIL_A) * DECK_HOIST_L * hoist) * HOIST_SIGN, hz = (float) (-Math.cos(DECK_RAIL_A) * DECK_HOIST_L * hoist) * HOIST_SIGN;
            model.getBone("hoist").ifPresent(b -> { b.setPosY(hy); b.setPosZ(hz); });
            final boolean shellUp = ct >= 0 && u >= 0.25F && u < 0.75F;
            model.getBone("hshell").ifPresent(b -> b.setHidden(!shellUp));
            for (int i = 0; i < 12; i++) {
                final boolean show = i < rackN;
                model.getBone("rd" + i).ifPresent(b -> b.setHidden(!show));
            }
        } else {
            for (int k = 0; k < 3; k++) {
                final int kk = k;
                model.getBone("cradle" + k).ifPresent(b -> b.setRotX(er));
                final float bz = (float) (spec.recoil() * Math.min(1.0F, kick[kk] * 1.5F)) * RECOIL_SIGN;
                model.getBone("barrel" + k).ifPresent(b -> b.setPosZ(bz));
                model.getBone("cap" + k).ifPresent(b -> b.setRotX(cap));
                final boolean fl = now < flashUntil[kk];
                model.getBone("flash" + k).ifPresent(b -> b.setHidden(!fl));
                final float hy = (float) (XL_HOIST_L * hoist) * HOIST_SIGN;
                model.getBone("hoist" + k).ifPresent(b -> b.setPosY(hy));
                final boolean shellUp = ct >= 0 && u >= 0.25F && u < 0.75F;
                model.getBone("hs" + k).ifPresent(b -> b.setHidden(!shellUp));
            }
            final float dr = (float) Math.toRadians(-20.0 * (ct >= 0 ? drumStep : 0.0F)) * DRUM_SIGN;
            model.getBone("drum").ifPresent(b -> b.setRotY(dr));
            for (int i = 0; i < 18; i++) { final boolean show = i < rackN; model.getBone("sh" + i).ifPresent(b -> b.setHidden(!show)); }
            for (int i = 0; i < 12; i++) { final boolean show = i < charges; model.getBone("cb" + i).ifPresent(b -> b.setHidden(!show)); }
        }
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return spec.xl() ? WmdSounds.XL_DRIVE_LOOP.get() : WmdSounds.DG_DRIVE_LOOP.get(); }
    @Override protected float soundVolume() { return spec.xl() ? Balance.SND_GIANT : Balance.SND_HEAVY; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, spec.height() - 2, 0); }

    @Override
    protected String detail() {
        return rackN + " in the store" + (loaded >= 0 ? ", 1 in the breech" : "") + (spec.xl() ? ", " + charges + " charges" + (heavyMode ? ", HEAVY" : "") : "")
                + ", deploy " + Math.round(deploy * 100) + "%" + (ship == null ? ", not on a craft" : "");
    }

    @Override
    public Component status() {
        int nr = nextRound(false);
        Component next = nr < 0 ? Component.translatable("message.wmd.naval_gun.none") : roundItem(nr).getHoverName();
        net.minecraft.network.chat.MutableComponent c = Component.translatable("block.wmd." + spec.id()).append(": ");
        if (spec.xl()) return c.append(Component.translatable("message.wmd.naval_gun.status_xl", rackN, spec.rack(), charges, spec.charges(), next));
        return c.append(Component.translatable("message.wmd.naval_gun.status", rounds(), roundsMax(), next));
    }

    private void writeAmmo(CompoundTag tag) {
        tag.putIntArray("ngRack", java.util.Arrays.copyOf(rack, rackN));
        tag.putInt("ngLoaded", loaded);
        tag.putInt("ngCharges", charges);
        tag.putBoolean("ngHeavy", heavyMode);
        tag.putInt("ngSteps", steps);
    }

    private static boolean validCode(int v) { return v == SUPER || v >= 0 && Artillery.canFire(Artillery.HOWITZER, v) && v != Artillery.CANISTER; }

    private void readAmmo(CompoundTag tag) {
        rackN = 0;
        for (int v : tag.getIntArray("ngRack")) if (rackN < rack.length && validCode(v) && (spec.xl() || v != SUPER)) rack[rackN++] = v;
        int l = tag.contains("ngLoaded") ? tag.getInt("ngLoaded") : -1;
        loaded = !spec.xl() && l >= 0 && l != SUPER && validCode(l) ? l : -1;
        charges = spec.xl() ? Mth.clamp(tag.getInt("ngCharges"), 0, spec.charges()) : 0;
        heavyMode = spec.xl() && tag.getBoolean("ngHeavy");
        steps = tag.getInt("ngSteps");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        writeAmmo(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        readAmmo(tag);
        if (tag.contains("ngCycle")) cycleAt = tag.getLong("ngCycle");
        if (tag.contains("ngDeploy")) { deployT = tag.getFloat("ngDeploy"); yawT = tag.getFloat("ngYaw"); elevT = tag.getFloat("ngElev"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        writeAmmo(tag);
        tag.putLong("ngCycle", cycleAt);
        tag.putFloat("ngDeploy", deploy);
        tag.putFloat("ngYaw", yaw);
        tag.putFloat("ngElev", elev);
        return tag;
    }
}

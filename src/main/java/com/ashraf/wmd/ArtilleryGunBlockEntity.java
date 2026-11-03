package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class ArtilleryGunBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    public static final int S_READY = 0, S_NO_TARGET = 1, S_TOO_FAR = 2, S_TOO_CLOSE = 3, S_NO_SHELLS = 4, S_NO_CHARGES = 5, S_NO_POWER = 6, S_LAYING = 7,
            S_RELOADING = 8, S_FIRING = 9, S_SKY_FULL = 10, S_PROTECTED = 11, S_FIRED = 99;

    public static final int A_TARGET = 0, A_DESIGNATED = 1, A_PICK = 2, A_CHARGE = 3, A_FIRE = 4, A_CEASE = 5;

    private final OwnerData ownerData = new OwnerData();

    protected final Slot[] shellSlots = new Slot[Artillery.TYPES.length];

    private boolean hasTarget;
    private int tx, tz;
    private int pick;
    private int charge;
    private int mission;
    private int seq;
    private int layLeft, cooldown;
    private long lastFire = -1000L;
    private int status = S_NO_TARGET;
    private boolean spotted;
    private int spreadQ;
    private int aimAz, aimEl;
    private int usedCharge;
    private int dist;

    float shownAz, shownEl = Float.NaN, prevAz, prevEl;

    protected ArtilleryGunBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fePerTick) {
        super(type, pos, state, fePerTick);
        for (int t = 0; t < shellSlots.length; t++) {
            final int k = t;
            shellSlots[t] = new Slot(magazine(), false, s -> s.is(Artillery.shellItem(gun(), k)));
        }
    }

    public abstract int gun();

    protected abstract int magazine();

    protected abstract int layTicks();

    protected abstract double[] pivotLocal();

    protected double[] worldPointW(double lx, double ly, double lz) {
        double[] p = worldPoint(lx, ly, lz);
        Vec3 w = SableBridge.toWorld(level, new Vec3(p[0], p[1], p[2]));
        return new double[] { w.x, w.y, w.z };
    }

    protected double floorYW() { return Math.floor(SableBridge.worldPos(level, worldPosition).y); }

    protected abstract double trunnionHeight();

    protected abstract double barrelLength();

    protected abstract float restElevation();

    protected Slot chargeSlot() { return null; }

    protected double[] solveOwn(int tx, int tz, int pick, int charge) { return null; }
    protected int flightTicksOwn(int ticks, double dist, double[] s) { return ticks; }
    protected int reloadOwn(int ticks) { return ticks; }
    protected double spreadOwn(double spread) { return spread; }
    protected boolean logMissionOwn(int rounds, String by) { return false; }

    void testReady() { layLeft = 0; cooldown = 0; refresh(); setChanged(); sync(); }

    public int range() { return Artillery.range(gun()); }

    public int minRange() { return Artillery.minRange(gun()); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    public boolean hasTarget() { return hasTarget; }
    public int targetX() { return tx; }
    public int targetZ() { return tz; }
    public int picked() { return pick; }
    public int charge() { return charge; }
    public int mission() { return mission; }
    public int gunStatus() { return status; }
    public boolean spotted() { return spotted; }
    public double spreadShown() { return spreadQ / 10.0; }
    public int usedCharge() { return usedCharge; }
    public int distance() { return dist; }
    public long lastFire() { return lastFire; }
    public float aimElevation() { return aimEl < 0 ? Float.NaN : aimEl / 10.0F; }

    public int ammo(int t) { int ownAmmo = ammoHook(Artillery.clampType(t)); if (ownAmmo >= 0) return ownAmmo; return shellSlots[Artillery.clampType(t)].count(); }

    protected Slot rackSlot(int k) { return null; }

    public int validPick(int p) {
        if (p >= Artillery.PICK_RACK && p < Artillery.PICK_RACK + Balance.RACK_SLOTS && rackSlot(p - Artillery.PICK_RACK) != null) return p;
        return Artillery.clampType(p);
    }

    protected Slot slotFor(int p) {
        if (p >= Artillery.PICK_RACK) { Slot r = rackSlot(p - Artillery.PICK_RACK); if (r != null) return r; }
        return shellSlots[Artillery.clampType(p)];
    }

    public int firedType(int p) {
        int own = shellTypeHook(p);
        if (own >= 0) return own;
        if (p >= Artillery.PICK_RACK) {
            Slot r = rackSlot(p - Artillery.PICK_RACK);
            return r != null && r.stack.getItem() instanceof ArtShellItem a ? a.type() : -1;
        }
        return Artillery.clampType(p);
    }

    protected int shellTypeHook(int p) { return -1; }

    public int roundsOf(int p) { return slotFor(validPick(p)).count(); }

    public void act(int action, int a, int b, Player by) {
        switch (action) {
            case A_TARGET -> setTarget(a, b);
            case A_DESIGNATED -> {
                CcTarget t = CcTarget.of(getOwner());
                if (t == null) { if (by != null) by.displayClientMessage(Component.translatable("message.wmd.art.no_designated"), true); return; }
                setTarget(t.x(), t.z());
            }
            case A_PICK -> { int np = validPick(a); if (np != pick) { pick = np; mission = 0; refresh(); } }
            case A_CHARGE -> { charge = Mth.clamp(a, 0, Balance.HOWITZER_CHARGES); refresh(); }
            case A_FIRE -> startMission(a < 0 ? -1 : Math.max(1, Math.min(64, a)), by == null ? "" : by.getGameProfile().getName());
            case A_CEASE -> { mission = 0; refresh(); }
            default -> { }
        }
        setChanged();
        sync();
    }

    public int order(int x, int z, int type, int rounds, String by) {
        if (!hasTarget || x != tx || z != tz) setTarget(x, z);
        if (validPick(type) != pick) pick = validPick(type);
        startMission(rounds < 0 ? -1 : Math.max(1, Math.min(64, rounds)), by);
        setChanged();
        sync();
        return mission != 0 ? (layLeft > 0 ? S_LAYING : cooldown > 0 ? S_RELOADING : S_FIRING) : status;
    }

    void testLoad(int t) {
        Slot s = shellSlots[Artillery.clampType(t)];
        s.stack = new ItemStack(Artillery.shellItem(gun(), Artillery.clampType(t)), s.max);
        s.sync();
        Slot cs = chargeSlot();
        if (cs != null) { cs.stack = new ItemStack(wmd("propellant_charge"), cs.max); cs.sync(); }
        setChanged();
    }

    void setTarget(int x, int z) {
        int nx = Mth.clamp(x, -29_999_000, 29_999_000), nz = Mth.clamp(z, -29_999_000, 29_999_000);
        if (hasTarget && nx == tx && nz == tz) return;
        hasTarget = true;
        tx = nx;
        tz = nz;
        mission = 0;
        layLeft = layTicks();
        refresh();
    }

    private void startMission(int rounds, String by) {
        refresh();
        if (!hasTarget && !onDot) { status = S_NO_TARGET; return; }
        int why = check();
        if (why != S_READY) { status = why; mission = 0; return; }
        mission = rounds;
        seq = 0;
        status = layLeft > 0 ? S_LAYING : cooldown > 0 ? S_RELOADING : S_FIRING;
        if (logMissionOwn(rounds, by)) return;
        if (level instanceof ServerLevel sl && getOwner() != null)
            CcBattleLog.add(sl.getServer(), getOwner(), CcBattleLog.LAUNCH_OUT, "log.wmd.art_fire", "#block.wmd." + Artillery.GUNS[gun()],
                    "#item.wmd." + Artillery.shellId(gun(), Math.max(0, firedType(pick))), rounds < 0 ? "#gui.wmd.art.all" : Integer.toString(rounds),
                    Integer.toString(onDot ? dotX : tx), Integer.toString(onDot ? dotZ : tz));
    }

    private double[] solve() {
        LaserSpots.Spot sp = laserSpot();
        if (sp != null) return solveSpot(sp.pos());
        if (!hasTarget) return null;
        return solveFor(tx, tz, pick, charge);
    }

    private LaserSpots.Spot laserSpot() {
        if (!(level instanceof ServerLevel sl) || getOwner() == null || firedType(pick) != Artillery.LASER) return null;
        if (hasTarget) {
            LaserSpots.Spot near = LaserSpots.nearest(sl, getOwner(), tx + 0.5, tz + 0.5, Balance.LASER_SHELL_RANGE);
            if (near != null) return near;
        }
        double[] pl = pivotLocal();
        double[] p = worldPointW(pl[0], pl[1], pl[2]);
        return LaserSpots.nearest(sl, getOwner(), p[0], p[2], range());
    }

    private double[] solveSpot(Vec3 sp) {
        double[] pl = pivotLocal();
        double[] p = worldPointW(pl[0], pl[1], pl[2]);
        double dx = sp.x - p[0], dz = sp.z - p[2], d = Math.sqrt(dx * dx + dz * dz), h0 = p[1] + trunnionHeight(), y = sp.y - h0;
        int ch = 0;
        double el;
        if (gun() == Artillery.MORTAR) el = Artillery.elevation(Artillery.velocity(Artillery.MORTAR, 0), d, y, true);
        else {
            ch = charge > 0 ? charge : Artillery.autoCharge(d, y);
            if (ch <= 0) el = Double.NaN;
            else {
                double v = Artillery.velocity(Artillery.HOWITZER, ch), low = Artillery.elevation(v, d, y, false);
                boolean high = d < Balance.LASER_HIGH_ARC || lowBlocked(p[0], h0, p[2], sp, v, low);
                el = high ? Artillery.elevation(v, d, y, true) : low;
                if (Double.isNaN(el)) el = low;
            }
        }
        int ticks = Double.isNaN(el) ? 0 : Artillery.flightTicks(Artillery.velocity(gun(), ch), d, el);
        return new double[] { d, ch, el, ticks, sp.y, dx, dz, 1 };
    }

    private boolean lowBlocked(double x0, double h0, double z0, Vec3 sp, double v, double el) {
        if (Double.isNaN(el) || level == null) return false;
        double dx = sp.x - x0, dz = sp.z - z0, d = Math.sqrt(dx * dx + dz * dz), t = Math.toRadians(el), c = Math.cos(t), g = Balance.ART_GRAVITY;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (double s = 6.0; s < d - Balance.LASER_CLEAR_END; s += 1.5) {
            double y = h0 + s * Math.tan(t) - g * s * s / (2.0 * v * v * c * c);
            q.set(Math.floor(x0 + dx / d * s), Math.floor(y), Math.floor(z0 + dz / d * s));
            if (!level.hasChunkAt(q)) continue;
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return true;
        }
        return false;
    }

    private double laidX = Double.NaN, laidY, laidZ;
    private boolean onDot;
    private int dotX, dotY, dotZ;

    public boolean onDot() { return onDot; }
    public int dotX() { return dotX; }
    public int dotY() { return dotY; }
    public int dotZ() { return dotZ; }

    private boolean relayForSpot() {
        LaserSpots.Spot sp = laserSpot();
        if (sp == null) return false;
        Vec3 v = sp.pos();
        boolean moved = Double.isNaN(laidX) || Math.abs(v.x - laidX) + Math.abs(v.y - laidY) + Math.abs(v.z - laidZ) > Balance.LASER_RELAY_DIST;
        if (!moved) return false;
        laidX = v.x; laidY = v.y; laidZ = v.z;
        layLeft = Balance.LASER_RELAY_TICKS;
        return true;
    }

    private double[] solveFor(int tx, int tz, int pick, int charge) {
        double[] ownSolve = solveHook(tx, tz, pick, charge);
        if (ownSolve != null) return ownSolve;
        if (level == null) return null;
        double[] own = solveOwn(tx, tz, pick, charge);
        if (own != null) return own;
        double[] pl = pivotLocal();
        double[] p = worldPointW(pl[0], pl[1], pl[2]);
        double dx = tx + 0.5 - p[0], dz = tz + 0.5 - p[2];
        double d = Math.sqrt(dx * dx + dz * dz);
        BlockPos probe = new BlockPos(tx, level.getMinBuildHeight(), tz);
        double groundY = level.hasChunkAt(probe) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) : floorYW();
        int type = firedType(pick);
        double endY = groundY + (pick == Artillery.ILLUM ? Artillery.ILLUM_BURST : pick == Artillery.CLUSTER ? Artillery.CLUSTER_BURST : Artillery.specialBurst(type));
        double y = endY - (p[1] + trunnionHeight());
        if (type == Artillery.CANISTER) {
            return new double[] { d, 1, Math.toDegrees(Math.atan2(y + 1.0, Math.max(1.0, d))), 1, groundY + 1.0, dx, dz };
        }
        double mul = type == Artillery.ROCKET_ASSIST ? Balance.ROCKET_RANGE_MUL : 1.0;
        int ch = 0;
        double el;
        if (gun() == Artillery.MORTAR) el = Artillery.elevation(Artillery.velocity(Artillery.MORTAR, 0), d, y, true);
        else {
            ch = charge > 0 ? charge : Artillery.autoCharge(d / mul, y / mul);
            el = ch > 0 ? Artillery.elevation(Artillery.velocity(Artillery.HOWITZER, ch), d / mul, y / mul, false) : Double.NaN;
        }
        int ticks = Double.isNaN(el) ? 0 : Artillery.flightTicks(Artillery.velocity(gun(), ch) * Math.sqrt(mul), d, el);
        return new double[] { d, ch, el, ticks, endY, dx, dz };
    }

    public int checkFor(int x, int z, int type) {
        int ownCheck = checkHook(x, z, Artillery.clampType(type));
        if (ownCheck != NO_HOOK) return ownCheck;
        if (!hasPower()) return S_NO_POWER;
        int t = validPick(type);
        double[] s = solveFor(x, z, t, charge);
        if (s == null) return S_NO_TARGET;
        int why = rangeCheck(s, firedType(t));
        if (why != S_READY) return why;
        if (slotFor(t).count() < 1) return S_NO_SHELLS;
        Slot cs = chargeSlot();
        if (cs != null && cs.count() < Math.max(1, (int) s[1])) return S_NO_CHARGES;
        return S_READY;
    }

    public int flightTicksFor(int x, int z, int type) {
        double[] s = solveFor(x, z, Artillery.clampType(type), charge);
        return s == null || Double.isNaN(s[2]) ? -1 : (int) s[3];
    }

    public void remoteLay(int x, int z, int type) {
        mission = 0;
        pick = Artillery.clampType(type);
        setTarget(x, z);
        refresh();
        setChanged();
        sync();
    }

    public int busyTicks() { return Math.max(layLeft, cooldown); }

    private int check() {
        int ownCheck = hasTarget ? checkHook(tx, tz, pick) : NO_HOOK;
        if (ownCheck != NO_HOOK) return ownCheck;
        if (!hasTarget && laserSpot() == null) return S_NO_TARGET;
        if (!hasPower()) return S_NO_POWER;
        double[] s = solve();
        if (s == null) return S_NO_TARGET;
        if (slotFor(pick).count() < 1) return S_NO_SHELLS;
        int why = rangeCheck(s, firedType(pick));
        if (why != S_READY) return why;
        Slot cs = chargeSlot();
        if (cs != null && cs.count() < Math.max(1, (int) s[1])) return S_NO_CHARGES;
        return S_READY;
    }

    private int rangeCheck(double[] s, int type) {
        if (type == Artillery.CANISTER) {
            if (s[0] < Balance.CANISTER_MIN) return S_TOO_CLOSE;
            return s[0] > Balance.CANISTER_MAX ? S_TOO_FAR : S_READY;
        }
        boolean dot = s.length > 7 && s[7] > 0.0;
        if (s[0] < (dot ? Balance.LASER_SHELL_MIN : type == Artillery.FLAK ? Balance.FLAK_MIN : minRange())) return S_TOO_CLOSE;
        double max = range() * (type == Artillery.ROCKET_ASSIST ? Balance.ROCKET_RANGE_MUL : 1.0);
        return s[0] > max + 0.5 || Double.isNaN(s[2]) ? S_TOO_FAR : S_READY;
    }

    private double typeSpread(double spread, int type) { return type == Artillery.ROCKET_ASSIST ? spread * Balance.ROCKET_SPREAD_MUL : spread; }

    private void refresh() {
        if (level == null || level.isClientSide) return;
        double[] s = solve();
        if (s != null) {
            dist = (int) Math.round(s[0]);
            usedCharge = (int) s[1];
            aimAz = (int) Math.round(LauncherGeometry.wrap(LauncherGeometry.azimuthFor(facing(), s[5], s[6]) + 180.0) * 10.0);
            aimEl = Double.isNaN(s[2]) ? -1 : (int) Math.round(s[2] * 10.0);
            spotted = level instanceof ServerLevel sl && Artillery.spotted(sl.getServer(), ArtilleryManager.get(sl), getOwner(), tx + 0.5, tz + 0.5, level.getGameTime());
            spreadQ = (int) Math.round(Artillery.spread(s[0], spotted) * 10.0);
            spreadQ = (int) Math.round(guidedSpread(spreadQ / 10.0) * 10.0);
            spreadQ = (int) Math.round(spreadOwn(spreadQ / 10.0) * 10.0);
            spreadQ = (int) Math.round(typeSpread(spreadQ / 10.0, firedType(pick)) * 10.0);
            if (s.length > 7 && s[7] > 0.0) spreadQ = 0;
            onDot = s.length > 7 && s[7] > 0.0;
            LaserSpots.Spot sp = onDot ? laserSpot() : null;
            if (sp != null) { dotX = Mth.floor(sp.pos().x); dotY = Mth.floor(sp.pos().y); dotZ = Mth.floor(sp.pos().z); }
        } else { dist = 0; aimEl = -1; usedCharge = 0; spreadQ = 0; spotted = false; onDot = false; }
        int c = check();
        status = mission != 0 ? (layLeft > 0 ? S_LAYING : cooldown > 0 ? S_RELOADING : S_FIRING)
                : c != S_READY ? c : layLeft > 0 ? S_LAYING : cooldown > 0 ? S_RELOADING : S_READY;
    }

    private int fireOne() {
        int hook = fireHook();
        if (hook != NO_HOOK) { if (hook == S_FIRED) { seq++; lastFire = level.getGameTime(); setChanged(); } return hook; }
        int why = check();
        if (why != S_READY) return why;
        double[] s = solve();
        if (!(level instanceof ServerLevel sl) || s == null) return S_NO_TARGET;
        int type = firedType(pick);
        boolean spot = Artillery.spotted(sl.getServer(), ArtilleryManager.get(sl), getOwner(), tx + 0.5, tz + 0.5, level.getGameTime());
        double spread = Artillery.spread(s[0], spot);
        spread = guidedSpread(spread);
        spread = spreadOwn(spread);
        spread = typeSpread(spread, type);
        double a = level.random.nextDouble() * Math.PI * 2.0, r = spread * level.random.nextDouble();
        double ix = tx + 0.5 + Math.cos(a) * r, iz = tz + 0.5 + Math.sin(a) * r;
        if (type == Artillery.LASER && s.length > 7 && s[7] > 0.0) {
            LaserSpots.Spot sp = laserSpot();
            if (sp != null) { ix = sp.pos().x; iz = sp.pos().z; }
        }
        if (OfflineGuard.refuse(sl, ix, iz) != null) return S_PROTECTED;

        double[] pl = pivotLocal();
        double[] p = worldPointW(pl[0], pl[1], pl[2]);
        double len = Math.max(1.0E-6, Math.hypot(s[5], s[6])), el = Math.toRadians(s[2]);
        double L = barrelLength();
        Vec3 muzzle = new Vec3(p[0] + s[5] / len * L * Math.cos(el), p[1] + trunnionHeight() + L * Math.sin(el), p[2] + s[6] / len * L * Math.cos(el));
        double dImpact = Math.hypot(ix - muzzle.x, iz - muzzle.z);
        Slot from = slotFor(pick);
        if (type == Artillery.CANISTER) {
            SpecialShells.canister(sl, this, muzzle, new Vec3(tx + 0.5, s[4], tz + 0.5));
            from.take(1);
            Slot cs0 = chargeSlot();
            if (cs0 != null) cs0.take(1);
            seq++;
            lastFire = level.getGameTime();
            setChanged();
            return S_FIRED;
        }
        int ticks = Artillery.flightTicks(Artillery.velocity(gun(), (int) s[1]) * Math.sqrt(type == Artillery.ROCKET_ASSIST ? Balance.ROCKET_RANGE_MUL : 1.0), dImpact, s[2]);
        ticks = flightTicksOwn(ticks, dImpact, s);
        boolean last = mission == 1 || (mission < 0 && from.count() == 1);
        String msg = type == Artillery.LEAFLET ? ArtShellItem.leaflet(from.stack) : "";
        ArtShell shell = ArtilleryManager.get(sl).fire(sl, getOwner(), getOwnerName(), gun(), type, muzzle, ix, s[4], iz, ticks, worldPosition, seq + 1, last);
        if (shell == null) return S_SKY_FULL;
        if (!msg.isEmpty()) ArtilleryManager.get(sl).leaflet(shell.id(), msg);
        from.take(1);
        Slot cs = chargeSlot();
        if (cs != null) cs.take(Math.max(1, (int) s[1]));
        seq++;
        lastFire = level.getGameTime();
        setChanged();
        return S_FIRED;
    }

    @Override
    protected boolean process() {
        ArtilleryGuns.seen(this);
        boolean busy = false, changed = false;
        int stopped = -1;
        if (layLeft > 0) { layLeft--; busy = true; if (layLeft == 0) changed = true; }
        if (cooldown > 0) { cooldown--; busy = true; if (cooldown == 0) changed = true; }
        if (mission != 0 && layLeft == 0 && cooldown == 0 && relayForSpot()) { busy = true; changed = true; }
        else if (mission != 0 && layLeft == 0 && cooldown == 0) {
            int r = fireOne();
            if (r == S_FIRED) {
                cooldown = Artillery.reload(gun());
                cooldown = reloadOwn(cooldown);
                if (mission > 0) mission--;
                busy = true;
            } else {
                mission = 0;
                stopped = r;
            }
            changed = true;
        }
        if (changed || ((hasTarget || firedType(pick) == Artillery.LASER) && level != null && level.getGameTime() % 20 == 0)) {
            int old = status, oldEl = aimEl, oldDot = onDot ? dotX * 31 + dotY * 17 + dotZ : Integer.MIN_VALUE;
            boolean oldSpot = spotted;
            refresh();
            if (stopped >= 0) status = stopped;
            int newDot = onDot ? dotX * 31 + dotY * 17 + dotZ : Integer.MIN_VALUE;
            if (changed || old != status || oldEl != aimEl || oldSpot != spotted || oldDot != newDot) { setChanged(); sync(); }
        }
        return busy || mission != 0;
    }

    @Override
    protected void idle() {
        super.idle();
        ArtilleryGuns.seen(this);
        if (level != null && level.getGameTime() % 20 == 0 && status != S_NO_POWER && hasTarget) { status = S_NO_POWER; setChanged(); sync(); }
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void clientTickGun(Level level, BlockPos pos, BlockState state, ArtilleryGunBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        float rest = be.restElevation();
        float tAz = be.aimEl >= 0 ? be.aimAz / 10.0F : be.shownAz;
        float tEl = be.aimEl >= 0 ? be.aimEl / 10.0F : rest;
        if (Float.isNaN(be.shownEl)) { be.shownEl = rest; be.shownAz = 0.0F; }
        be.prevAz = be.shownAz;
        be.prevEl = be.shownEl;
        float slew = be.gun() == Artillery.MORTAR ? 6.0F : 2.5F;
        float dAz = (float) LauncherGeometry.wrap(tAz - be.shownAz);
        be.shownAz += Mth.clamp(dAz, -slew, slew);
        be.shownEl += Mth.clamp(tEl - be.shownEl, -slew * 0.6F, slew * 0.6F);
        if (be.prevAz - be.shownAz > 180.0F) be.prevAz -= 360.0F;
        else if (be.shownAz - be.prevAz > 180.0F) be.prevAz += 360.0F;
    }

    float az(float pt) {
        if (Float.isNaN(shownEl)) return 0.0F;
        float a = prevAz + (shownAz - prevAz) * pt;
        double[] s = ShipFrame.modelAim(level, worldPosition, facing(), a - 180.0, prevEl + (shownEl - prevEl) * pt, pt);
        return s == null ? a : (float) LauncherGeometry.wrap(s[0] + 180.0);
    }

    float el(float pt) {
        if (Float.isNaN(shownEl)) return restElevation();
        float e = prevEl + (shownEl - prevEl) * pt;
        double[] s = ShipFrame.modelAim(level, worldPosition, facing(), prevAz + (shownAz - prevAz) * pt - 180.0, e, pt);
        return s == null ? e : (float) s[1];
    }

    double sinceFire(double tt) { return tt - lastFire; }

    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); for (int i = 0; i < slots().length; i++) l.add(Fluids.EMPTY); return l; }
    @Override protected int inputTanks() { return slots().length; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return -1.0F; }

    @Override
    protected List<String> labelKeys() {
        List<String> l = new ArrayList<>();
        for (String t : Artillery.TYPES) l.add("shell_" + t);
        for (int k = 0; k < Balance.RACK_SLOTS; k++) if (rackSlot(k) != null) l.add("shell_rack");
        if (chargeSlot() != null) l.add("propellant");
        return l;
    }

    @Override
    protected String[] iconIds() {
        List<String> l = new ArrayList<>();
        for (int t = 0; t < Artillery.TYPES.length; t++) l.add(Artillery.shellId(gun(), t));
        for (int k = 0; k < Balance.RACK_SLOTS; k++) if (rackSlot(k) != null) l.add("shell_howitzer_laser");
        if (chargeSlot() != null) l.add("propellant_charge");
        return l.toArray(new String[0]);
    }

    @Override
    public int guiStatus() {
        if (isRunning()) return 1;
        return guiEnergy() != null && !hasPower() ? 2 : 0;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.art_gun", minRange(), range()));
    }

    @Override
    protected String detail() {
        return (hasTarget ? "target " + tx + " " + tz + ", " + dist + " m" : "no target") + ", " + Artillery.typeName(Math.max(0, firedType(pick))) + " x" + roundsOf(pick)
                + (mission != 0 ? ", firing" : "") + ", status " + status;
    }

    @Override protected BlockPos soundLocal() { double[] p = pivotLocal(); return new BlockPos((int) Math.floor(p[0] + 0.5), 0, (int) Math.floor(p[2] + 0.5)); }

    @Override protected SoundEvent runSound() { return WmdSounds.AG_RUN_LOOP.get(); }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putBoolean("artTarget", hasTarget);
        tag.putInt("artX", tx);
        tag.putInt("artZ", tz);
        tag.putInt("artPick", pick);
        tag.putInt("artCharge", charge);
        tag.putInt("artMission", mission);
        tag.putInt("artSeq", seq);
        tag.putInt("artLay", layLeft);
        tag.putInt("artCool", cooldown);
        tag.putLong("artLast", lastFire);
        tag.putInt("artStatus", status);
        tag.putBoolean("artSpot", spotted);
        tag.putInt("artSpread", spreadQ);
        tag.putInt("artAz", aimAz);
        tag.putInt("artEl", aimEl);
        tag.putInt("artUsed", usedCharge);
        tag.putInt("artDist", dist);
        tag.putBoolean("artOnDot", onDot);
        tag.putInt("artDotX", dotX);
        tag.putInt("artDotY", dotY);
        tag.putInt("artDotZ", dotZ);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        hasTarget = tag.getBoolean("artTarget");
        tx = tag.getInt("artX");
        tz = tag.getInt("artZ");
        pick = validPick(tag.getInt("artPick"));
        charge = Mth.clamp(tag.getInt("artCharge"), 0, Balance.HOWITZER_CHARGES);
        mission = Math.max(-1, tag.getInt("artMission"));
        seq = Math.max(0, tag.getInt("artSeq"));
        layLeft = Math.max(0, tag.getInt("artLay"));
        cooldown = Math.max(0, tag.getInt("artCool"));
        lastFire = tag.contains("artLast") ? tag.getLong("artLast") : -1000L;
        status = tag.getInt("artStatus");
        spotted = tag.getBoolean("artSpot");
        spreadQ = Math.max(0, tag.getInt("artSpread"));
        aimAz = tag.getInt("artAz");
        aimEl = tag.contains("artEl") ? tag.getInt("artEl") : -1;
        usedCharge = tag.getInt("artUsed");
        dist = tag.getInt("artDist");
        onDot = tag.getBoolean("artOnDot");
        dotX = tag.getInt("artDotX");
        dotY = tag.getInt("artDotY");
        dotZ = tag.getInt("artDotZ");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        ownerData.write(tag);
        return tag;
    }

    @Override
    public List<ItemStack> takeAll() { mission = 0; return super.takeAll(); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float az = (float) Math.toRadians(AZ_SIGN * az(pt));
        final float el = (float) Math.toRadians(EL_SIGN * (el(pt) - restElevation()));
        model.getBone("yaw").ifPresent(b -> b.setRotY(az));
        model.getBone(gun() == Artillery.MORTAR ? "tube" : "cradle").ifPresent(b -> b.setRotX(el));
        double since = sinceFire(tt);
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        model.getBone("blast").ifPresent(b -> b.setHidden(since < 0 || since > 3.0));
        boolean ready = (hasTarget || onDot) && (status == S_READY || status == S_FIRING || status == S_RELOADING || status == S_LAYING);
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready || dark));
        animateExtra(model, tt, pt, since);
    }

    protected void animateExtra(CcBones model, double tt, float pt, double since) {}

    static final float AZ_SIGN = 1.0F, EL_SIGN = 1.0F;

    protected static final int NO_HOOK = Integer.MIN_VALUE;

    protected int fireHook() { return NO_HOOK; }

    protected int checkHook(int x, int z, int type) { return NO_HOOK; }

    protected double[] solveHook(int x, int z, int type, int charge) { return null; }

    protected int ammoHook(int t) { return -1; }

    protected double guidedSpread(double spread) {
        return pick == Artillery.GUIDED && level instanceof ServerLevel sl && Mlrs.navGuided(sl.getServer(), getOwner()) ? Math.min(spread, Balance.GUIDED_SHELL_SPREAD) : spread;
    }

    protected final void holdFire(int ticks) { cooldown = Math.max(cooldown, ticks); }

    protected final int roundNo() { return seq; }

    @Override public abstract net.neoforged.neoforge.items.IItemHandler itemPort(BlockPos part, Direction side);

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return null; }

    protected static List<FluidTank> gauges(Slot[] ss) { List<FluidTank> l = new ArrayList<>(); for (Slot s : ss) l.add(s.gauge); return l; }
}

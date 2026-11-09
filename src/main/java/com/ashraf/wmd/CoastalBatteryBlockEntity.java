package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CoastalBatteryBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    public static final int X0 = -1, X1 = 2, Y0 = 0, Y1 = 2, Z0 = -1, Z1 = 2;
    public static final int CANISTERS = 4;

    public static final int S_READY = 0, S_NO_TARGET = 1, S_NO_MISSILE = 2, S_NO_FUEL = 3, S_TOO_FAR = 4, S_TOO_CLOSE = 5, S_NO_POWER = 6, S_RAISING = 7,
            S_FIRING = 8, S_LOWERING = 9, S_DISABLED = 10, S_SUBMERGED = 11, S_TRAINING = 12, S_FIRED = 99;

    public static final int A_MODE = 0, A_TARGET = 1, A_FIRE = 2, A_SALVO = 3, A_ABORT = 4, A_CLEAR = 5, A_FREQ = 6;
    public static final int P_STOWED = 0, P_RAISING = 1, P_FIRING = 2, P_HOLD = 3, P_LOWERING = 4;
    public static final int M_NET = 0, M_TYPED = 1;

    static final float TX = 8.0F, TZ = 8.0F, EV_Y = 22.0F, EV_Z = 16.0F, RAISE_DEG = 30.0F, CZ0 = -22.0F;
    static final float[][] CANS = { { 2.0F, 27.0F }, { 14.0F, 27.0F }, { 2.0F, 39.0F }, { 14.0F, 39.0F } };
    static final int[] ORDER = { 2, 3, 0, 1 };

    static final float TRAIN_SIGN = TorpedoLauncherDeckBlockEntity.TRAIN_SIGN, RAISE_SIGN = 1.0F;

    private final OwnerData ownerData = new OwnerData();
    private final Slot[] cans = new Slot[CANISTERS];
    private final Slot fuel = new Slot(Balance.CRUISE_FUEL_SLOT, false, Alkylate::isCanister);
    private final Slot[] slots;
    private final List<FluidTank> tanks;

    private int mode = M_NET, frequency = 1;
    private boolean typed;
    private int typedX, typedZ;

    private boolean hasAim;
    private double aimX, aimY, aimZ;
    private UUID lock;
    private String lockLabel = "";
    private float train, trainNow;
    private int phase = P_STOWED;
    private long phaseStart;
    private int salvoLeft, gap;
    private long lastFire = -1000L;
    private int lastCan;
    private boolean coversOff;
    private int status = S_NO_TARGET, routeLen, nextRange;
    private String salvoBy = "";

    private float shown = Float.NaN, prevShown;
    Object trainLoop;

    public CoastalBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.COASTAL_BATTERY_BE.get(), pos, state, Balance.COASTAL_FE);
        for (int i = 0; i < CANISTERS; i++) cans[i] = new Slot(1, false, s -> s.getItem() instanceof CruiseMissileItem);
        slots = new Slot[] { cans[0], cans[1], cans[2], cans[3], fuel };
        List<FluidTank> l = new ArrayList<>();
        for (Slot s : slots) l.add(s.gauge);
        tanks = List.copyOf(l);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public int mode() { return mode; }
    public int frequency() { return frequency; }
    public boolean typed() { return typed; }
    public int typedX() { return typedX; }
    public int typedZ() { return typedZ; }
    public boolean hasAim() { return hasAim; }
    public int aimX() { return Mth.floor(aimX); }
    public int aimZ() { return Mth.floor(aimZ); }
    public String lockLabel() { return lockLabel; }
    public float trainTarget() { return train; }
    public int phase() { return phase; }
    public int batteryStatus() { return status; }
    public int routeLength() { return routeLen; }
    public int nextRange() { return nextRange; }
    public int salvoLeft() { return salvoLeft; }
    public int loaded() { int n = 0; for (Slot s : cans) if (s.count() > 0) n++; return n; }

    public void act(int action, int[] data, Player by) {
        String who = by == null ? "" : by.getGameProfile().getName();
        switch (action) {
            case A_MODE -> { if (data.length > 0) mode = data[0] == M_TYPED ? M_TYPED : M_NET; }
            case A_TARGET -> { if (data.length >= 2) { typed = true; typedX = clampXZ(data[0]); typedZ = clampXZ(data[1]); mode = M_TYPED; } }
            case A_CLEAR -> typed = false;
            case A_FREQ -> { if (data.length > 0) { if (level instanceof ServerLevel sl) FireControlNet.forgetBattery(sl, getOwner(), frequency, worldPosition.asLong()); frequency = Mth.clamp(data[0], 1, 9999); } }
            case A_FIRE -> fireOrder(1, who);
            case A_SALVO -> fireOrder(CANISTERS, who);
            case A_ABORT -> { salvoLeft = 0; if (phase == P_RAISING || phase == P_FIRING) enter(P_HOLD); }
            default -> { }
        }
        if (level instanceof ServerLevel sl) aim(sl);
        refresh();
        setChanged();
        sync();
    }

    public boolean fireFromConsole(Player by) {
        int before = salvoLeft;
        act(A_FIRE, new int[0], by);
        return salvoLeft > before;
    }

    public boolean salvoReady() { return status == S_READY || status == S_TRAINING; }

    public void consoleTarget(boolean set, int x, int z, Player by) {
        if (set) act(A_TARGET, new int[] { x, z }, by);
        else { act(A_CLEAR, new int[0], by); act(A_MODE, new int[] { M_NET }, by); }
    }

    private static int clampXZ(int v) { return Mth.clamp(v, -29_999_000, 29_999_000); }

    private void fireOrder(int count, String by) {
        refresh();
        int why = check(true);
        if (why != S_READY && why != S_TRAINING) { status = why; return; }
        boolean fresh = salvoLeft <= 0;
        salvoLeft = Math.min(loaded(), Math.max(salvoLeft, 0) + count);
        salvoBy = by;
        switch (phase) {
            case P_STOWED -> enter(P_RAISING);
            case P_LOWERING -> {
                long now = level.getGameTime();
                long done = Math.max(0, Balance.COASTAL_RAISE_TICKS - (now - phaseStart));
                phase = P_RAISING; phaseStart = now - done;
            }
            case P_HOLD -> { enter(P_FIRING); gap = 0; }
            default -> { }
        }
        if (fresh && level instanceof ServerLevel sl && getOwner() != null)
            CcBattleLog.add(sl.getServer(), getOwner(), CcBattleLog.LAUNCH_OUT, "log.wmd.coastal_salvo", Integer.toString(salvoLeft), Integer.toString(aimX()), Integer.toString(aimZ()),
                    lockLabel.isEmpty() ? "-" : lockLabel);
        status = phase == P_RAISING ? S_RAISING : S_FIRING;
    }

    private void enter(int p) {
        phase = p;
        phaseStart = level != null ? level.getGameTime() : 0L;
    }

    void testLoad(MissileBuild b) {
        for (Slot s : cans) {
            ItemStack m = new ItemStack(Wmd.CRUISE_MISSILE.get());
            m.set(WmdComponents.MISSILE_BUILD.get(), b);
            s.stack = m; s.sync();
        }
        fuel.stack = new ItemStack(Wmd.ALKYLATE_CANISTER.get(), fuel.max); fuel.sync();
        setChanged();
        sync();
    }

    private void aim(ServerLevel sl) {
        Vec3 a = null;
        if (mode == M_NET) a = FireControlNet.aimOf(sl, getOwner(), frequency);
        else if (typed) a = new Vec3(typedX + 0.5, sl.getSeaLevel(), typedZ + 0.5);
        lock = null;
        lockLabel = "";
        if (a == null) { hasAim = false; return; }
        SableBridge.ShipInfo best = null;
        double bd = Balance.COASTAL_PICK;
        if (SableBridge.active()) {
            for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(a, a).inflate(Balance.COASTAL_PICK, 256.0, Balance.COASTAL_PICK))) {
                double d = Math.hypot(s.worldCenter().x - a.x, s.worldCenter().z - a.z);
                if (d < bd) { bd = d; best = s; }
            }
        }
        if (best != null) {
            a = best.worldCenter();
            lock = best.id();
            boolean sub = DeepSeasBridge.isSub(best.id()), under = NavalLayer.submerged(sl, best);
            lockLabel = (under ? "sub_down" : sub ? "sub" : "ship") + ":" + best.blockCount();
        }
        hasAim = true;
        aimX = a.x; aimY = a.y; aimZ = a.z;
    }

    private Vec3 wp(double lx, double ly, double lz) { double[] p = worldPoint(lx, ly, lz); return new Vec3(p[0], p[1], p[2]); }

    private float bearingTo(double x, double z) {
        Vec3 o = wp(TX / 16.0, 0.0, TZ / 16.0), f = wp(TX / 16.0, 0.0, TZ / 16.0 - 1.0).subtract(o), r = wp(TX / 16.0 + 1.0, 0.0, TZ / 16.0).subtract(o);
        double dx = x - o.x, dz = z - o.z;
        return (float) Math.toDegrees(Math.atan2(dx * r.x + dz * r.z, dx * f.x + dz * f.z));
    }

    static double[] mouthLocal(int c, float raise, float tr) {
        double e = Math.toRadians(RAISE_DEG * raise);
        double dy = CANS[c][1] - EV_Y, dz = CZ0 - EV_Z;
        double y = EV_Y + dy * Math.cos(e) - dz * Math.sin(e), z = EV_Z + dy * Math.sin(e) + dz * Math.cos(e);
        double x = CANS[c][0] - TX, zz = z - TZ, t = Math.toRadians(tr);
        double rx = x * Math.cos(t) - zz * Math.sin(t), rz = x * Math.sin(t) + zz * Math.cos(t);
        return new double[] { (TX + rx) / 16.0, y / 16.0, (TZ + rz) / 16.0 };
    }

    static double[] frontLocal(float tr) { double t = Math.toRadians(tr); return new double[] { Math.sin(t), -Math.cos(t) }; }

    private int nextCanister() { for (int i : ORDER) if (cans[i].count() > 0) return i; return -1; }

    private CruiseRoute route(int c) {
        double[] m = mouthLocal(c, 1.0F, trainNow), f = frontLocal(trainNow);
        Vec3 mw = wp(m[0], m[1], m[2]), fw = wp(m[0] + f[0], m[1], m[2] + f[1]).subtract(mw);
        return new CruiseRoute(mw.x, mw.z, fw.x, fw.z, new int[] { aimX() }, new int[] { aimZ() }, (float) Balance.CRUISE_SPEED, null);
    }

    private boolean onBearing() { return Math.abs(Mth.wrapDegrees(train - trainNow)) < 1.0F; }

    private int check(boolean full) {
        if (!hasAim) return S_NO_TARGET;
        if (lockLabel.startsWith("sub_down")) return S_SUBMERGED;
        int c = nextCanister();
        if (c < 0) return S_NO_MISSILE;
        if (Config.Server.refuse(CruiseMissileItem.buildOf(cans[c].stack)) != null) return S_DISABLED;
        if (fuel.count() < Balance.CRUISE_FUEL_CANISTERS) return S_NO_FUEL;
        if (full && !hasPower()) return S_NO_POWER;
        double len = route(c).length();
        if (len < Balance.CRUISE_MIN_RANGE) return S_TOO_CLOSE;
        if (len > MissileStats.range(CruiseMissileItem.buildOf(cans[c].stack)) + 0.5) return S_TOO_FAR;
        return onBearing() ? S_READY : S_TRAINING;
    }

    private void refresh() {
        if (level == null || level.isClientSide) return;
        int c = nextCanister();
        routeLen = hasAim ? (int) Math.round(route(Math.max(0, c)).length()) : 0;
        nextRange = c >= 0 ? MissileStats.range(CruiseMissileItem.buildOf(cans[c].stack)) : 0;
        status = switch (phase) {
            case P_RAISING -> S_RAISING;
            case P_FIRING -> onBearing() ? S_FIRING : S_TRAINING;
            case P_LOWERING -> S_LOWERING;
            default -> check(true);
        };
    }

    static short[] seaSkim(ServerLevel level, CruiseRoute r) {
        double L = r.length();
        int n = (int) Math.floor(L / CruiseRoute.STEP) + 2;
        double[] ground = new double[n];
        boolean[] sea = new boolean[n];
        for (int i = 0; i < n; i++) {
            double[] p = r.ground(i * (double) CruiseRoute.STEP);
            int x = Mth.floor(p[0]), z = Mth.floor(p[1]);
            int g = CruiseRoute.groundAt(level, x, z);
            ground[i] = g;
            BlockPos top = new BlockPos(x, g - 1, z);
            sea[i] = level.hasChunkAt(top) ? level.getFluidState(top).is(FluidTags.WATER) : g <= level.getSeaLevel() + 1;
        }
        short[] a = new short[n];
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            double m = ground[i];
            boolean open = true;
            for (int k = Math.max(0, i - 1); k <= Math.min(n - 1, i + 1); k++) { m = Math.max(m, ground[k]); open &= sea[k]; }
            raw[i] = m + (open ? Balance.COASTAL_SKIM_ALT : Balance.CRUISE_ALT);
        }
        double top = level.getMaxBuildHeight() - 8;
        for (int i = 0; i < n; i++) {
            double v = (raw[Math.max(0, i - 1)] + 2.0 * raw[i] + raw[Math.min(n - 1, i + 1)]) / 4.0;
            v = Math.max(v, ground[i] + Balance.COASTAL_SKIM_ALT);
            a[i] = (short) Mth.clamp(Math.round(v), level.getMinBuildHeight(), (long) top);
        }
        return a;
    }

    private int fireOne() {
        int why = check(true);
        if (why != S_READY) return why;
        if (!(level instanceof ServerLevel sl)) return S_NO_TARGET;
        int c = nextCanister();
        MissileBuild b = CruiseMissileItem.buildOf(cans[c].stack);
        CruiseRoute r = route(c);
        r = r.withAlt(seaSkim(sl, r));
        double[] m = mouthLocal(c, 1.0F, trainNow);
        Vec3 o = wp(m[0], m[1], m[2]);
        int groundY = CruiseRoute.groundAt(sl, aimX(), aimZ());
        double miss = lock != null ? 0.0 : Config.missRadius(b.guidance()) * Balance.CRUISE_MISS_FACTOR;
        FlightManager mgr = FlightManager.get(sl);
        long id = mgr.launchCruise(sl, o, r, groundY, getOwnerName().isEmpty() ? "?" : getOwnerName(), getOwner(), miss, b);
        if (lock != null) { mgr.shipLocks.put(id, lock); Wmd.LOGGER.info("Coastal battery: flight {} locks onto ship {}", id, lock); }
        cans[c].take(1);
        fuel.take(Balance.CRUISE_FUEL_CANISTERS);
        coversOff = true;
        lastFire = sl.getGameTime();
        lastCan = c;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(sl, null, o.x, o.y, o.z, LaunchSoundPayload.range(LaunchSoundPayload.CRUISE),
                new LaunchSoundPayload(LaunchSoundPayload.CRUISE, o.x, o.y, o.z));
        NoiseNet.pulse(sl, o, Balance.COASTAL_LAUNCH_DB, NoiseNet.Kind.LAUNCH, getOwner(), 20);
        ServerPlayer p = salvoBy.isEmpty() ? null : sl.getServer().getPlayerList().getPlayerByName(salvoBy);
        if (p != null) p.displayClientMessage(Component.translatable("message.wmd.coastal.away", c + 1, aimX(), aimZ(), (int) Math.round(r.length()), r.ticks() / 20), false);
        Wmd.LOGGER.info("Coastal battery missile {} away from {} (canister {}) to {} {}, {} blocks, lock {}", id, worldPosition, c, aimX(), aimZ(), (int) r.length(), lock);
        setChanged();
        return S_FIRED;
    }

    @Override
    protected boolean process() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        boolean changed = false;
        if (now % 10 == 0) {
            aim(sl);
            if (hasAim) train = Mth.wrapDegrees(bearingTo(aimX, aimZ));
        }
        boolean training = !onBearing() && hasAim;
        if (training) {
            float d = Mth.wrapDegrees(train - trainNow);
            trainNow = Mth.wrapDegrees(trainNow + Mth.clamp(d, -Balance.COASTAL_TRAIN_SPEED, Balance.COASTAL_TRAIN_SPEED));
            if (now % 5 == 0 || onBearing()) changed = true;
        }
        if (coversOff && loaded() == CANISTERS) { coversOff = false; changed = true; }
        int stopped = -1;
        switch (phase) {
            case P_RAISING -> { if (now - phaseStart >= Balance.COASTAL_RAISE_TICKS) { enter(P_FIRING); gap = 0; changed = true; } }
            case P_FIRING -> {
                if (gap > 0) gap--;
                else if (salvoLeft > 0 && onBearing()) {
                    int r = fireOne();
                    if (r == S_FIRED) { salvoLeft--; gap = Balance.COASTAL_SALVO_GAP; }
                    else { salvoLeft = 0; stopped = r; }
                    changed = true;
                }
                if (salvoLeft <= 0 && gap <= 0) { enter(P_HOLD); changed = true; }
            }
            case P_HOLD -> { if (now - phaseStart >= Balance.COASTAL_HOLD_TICKS) { enter(P_LOWERING); changed = true; } }
            case P_LOWERING -> { if (now - phaseStart >= Balance.COASTAL_RAISE_TICKS) { enter(P_STOWED); changed = true; } }
            default -> { }
        }
        if (now % 20 == 7) FireControlNet.beatBattery(sl, getOwner(), frequency, worldPosition.asLong());
        if (changed || now % 20 == 0) {
            int old = status, oldLen = routeLen;
            refresh();
            if (stopped >= 0) status = stopped;
            if (changed || old != status || oldLen != routeLen) { setChanged(); sync(); }
        }
        return phase != P_STOWED || training;
    }

    @Override
    protected void idle() {
        super.idle();
        if (level != null && level.getGameTime() % 20 == 0 && status != S_NO_POWER) { status = S_NO_POWER; setChanged(); sync(); }
        if (level instanceof ServerLevel sl && sl.getGameTime() % 20 == 7) FireControlNet.beatBattery(sl, getOwner(), frequency, worldPosition.asLong());
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) FireControlNet.forgetBattery(sl, getOwner(), frequency, worldPosition.asLong());
        super.setRemoved();
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    float raise(double tt) {
        double k = Mth.clamp((tt - phaseStart) / Math.max(1, Balance.COASTAL_RAISE_TICKS), 0.0, 1.0);
        k = k * k * (3.0 - 2.0 * k);
        return switch (phase) { case P_RAISING -> (float) k; case P_FIRING, P_HOLD -> 1.0F; case P_LOWERING -> (float) (1.0 - k); default -> 0.0F; };
    }

    public float shownTrain(float pt) { return Float.isNaN(shown) ? trainNow : prevShown + Mth.wrapDegrees(shown - prevShown) * pt; }

    boolean armed() { return hasAim && status != S_NO_POWER && status != S_NO_MISSILE && status != S_NO_FUEL && status != S_NO_TARGET && status != S_SUBMERGED; }

    public static void clientTickBattery(Level level, BlockPos pos, BlockState state, CoastalBatteryBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (Float.isNaN(be.shown)) be.shown = be.trainNow;
        be.prevShown = be.shown;
        be.shown = Mth.wrapDegrees(be.shown + Mth.clamp(Mth.wrapDegrees(be.trainNow - be.shown), -Balance.COASTAL_TRAIN_SPEED, Balance.COASTAL_TRAIN_SPEED));
        final Vec3 at = Vec3.atCenterOf(pos);
        be.trainLoop = LoopSound.keep(be.trainLoop, level.getGameTime(), WmdSounds.DECK_TRAIN_LOOP.get(), Balance.SND_MACHINE, 0.8F, () -> at,
                () -> !be.isRemoved() && Math.abs(Mth.wrapDegrees(be.trainNow - be.shown)) > 0.5F);
        be.shotEffects(level);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float tr = (float) Math.toRadians(TRAIN_SIGN * shownTrain(pt));
        final float el = (float) Math.toRadians(RAISE_SIGN * RAISE_DEG * raise(tt));
        final boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        model.getBone("base").ifPresent(b -> { b.setPosX(0.0F); b.setPosZ(0.0F); b.setRotX(0.0F); b.setRotZ(0.0F); });
        model.getBone("train").ifPresent(b -> b.setRotY(tr));
        model.getBone("elev").ifPresent(b -> b.setRotX(el));
        model.getBone("covers").ifPresent(b -> b.setHidden(coversOff));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!armed() || dark));
    }

    private void shotEffects(Level level) {
        long since = level.getGameTime() - lastFire;
        if (since < 0 || since > 30) return;
        var rnd = level.random;
        float tr = shownTrain(1.0F);
        double[] ml = mouthLocal(lastCan, 1.0F, tr), f = frontLocal(tr);
        double[] o = worldPoint(ml[0], ml[1], ml[2]), o2 = worldPoint(ml[0] + f[0], ml[1], ml[2] + f[1]);
        double fx = o2[0] - o[0], fz = o2[2] - o[2];
        double cs = Math.cos(Math.toRadians(RAISE_DEG)), sn = Math.sin(Math.toRadians(RAISE_DEG));
        if (since == 0) for (int i = 0; i < 14; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, o[0], o[1], o[2], fx * cs * 0.4 + rnd.nextGaussian() * 0.12, sn * 0.4 + rnd.nextGaussian() * 0.12, fz * cs * 0.4 + rnd.nextGaussian() * 0.12);
        int n = since < 4 ? 12 : 2;
        for (int i = 0; i < n; i++) {
            double k = rnd.nextDouble();
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, o[0] - fx * cs * 4.0 * k + rnd.nextGaussian() * 0.3, o[1] - sn * 4.0 * k + rnd.nextGaussian() * 0.3,
                    o[2] - fz * cs * 4.0 * k + rnd.nextGaussian() * 0.3, -fx * 0.08 + rnd.nextGaussian() * 0.02, 0.02, -fz * 0.08 + rnd.nextGaussian() * 0.02);
            if (since < 4) level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, o[0] + rnd.nextGaussian() * 0.2, o[1] + rnd.nextGaussian() * 0.2, o[2] + rnd.nextGaussian() * 0.2,
                    fx * cs * 0.3, sn * 0.3, fz * cs * 0.3);
        }
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); for (int i = 0; i < slots.length; i++) l.add(Fluids.EMPTY); return l; }
    @Override protected int inputTanks() { return slots.length; }
    @Override protected List<String> labelKeys() { return List.of("canister_1", "canister_2", "canister_3", "canister_4", "alkylate_canisters"); }
    @Override protected String[] iconIds() { return new String[] { "cruise_missile", "cruise_missile", "cruise_missile", "cruise_missile", "alkylate_canister" }; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return -1.0F; }

    @Override
    public int guiStatus() {
        if (isRunning()) return 1;
        return guiEnergy() != null && !hasPower() ? 2 : 0;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.COASTAL_FE * 20),
                Component.translatable("gui.wmd.info.coastal_battery", Balance.CRUISE_FUEL_CANISTERS, (int) Balance.COASTAL_SKIM_ALT));
    }

    @Override
    protected String detail() {
        return (hasAim ? "aim " + aimX() + " " + aimZ() + (lock != null ? " locked " + lockLabel : "") + ", " + routeLen + " blocks" : "no target") + ", mode " + mode + " freq " + frequency
                + ", train " + Math.round(trainNow) + " -> " + Math.round(train) + ", missiles " + loaded() + ", fuel " + fuel.count() + ", phase " + phase + ", status " + status;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CL_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    @Override
    public List<ItemStack> takeAll() { salvoLeft = 0; return super.takeAll(); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("cbMode", mode);
        tag.putInt("cbFreq", frequency);
        tag.putBoolean("cbTyped", typed);
        tag.putInt("cbTx", typedX);
        tag.putInt("cbTz", typedZ);
        tag.putBoolean("cbAim", hasAim);
        tag.putDouble("cbAx", aimX);
        tag.putDouble("cbAy", aimY);
        tag.putDouble("cbAz", aimZ);
        tag.putString("cbLock", lockLabel);
        tag.putFloat("cbTrain", train);
        tag.putFloat("cbTrainNow", trainNow);
        tag.putInt("cbPhase", phase);
        tag.putLong("cbPhaseT", phaseStart);
        tag.putInt("cbSalvo", salvoLeft);
        tag.putInt("cbGap", gap);
        tag.putLong("cbLast", lastFire);
        tag.putInt("cbLastCan", lastCan);
        tag.putBoolean("cbCovers", coversOff);
        tag.putInt("cbStatus", status);
        tag.putInt("cbLen", routeLen);
        tag.putInt("cbRange", nextRange);
        tag.putString("cbBy", salvoBy);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        mode = tag.getInt("cbMode") == M_TYPED ? M_TYPED : M_NET;
        frequency = Mth.clamp(tag.contains("cbFreq") ? tag.getInt("cbFreq") : 1, 1, 9999);
        typed = tag.getBoolean("cbTyped");
        typedX = tag.getInt("cbTx");
        typedZ = tag.getInt("cbTz");
        hasAim = tag.getBoolean("cbAim");
        aimX = tag.getDouble("cbAx");
        aimY = tag.getDouble("cbAy");
        aimZ = tag.getDouble("cbAz");
        lockLabel = tag.getString("cbLock");
        train = tag.getFloat("cbTrain");
        trainNow = tag.getFloat("cbTrainNow");
        phase = Mth.clamp(tag.getInt("cbPhase"), P_STOWED, P_LOWERING);
        phaseStart = tag.getLong("cbPhaseT");
        salvoLeft = Math.max(0, tag.getInt("cbSalvo"));
        gap = Math.max(0, tag.getInt("cbGap"));
        lastFire = tag.contains("cbLast") ? tag.getLong("cbLast") : -1000L;
        lastCan = Mth.clamp(tag.getInt("cbLastCan"), 0, CANISTERS - 1);
        coversOff = tag.getBoolean("cbCovers");
        status = tag.getInt("cbStatus");
        routeLen = tag.getInt("cbLen");
        nextRange = tag.getInt("cbRange");
        salvoBy = tag.getString("cbBy");
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
}

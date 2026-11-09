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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class CruiseLauncherBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 2, Z0 = 0, Z1 = 6;
    public static final int CANISTERS = 4;

    public static final int S_READY = 0, S_NO_TARGET = 1, S_NO_MISSILE = 2, S_NO_FUEL = 3, S_TOO_FAR = 4, S_TOO_CLOSE = 5, S_NO_POWER = 6, S_RAISING = 7,
            S_FIRING = 8, S_LOWERING = 9, S_DISABLED = 10, S_FIRED = 99;

    public static final int A_ROUTE = 0, A_DESIGNATED = 1, A_FIRE = 2, A_SALVO = 3, A_ABORT = 4, A_CLEAR_WP = 5;

    public static final int P_STOWED = 0, P_RAISING = 1, P_FIRING = 2, P_HOLD = 3, P_LOWERING = 4;

    static final float HINGE_Y = 13.0F, HINGE_Z = 94.0F, RAISE_DEG = 35.0F, CAP_DEG = 95.0F;

    static final float MOUTH_X = -10.5F, MOUTH_Y = 38.5F, MOUTH_Z = -7.6F, COL_DX = 21.0F, ROW_DY = -17.0F;

    static final float XS = -1.0F, RAISE_SIGN = 1.0F, CAP_SIGN = 1.0F;

    private final OwnerData ownerData = new OwnerData();

    private final Slot[] cans = new Slot[CANISTERS];
    private final Slot fuel = new Slot(Balance.CRUISE_FUEL_SLOT, false, Alkylate::isCanister);
    private final Slot[] slots;
    private final List<FluidTank> tanks;
    private final IItemHandler hatch;

    private int wpN;
    private final int[] wpx = new int[Balance.CRUISE_WAYPOINTS], wpz = new int[Balance.CRUISE_WAYPOINTS];
    private boolean hasTarget;
    private int tx, tz;

    private int phase = P_STOWED;
    private long phaseStart;
    private int salvoLeft, gap, salvoFired;
    private long lastFire = -1000L;
    private int lastCan;
    private final long[] capTime = { -1L, -1L, -1L, -1L };
    private int status = S_NO_TARGET;
    private int routeLen, nextRange;
    private String salvoBy = "";
    private short[] salvoAlt;

    public CruiseLauncherBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CRUISE_LAUNCHER_BE.get(), pos, state, Balance.CRUISE_LAUNCHER_FE_PER_TICK);
        for (int i = 0; i < CANISTERS; i++) cans[i] = new Slot(1, false, s -> s.getItem() instanceof CruiseMissileItem);
        slots = new Slot[] { cans[0], cans[1], cans[2], cans[3], fuel };
        List<FluidTank> l = new ArrayList<>();
        for (Slot s : slots) l.add(s.gauge);
        tanks = List.copyOf(l);
        hatch = inHandler(slots);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public int waypoints() { return wpN; }
    public int wpX(int i) { return wpx[i]; }
    public int wpZ(int i) { return wpz[i]; }
    public boolean hasTarget() { return hasTarget; }
    public int targetX() { return tx; }
    public int targetZ() { return tz; }
    public int phase() { return phase; }
    public int launcherStatus() { return status; }
    public int routeLength() { return routeLen; }
    public int nextRange() { return nextRange; }
    public int salvoLeft() { return salvoLeft; }

    public int loaded() { int n = 0; for (Slot s : cans) if (s.count() > 0) n++; return n; }

    public int fuelCanisters() { return fuel.count(); }

    public void act(int action, int[] data, Player by) {
        String who = by == null ? "" : by.getGameProfile().getName();
        switch (action) {
            case A_ROUTE -> setRoute(data);
            case A_DESIGNATED -> {
                CcTarget t = CcTarget.of(getOwner());
                if (t == null) { if (by != null) by.displayClientMessage(Component.translatable("message.wmd.cruise.no_designated"), true); return; }
                hasTarget = true; tx = t.x(); tz = t.z();
                refresh();
            }
            case A_FIRE -> fireOrder(1, who);
            case A_SALVO -> fireOrder(CANISTERS, who);
            case A_ABORT -> { salvoLeft = 0; if (phase == P_RAISING || phase == P_FIRING) enter(P_HOLD); refresh(); }
            case A_CLEAR_WP -> { wpN = 0; refresh(); }
            default -> { }
        }
        setChanged();
        sync();
    }

    public int order(int x, int z, int count, String by) {
        hasTarget = true; tx = clampXZ(x); tz = clampXZ(z);
        refresh();
        fireOrder(Mth.clamp(count, 1, CANISTERS), by);
        setChanged();
        sync();
        return status;
    }

    public void aimAt(int x, int z) {
        hasTarget = true; tx = clampXZ(x); tz = clampXZ(z);
        salvoAlt = null;
        refresh();
        setChanged();
        sync();
    }

    public int checkAt(int x, int z) { return check(true, x, z); }

    public int strikeTicks(int x, int z) {
        int c = nextCanister();
        if (c < 0) return -1;
        int raise = phase == P_HOLD || phase == P_FIRING ? 0 : Balance.CRUISE_RAISE_TICKS;
        return raise + 1 + routeTo(c, x, z).ticks();
    }

    public int flightTicks() {
        int c = nextCanister();
        return c < 0 || !hasTarget ? -1 : routeTo(c, tx, tz).ticks();
    }

    private void setRoute(int[] d) {
        if (d == null || d.length < 1) return;
        int n = Mth.clamp(d[0], 0, Balance.CRUISE_WAYPOINTS);
        if (d.length < 1 + 2 * n) return;
        wpN = n;
        for (int i = 0; i < n; i++) { wpx[i] = clampXZ(d[1 + 2 * i]); wpz[i] = clampXZ(d[2 + 2 * i]); }
        if (d.length >= 3 + 2 * n) { hasTarget = true; tx = clampXZ(d[1 + 2 * n]); tz = clampXZ(d[2 + 2 * n]); }
        salvoAlt = null;
        refresh();
    }

    private static int clampXZ(int v) { return Mth.clamp(v, -29_999_000, 29_999_000); }

    private void fireOrder(int count, String by) {
        refresh();
        int why = check(true);
        if (why != S_READY) { status = why; return; }
        boolean newSalvo = salvoLeft <= 0;
        salvoLeft = Math.min(loaded(), Math.max(salvoLeft, 0) + count);
        salvoBy = by;
        salvoAlt = null;
        if (newSalvo) salvoFired = 0;
        switch (phase) {
            case P_STOWED -> enter(P_RAISING);
            case P_LOWERING -> {
                long now = level.getGameTime();
                long done = Math.max(0, Balance.CRUISE_RAISE_TICKS - (now - phaseStart));
                phase = P_RAISING; phaseStart = now - done;
            }
            case P_HOLD -> { enter(P_FIRING); gap = 0; }
            default -> { }
        }
        if (newSalvo && level instanceof ServerLevel sl && getOwner() != null)
            CcBattleLog.add(sl.getServer(), getOwner(), CcBattleLog.LAUNCH_OUT, "log.wmd.cruise_salvo", Integer.toString(salvoLeft), Integer.toString(tx), Integer.toString(tz), Integer.toString(wpN));
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

    private int nextCanister() { for (int i = 0; i < CANISTERS; i++) if (cans[i].count() > 0) return i; return -1; }

    private int[][] points(int x, int z) {
        int[] xs = new int[wpN + 1], zs = new int[wpN + 1];
        for (int i = 0; i < wpN; i++) { xs[i] = wpx[i]; zs[i] = wpz[i]; }
        xs[wpN] = x; zs[wpN] = z;
        return new int[][] { xs, zs };
    }

    private double[] front() {
        double[] a = worldPoint(0.0, 0.0, 0.0), b = worldPoint(0.0, 0.0, -1.0);
        return new double[] { b[0] - a[0], b[2] - a[2] };
    }

    private double[] mouth(int c, float raise) {
        float mx = MOUTH_X + ((c & 1) != 0 ? COL_DX : 0.0F);
        float dy = MOUTH_Y + ((c & 2) != 0 ? ROW_DY : 0.0F) - HINGE_Y, dz = MOUTH_Z - HINGE_Z;
        double a = Math.toRadians(RAISE_DEG * raise);
        double y = HINGE_Y + dy * Math.cos(a) - dz * Math.sin(a), z = HINGE_Z + dy * Math.sin(a) + dz * Math.cos(a);
        return worldPoint(mx / 16.0, y / 16.0, z / 16.0);
    }

    private double[] w(double[] p) {
        Vec3 v = SableBridge.toWorld(level, new Vec3(p[0], p[1], p[2]));
        return new double[] { v.x, v.y, v.z };
    }

    private double[] mouthW(int c, float raise) { return w(mouth(c, raise)); }

    private double[] frontW() {
        double[] a = w(worldPoint(0.0, 0.0, 0.0)), b = w(worldPoint(0.0, 0.0, -1.0));
        return new double[] { b[0] - a[0], b[2] - a[2] };
    }

    private CruiseRoute route(int c) { return routeTo(c, tx, tz); }

    private CruiseRoute routeTo(int c, int x, int z) {
        int[][] p = points(x, z);
        double[] m = mouthW(c, 1.0F), f = frontW();
        return new CruiseRoute(m[0], m[2], f[0], f[1], p[0], p[1], (float) Balance.CRUISE_SPEED, salvoAlt);
    }

    private int check(boolean full) {
        if (!hasTarget) return S_NO_TARGET;
        return check(full, tx, tz);
    }

    private int check(boolean full, int x, int z) {
        int c = nextCanister();
        if (c < 0) return S_NO_MISSILE;
        if (Config.Server.refuse(CruiseMissileItem.buildOf(cans[c].stack)) != null) return S_DISABLED;
        if (fuel.count() < Balance.CRUISE_FUEL_CANISTERS) return S_NO_FUEL;
        if (full && !hasPower()) return S_NO_POWER;
        double len = routeTo(c, x, z).length();
        if (len < Balance.CRUISE_MIN_RANGE) return S_TOO_CLOSE;
        if (len > MissileStats.range(CruiseMissileItem.buildOf(cans[c].stack)) + 0.5) return S_TOO_FAR;
        return S_READY;
    }

    private void refresh() {
        if (level == null || level.isClientSide) return;
        int c = nextCanister();
        routeLen = hasTarget ? (int) Math.round(route(Math.max(0, c)).length()) : 0;
        nextRange = c >= 0 ? MissileStats.range(CruiseMissileItem.buildOf(cans[c].stack)) : 0;
        status = switch (phase) {
            case P_RAISING -> S_RAISING;
            case P_FIRING -> S_FIRING;
            case P_LOWERING -> S_LOWERING;
            default -> check(true);
        };
    }

    private int fireOne() {
        int why = check(true);
        if (why != S_READY) return why;
        if (!(level instanceof ServerLevel sl)) return S_NO_TARGET;
        int c = nextCanister();
        ItemStack m = cans[c].stack;
        MissileBuild b = CruiseMissileItem.buildOf(m);
        CruiseRoute r = route(c);
        if (salvoAlt == null) { salvoAlt = r.sampleAltitude(sl); r = r.withAlt(salvoAlt); }
        double[] o = mouthW(c, 1.0F);
        int groundY = CruiseRoute.groundAt(sl, tx, tz);
        double miss = Config.missRadius(b.guidance()) * Balance.CRUISE_MISS_FACTOR;
        long id = FlightManager.get(sl).launchCruise(sl, new Vec3(o[0], o[1], o[2]), r, groundY, getOwnerName().isEmpty() ? "?" : getOwnerName(), getOwner(), miss, b);
        ShipDefence.assignCruiseLock(sl, FlightManager.get(sl), FlightManager.get(sl).find(id), getOwner());
        cans[c].take(1);
        fuel.take(Balance.CRUISE_FUEL_CANISTERS);
        capTime[c] = level.getGameTime();
        lastFire = level.getGameTime();
        lastCan = c;
        salvoFired++;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(sl, null, o[0], o[1], o[2], LaunchSoundPayload.range(LaunchSoundPayload.CRUISE),
                new LaunchSoundPayload(LaunchSoundPayload.CRUISE, o[0], o[1], o[2]));
        ServerPlayer p = salvoBy.isEmpty() ? null : sl.getServer().getPlayerList().getPlayerByName(salvoBy);
        if (p != null) p.displayClientMessage(Component.translatable("message.wmd.cruise.away", c + 1, tx, tz, (int) Math.round(r.length()), r.ticks() / 20), false);
        Wmd.LOGGER.info("Cruise missile {} away from {} (canister {}) to {} {} via {} waypoints, {} blocks", id, worldPosition, c, tx, tz, wpN, (int) r.length());
        setChanged();
        return S_FIRED;
    }

    @Override
    protected boolean process() {
        CruiseLaunchers.seen(this);
        long now = level.getGameTime();
        boolean changed = false;
        for (int i = 0; i < CANISTERS; i++) if (capTime[i] >= 0 && cans[i].count() > 0) { capTime[i] = -1L; changed = true; }
        int stopped = -1;
        switch (phase) {
            case P_RAISING -> { if (now - phaseStart >= Balance.CRUISE_RAISE_TICKS) { enter(P_FIRING); gap = 0; changed = true; } }
            case P_FIRING -> {
                if (gap > 0) gap--;
                else if (salvoLeft > 0) {
                    int r = fireOne();
                    if (r == S_FIRED) { salvoLeft--; gap = Balance.CRUISE_SALVO_GAP_TICKS; }
                    else { salvoLeft = 0; stopped = r; }
                    changed = true;
                }
                if (salvoLeft <= 0 && gap <= 0) { enter(P_HOLD); changed = true; }
            }
            case P_HOLD -> { if (now - phaseStart >= Balance.CRUISE_HOLD_TICKS) { enter(P_LOWERING); changed = true; } }
            case P_LOWERING -> { if (now - phaseStart >= Balance.CRUISE_RAISE_TICKS) { enter(P_STOWED); changed = true; } }
            default -> { }
        }
        if (changed || now % 20 == 0) {
            int old = status, oldLen = routeLen, oldRange = nextRange;
            refresh();
            if (stopped >= 0) status = stopped;
            if (changed || old != status || oldLen != routeLen || oldRange != nextRange) { setChanged(); sync(); }
        }
        return phase != P_STOWED;
    }

    @Override
    protected void idle() {
        super.idle();
        CruiseLaunchers.seen(this);
        if (level != null && level.getGameTime() % 20 == 0 && status != S_NO_POWER) { status = S_NO_POWER; setChanged(); sync(); }
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    float raise(double tt) {
        double k = Mth.clamp((tt - phaseStart) / Math.max(1, Balance.CRUISE_RAISE_TICKS), 0.0, 1.0);
        k = k * k * (3.0 - 2.0 * k);
        return switch (phase) { case P_RAISING -> (float) k; case P_FIRING, P_HOLD -> 1.0F; case P_LOWERING -> (float) (1.0 - k); default -> 0.0F; };
    }

    boolean armed() { return hasTarget && status != S_NO_POWER && status != S_NO_MISSILE && status != S_NO_FUEL && status != S_NO_TARGET; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float r = (float) Math.toRadians(RAISE_SIGN * RAISE_DEG * raise(tt));
        model.getBone("base").ifPresent(b -> { b.setPosX(0.0F); b.setPosZ(0.0F); b.setRotX(0.0F); b.setRotZ(0.0F); });
        model.getBone("box").ifPresent(b -> b.setRotX(r));
        for (int i = 0; i < CANISTERS; i++) {
            final float open = capTime[i] < 0 ? 0.0F : (float) Mth.clamp((tt - capTime[i]) / 3.0, 0.0, 1.0);
            final float ca = (float) Math.toRadians(CAP_SIGN * CAP_DEG * open);
            model.getBone("cap" + i).ifPresent(b -> b.setRotX(ca));
        }
        double since = tt - lastFire;
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final int c = lastCan;
        model.getBone("blast").ifPresent(b -> {
            b.setHidden(since < 0 || since > 6.0 || dark);
            b.setPosX(XS * ((c & 1) != 0 ? COL_DX : 0.0F));
            b.setPosY((c & 2) != 0 ? ROW_DY : 0.0F);
        });
        model.getBone("lamp").ifPresent(b -> b.setHidden(!armed() || dark));
    }

    @Override
    protected void clientEffects(Level level) {
        long now = level.getGameTime();
        long since = now - lastFire;
        if (since < 0 || since > 30) return;
        var rnd = level.random;
        double[] o = mouth(lastCan, 1.0F);
        double[] f = front();
        double cs = Math.cos(Math.toRadians(RAISE_DEG)), sn = Math.sin(Math.toRadians(RAISE_DEG));
        int n = since < 4 ? 12 : 2;
        for (int i = 0; i < n; i++) {
            double k = rnd.nextDouble();
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, o[0] - f[0] * cs * 6.5 * k + rnd.nextGaussian() * 0.3, o[1] - sn * 6.5 * k + rnd.nextGaussian() * 0.3,
                    o[2] - f[1] * cs * 6.5 * k + rnd.nextGaussian() * 0.3, -f[0] * 0.08 + rnd.nextGaussian() * 0.02, 0.02, -f[1] * 0.08 + rnd.nextGaussian() * 0.02);
            if (since < 4) level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, o[0] + rnd.nextGaussian() * 0.2, o[1] + rnd.nextGaussian() * 0.2, o[2] + rnd.nextGaussian() * 0.2,
                    f[0] * cs * 0.3, sn * 0.3, f[1] * cs * 0.3);
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
        return List.of(Component.translatable("gui.wmd.info.use", Balance.CRUISE_LAUNCHER_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.cruise_launcher", Balance.CRUISE_FUEL_CANISTERS, Balance.CRUISE_SALVO_GAP_TICKS / 20.0));
    }

    @Override
    protected String detail() {
        return (hasTarget ? "target " + tx + " " + tz + " via " + wpN + " waypoints, " + routeLen + " blocks" : "no target") + ", missiles " + loaded() + ", fuel " + fuel.count()
                + ", phase " + phase + ", status " + status;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CL_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 5); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    @Override
    public List<ItemStack> takeAll() { salvoLeft = 0; return super.takeAll(); }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 3, Direction.WEST) ? hatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 6, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(-1, 0, 3, Direction.WEST, false).power(0, 0, 6, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("cmWp", wpN);
        tag.putIntArray("cmWx", wpx);
        tag.putIntArray("cmWz", wpz);
        tag.putBoolean("cmTarget", hasTarget);
        tag.putInt("cmX", tx);
        tag.putInt("cmZ", tz);
        tag.putInt("cmPhase", phase);
        tag.putLong("cmPhaseT", phaseStart);
        tag.putInt("cmSalvo", salvoLeft);
        tag.putInt("cmGap", gap);
        tag.putInt("cmFired", salvoFired);
        tag.putLong("cmLast", lastFire);
        tag.putInt("cmLastCan", lastCan);
        tag.putLongArray("cmCaps", capTime);
        tag.putInt("cmStatus", status);
        tag.putInt("cmLen", routeLen);
        tag.putInt("cmRange", nextRange);
        tag.putString("cmBy", salvoBy);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        wpN = Mth.clamp(tag.getInt("cmWp"), 0, Balance.CRUISE_WAYPOINTS);
        int[] ax = tag.getIntArray("cmWx"), az = tag.getIntArray("cmWz");
        for (int i = 0; i < Balance.CRUISE_WAYPOINTS; i++) { wpx[i] = i < ax.length ? ax[i] : 0; wpz[i] = i < az.length ? az[i] : 0; }
        hasTarget = tag.getBoolean("cmTarget");
        tx = tag.getInt("cmX");
        tz = tag.getInt("cmZ");
        phase = Mth.clamp(tag.getInt("cmPhase"), P_STOWED, P_LOWERING);
        phaseStart = tag.getLong("cmPhaseT");
        salvoLeft = Math.max(0, tag.getInt("cmSalvo"));
        gap = Math.max(0, tag.getInt("cmGap"));
        salvoFired = tag.getInt("cmFired");
        lastFire = tag.contains("cmLast") ? tag.getLong("cmLast") : -1000L;
        lastCan = Mth.clamp(tag.getInt("cmLastCan"), 0, CANISTERS - 1);
        long[] caps = tag.getLongArray("cmCaps");
        for (int i = 0; i < CANISTERS; i++) capTime[i] = i < caps.length ? caps[i] : -1L;
        status = tag.getInt("cmStatus");
        routeLen = tag.getInt("cmLen");
        nextRange = tag.getInt("cmRange");
        salvoBy = tag.getString("cmBy");
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

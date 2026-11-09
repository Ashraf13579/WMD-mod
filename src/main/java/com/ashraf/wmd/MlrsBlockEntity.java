package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class MlrsBlockEntity extends ArtilleryGunBlockEntity {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 3, Z0 = 0, Z1 = 8;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(2, 0, 4, Direction.EAST, false).power(0, 0, 8, Direction.SOUTH, false);

    private static final double LAUNCH_H = 51.0 / 16.0;

    static final float HATCH_OPEN = 95.0F;

    static final float LIFT_SIGN = 1.0F, HATCH_SIGN = 1.0F, RAM_SIGN = 1.0F;
    static final int POD_ROCKETS = 6;

    private final Slot hePods = shellSlots[Artillery.HE], guidedPods = shellSlots[Artillery.GUIDED];
    private final Slot[] slots = { hePods, guidedPods };
    private final List<FluidTank> tanks = gauges(slots);
    private final IItemHandler hatch = inHandler(slots);

    private final int[] podType = { -1, -1 }, podLeft = { 0, 0 };
    private long reloadStart = -100_000L;
    private int reloadMask;
    private int lastTube = -1;
    private long lastShot = -1000L;
    private double salvoEx, salvoEz;
    private boolean navOk;

    public MlrsBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.MLRS_BE.get(), pos, state, Balance.MLRS_FE_PER_TICK);
    }

    @Override public int gun() { return Artillery.ROCKET; }
    @Override protected int magazine() { return Balance.MLRS_MAGAZINE; }
    @Override protected int layTicks() { return Balance.MLRS_LAY_TICKS; }
    @Override protected double[] pivotLocal() { return new double[] { 0.0, 0.0, Mlrs.YAW_Z / 16.0 }; }
    @Override protected double trunnionHeight() { return LAUNCH_H; }
    @Override protected double barrelLength() { return 6.0; }
    @Override protected float restElevation() { return 0.0F; }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 4, Direction.EAST) ? hatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 8, Direction.SOUTH) ? energy() : null; }

    static boolean rocketType(int t) { return t == Artillery.HE || t == Artillery.GUIDED; }

    private Slot slotOf(int t) { return t == Artillery.GUIDED ? guidedPods : hePods; }

    public int podType(int p) { return podType[p & 1]; }

    public int podLeft(int p) { return podLeft[p & 1]; }

    public int loaded(int t) {
        int n = 0;
        for (int p = 0; p < 2; p++) if (podType[p] == t) n += podLeft[p];
        return n;
    }

    public long reloadStart() { return reloadStart; }

    public boolean reloading(double now) { return now - reloadStart >= 0 && now - reloadStart < Balance.MLRS_RELOAD_TICKS; }

    public float reload01(double now) { return reloading(now) ? (float) ((now - reloadStart) / Balance.MLRS_RELOAD_TICKS) : 1.0F; }

    public boolean navOk() { return navOk; }

    private boolean freeFor(int p, int t) {
        if (podType[p] < 0 || podLeft[p] <= 0) return true;
        if (podType[p] == t) return false;
        Slot back = slotOf(podType[p]);
        return podLeft[p] >= POD_ROCKETS && back.count() < back.max;
    }

    private boolean canReload(int t) { return rocketType(t) && slotOf(t).count() > 0 && (freeFor(0, t) || freeFor(1, t)); }

    private boolean launcherTick() {
        if (level == null) return false;
        long now = level.getGameTime();
        if (now % 20 == 7 && level instanceof ServerLevel sl) {
            boolean n = Mlrs.navGuided(sl.getServer(), getOwner());
            if (n != navOk) { navOk = n; setChanged(); sync(); }
        }
        if (reloading(now)) return true;
        int t = picked();
        if (!rocketType(t) || loaded(t) > 0 || !canReload(t)) return false;
        int mask = 0;
        for (int p = 0; p < 2 && slotOf(t).count() > 0; p++) {
            if (!freeFor(p, t)) continue;
            if (podType[p] >= 0) {
                mask |= 4 << p;
                if (podLeft[p] > 0) slotOf(podType[p]).add(Artillery.shellItem(Artillery.ROCKET, podType[p]), 1);
            }
            slotOf(t).take(1);
            podType[p] = t;
            podLeft[p] = POD_ROCKETS;
            mask |= 1 << p;
        }
        if ((mask & 3) == 0) return false;
        reloadMask = mask;
        reloadStart = now;
        holdFire(Balance.MLRS_RELOAD_TICKS);
        setChanged();
        sync();
        return true;
    }

    @Override
    protected boolean process() {
        boolean r = launcherTick();
        return super.process() || r;
    }

    @Override
    protected double[] solveHook(int x, int z, int type, int charge) {
        if (level == null) return null;
        double[] p = worldPointW(0.0, 0.0, Mlrs.YAW_Z / 16.0);
        double dx = x + 0.5 - p[0], dz = z + 0.5 - p[2];
        double d = Math.sqrt(dx * dx + dz * dz);
        BlockPos probe = new BlockPos(x, level.getMinBuildHeight(), z);
        double floorY = floorYW();
        double groundY = level.hasChunkAt(probe) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : floorY;
        double[] a = Mlrs.aim(d, groundY - (floorY + LAUNCH_H));
        if (a == null) return new double[] { d, 0, Double.NaN, 0, groundY, dx, dz };
        int ticks = Artillery.flightTicks(Mlrs.velocity((int) a[0]), d, a[1]);
        return new double[] { d, a[0], a[1], ticks, groundY, dx, dz };
    }

    @Override
    protected int checkHook(int x, int z, int type) {
        if (!hasPower()) return S_NO_POWER;
        if (!rocketType(type)) return S_NO_SHELLS;
        double[] s = solveHook(x, z, type, 0);
        if (s == null) return S_NO_TARGET;
        if (s[0] < minRange()) return S_TOO_CLOSE;
        if (s[0] > range() + 0.5 || Double.isNaN(s[2])) return S_TOO_FAR;
        if (loaded(type) < 1 && !canReload(type)) return S_NO_SHELLS;
        return S_READY;
    }

    @Override
    protected int ammoHook(int t) { return rocketType(t) ? loaded(t) + slotOf(t).count() * POD_ROCKETS : 0; }

    @Override
    protected int fireHook() {
        if (!hasTarget()) return S_NO_TARGET;
        int t = picked();
        int why = checkHook(targetX(), targetZ(), t);
        if (why != S_READY) return why;
        if (!(level instanceof ServerLevel sl)) return S_NO_TARGET;
        int p = -1;
        for (int q = 0; q < 2; q++) if (podType[q] == t && podLeft[q] > 0 && (p < 0 || podLeft[q] > podLeft[p])) p = q;
        if (p < 0) return S_NO_SHELLS;
        double[] s = solveHook(targetX(), targetZ(), t, 0);
        if (s == null || Double.isNaN(s[2])) return S_TOO_FAR;
        long now = level.getGameTime();
        int tube = p * POD_ROCKETS + Mth.clamp(POD_ROCKETS - podLeft[p], 0, POD_ROCKETS - 1);

        double[] m = Mlrs.mouth(tube, s[2]);
        double[] yaw = worldPointW(0.0, 0.0, Mlrs.YAW_Z / 16.0);
        double len = Math.max(1.0E-6, Math.hypot(s[5], s[6])), fx = s[5] / len, fz = s[6] / len, rx = -fz, rz = fx;
        Vec3 muzzle = new Vec3(yaw[0] + fx * m[2] / 16.0 + rx * m[0] / 16.0, floorYW() + m[1] / 16.0, yaw[2] + fz * m[2] / 16.0 + rz * m[0] / 16.0);

        boolean nav = t == Artillery.GUIDED && Mlrs.navGuided(sl.getServer(), getOwner());
        double cx = targetX() + 0.5, cz = targetZ() + 0.5, ix, iz;
        if (nav) {
            double a = level.random.nextDouble() * Math.PI * 2.0, r = Balance.MLRS_GUIDED_SPREAD * level.random.nextDouble();
            ix = cx + Math.cos(a) * r;
            iz = cz + Math.sin(a) * r;
        } else {
            if (roundNo() == 0) {
                boolean spot = Artillery.spotted(sl.getServer(), ArtilleryManager.get(sl), getOwner(), cx, cz, now);
                double a = level.random.nextDouble() * Math.PI * 2.0, r = Artillery.spread(s[0], spot) * Balance.MLRS_AIM_ERROR * level.random.nextDouble();
                salvoEx = Math.cos(a) * r;
                salvoEz = Math.sin(a) * r;
            }
            double a = level.random.nextDouble() * Math.PI * 2.0, r = Balance.MLRS_SALVO_RADIUS * Math.sqrt(level.random.nextDouble());
            ix = cx + salvoEx + Math.cos(a) * r;
            iz = cz + salvoEz + Math.sin(a) * r;
        }
        int ticks = Artillery.flightTicks(Mlrs.velocity((int) s[1]), Math.hypot(ix - muzzle.x, iz - muzzle.z), s[2]);
        boolean last = mission() == 1 || (mission() < 0 && loaded(t) == 1 && slotOf(t).count() == 0);
        ArtShell shell = ArtilleryManager.get(sl).fire(sl, getOwner(), getOwnerName(), Artillery.ROCKET, t, muzzle, ix, s[4], iz, ticks, worldPosition, roundNo() + 1, last);
        if (shell == null) return S_SKY_FULL;
        podLeft[p]--;
        lastTube = tube;
        lastShot = now;
        setChanged();
        sync();
        return S_FIRED;
    }

    @Override
    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>(super.takeAll());
        for (int p = 0; p < 2; p++) {
            if (podType[p] >= 0 && podLeft[p] >= POD_ROCKETS) l.add(new ItemStack(Artillery.shellItem(Artillery.ROCKET, podType[p])));
            podType[p] = -1;
            podLeft[p] = 0;
        }
        return l;
    }

    @Override protected List<String> labelKeys() { return List.of("mlrs_pod_he", "mlrs_pod_guided"); }

    @Override protected String[] iconIds() { return new String[] { "mlrs_pod", "mlrs_pod_guided" }; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20),
                Component.translatable("gui.wmd.info.mlrs", minRange(), range(), Balance.MLRS_RELOAD_TICKS / 20));
    }

    @Override
    protected String detail() {
        return super.detail() + ", launcher " + desc(0) + " / " + desc(1) + (level != null && reloading(level.getGameTime()) ? ", reloading" : "");
    }

    private String desc(int p) { return podType[p] < 0 ? "-" : Artillery.TYPES[Artillery.clampType(podType[p])] + " " + podLeft[p]; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        super.saveExtra(tag);
        tag.putIntArray("mlrsType", podType.clone());
        tag.putIntArray("mlrsLeft", podLeft.clone());
        tag.putLong("mlrsReload", reloadStart);
        tag.putInt("mlrsMask", reloadMask);
        tag.putInt("mlrsTube", lastTube);
        tag.putLong("mlrsShot", lastShot);
        tag.putBoolean("mlrsNav", navOk);
        tag.putDouble("mlrsEx", salvoEx);
        tag.putDouble("mlrsEz", salvoEz);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        super.loadExtra(tag);
        int[] ty = tag.getIntArray("mlrsType"), le = tag.getIntArray("mlrsLeft");
        for (int p = 0; p < 2; p++) {
            podType[p] = ty.length > p && rocketType(ty[p]) ? ty[p] : -1;
            podLeft[p] = le.length > p && podType[p] >= 0 ? Mth.clamp(le[p], 0, POD_ROCKETS) : 0;
        }
        reloadStart = tag.contains("mlrsReload") ? tag.getLong("mlrsReload") : -100_000L;
        reloadMask = tag.getInt("mlrsMask");
        lastTube = tag.contains("mlrsTube") ? tag.getInt("mlrsTube") : -1;
        lastShot = tag.contains("mlrsShot") ? tag.getLong("mlrsShot") : -1000L;
        navOk = tag.getBoolean("mlrsNav");
        salvoEx = tag.getDouble("mlrsEx");
        salvoEz = tag.getDouble("mlrsEz");
    }

    private static float smooth(double k) { float f = (float) Mth.clamp(k, 0.0, 1.0); return f * f * (3.0F - 2.0F * f); }

    private float lowered(double f) { return f >= 1.0 ? 0.0F : f < 0.08 ? smooth(f / 0.08) : f > 0.9 ? smooth((1.0 - f) / 0.1) : 1.0F; }

    private float hatchOpen(double f) { return f >= 1.0 ? 0.0F : f < 0.06 ? 0.0F : f < 0.14 ? smooth((f - 0.06) / 0.08) : f < 0.82 ? 1.0F : smooth((0.9 - f) / 0.08); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double f = reload01(tt);
        final double e = Mth.clamp(el(pt), 0.0F, (float) Mlrs.MAX_LIFT) * (1.0F - lowered(f));
        final float yawR = (float) Math.toRadians(AZ_SIGN * az(pt)), liftR = (float) Math.toRadians(LIFT_SIGN * e);
        model.getBone("yaw").ifPresent(b -> b.setRotY(yawR));
        model.getBone("lift").ifPresent(b -> b.setRotX(liftR));
        double alpha = Mlrs.ramAlpha(e) - Mlrs.RAM_ALPHA0;
        final float ramR = (float) Math.toRadians(RAM_SIGN * alpha), rodR = (float) Math.toRadians(RAM_SIGN * (alpha - e));
        model.getBone("ram").ifPresent(b -> b.setRotX(ramR));
        model.getBone("ramrod").ifPresent(b -> b.setRotX(rodR));
        final float hatchR = (float) Math.toRadians(HATCH_SIGN * HATCH_OPEN * hatchOpen(f));
        model.getBone("hatch").ifPresent(b -> b.setRotX(hatchR));
        boolean inReload = f < 1.0;
        for (int p = 0; p < 2; p++) {
            boolean swapped = inReload && (reloadMask & (1 << p)) != 0;
            boolean show, full;
            if (swapped && f < 0.25) { show = (reloadMask & (4 << p)) != 0; full = false; }
            else if (swapped && f < 0.6) { show = false; full = false; }
            else { show = podType[p] >= 0; full = swapped; }
            final boolean sh = show;
            model.getBone(p == 0 ? "pod_l" : "pod_r").ifPresent(b -> b.setHidden(!sh));
            int fired = full ? 0 : POD_ROCKETS - podLeft[p];
            for (int i = 0; i < POD_ROCKETS; i++) {
                final boolean nose = show && (full || (podType[p] >= 0 && i >= fired));
                model.getBone("tube" + (p * POD_ROCKETS + i)).ifPresent(b -> b.setHidden(!nose));
            }
        }
        double since = tt - lastShot;
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final int tube = lastTube;
        model.getBone("blast").ifPresent(b -> {
            b.setHidden(tube < 0 || since < 0 || since > 3.0 || dark);
            if (tube >= 0) {
                b.setPosX((float) -(Mlrs.TUBES[tube][0] - Mlrs.TUBES[0][0]));
                b.setPosY((float) (Mlrs.TUBES[tube][1] - Mlrs.TUBES[0][1]));
            }
        });
        int st = gunStatus();
        boolean ready = hasTarget() && (st == S_READY || st == S_FIRING || st == S_RELOADING || st == S_LAYING);
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready || dark));
    }
}

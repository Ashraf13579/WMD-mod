package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class SuperGunBlockEntity extends ArtilleryGunBlockEntity {

    public static final int X0 = -4, X1 = 4, Y0 = 0, Y1 = 8, Z0 = 0, Z1 = 20;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(-4, 0, 10, Direction.WEST, false).power(0, 0, 20, Direction.SOUTH, false);

    static final float RECOIL_PX = 16.0F, RECOIL_SIGN = 1.0F, LOAD_DROP_PX = 6.0F;

    static final int T_RUNOUT = 50, T_DOWN = 150, T_LOADER = 200, T_RAM = 260, T_UP = 310, T_RAISED = 410;

    static final float AZ_SLEW = 1.2F, EL_SLEW = 0.8F;

    private final Slot shells = new Slot(Balance.SUPER_GUN_MAGAZINE, false, s -> s.is(wmd(SuperGun.SHELL)) || s.is(wmd("shell_super_nuke")));
    private final Slot charges = new Slot(Balance.SUPER_GUN_CHARGE_SLOT, false, s -> s.is(wmd(SuperGun.CHARGE)));
    private final Slot[] slots = { shells, charges };
    private final List<FluidTank> tanks = gauges(slots);
    private final IItemHandler hatch = inHandler(slots);

    public SuperGunBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SUPER_GUN_BE.get(), pos, state, Balance.SUPER_GUN_FE_PER_TICK);
        shellSlots[Artillery.HE] = shells;
    }

    @Override public int gun() { return Artillery.SUPER_GUN; }

    @Override protected int shellTypeHook(int p) { return shells.stack.is(wmd("shell_super_nuke")) ? Artillery.SUPER_NUKE : -1; }
    @Override protected int magazine() { return Balance.SUPER_GUN_MAGAZINE; }

    public static final int S_DISABLED = 11;

    @Override
    protected int checkHook(int x, int z, int type) { return Config.Server.allowSuperGun() ? NO_HOOK : S_DISABLED; }
    @Override protected int layTicks() { return Balance.SUPER_GUN_LAY_TICKS; }
    @Override protected double[] pivotLocal() { return new double[] { 0.0, 0.0, SuperGun.PIVOT_Z }; }
    @Override protected double trunnionHeight() { return SuperGun.TRUNNION_Y; }
    @Override protected double barrelLength() { return SuperGun.BARREL_LEN; }
    @Override protected float restElevation() { return 0.0F; }
    @Override protected Slot chargeSlot() { return charges; }
    @Override public int range() { return Balance.SUPER_GUN_RANGE; }
    @Override public int minRange() { return Balance.SUPER_GUN_MIN; }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float soundVolume() { return 0.2F; }

    public int propellant() { return charges.count(); }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, -4, 0, 10, Direction.WEST) ? hatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 20, Direction.SOUTH) ? energy() : null; }

    @Override
    protected double[] solveOwn(int tx, int tz, int pick, int charge) {
        double[] pl = pivotLocal();
        double[] p = worldPointW(pl[0], pl[1], pl[2]);
        double dx = tx + 0.5 - p[0], dz = tz + 0.5 - p[2];
        double d = Math.sqrt(dx * dx + dz * dz);
        BlockPos probe = new BlockPos(tx, level.getMinBuildHeight(), tz);
        double endY = level.hasChunkAt(probe) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) : floorYW();
        double y = endY - (p[1] + trunnionHeight());
        int k = SuperGun.autoCharges(d, y);
        double el = k > 0 ? Artillery.elevation(SuperGun.velocity(k), d, y, false) : Double.NaN;
        int ticks = Double.isNaN(el) ? 0 : Artillery.flightTicks(SuperGun.velocity(k), d, el);
        return new double[] { d, Math.max(0, k), el, ticks, endY, dx, dz };
    }

    @Override
    protected int flightTicksOwn(int ticks, double dist, double[] s) { return Artillery.flightTicks(SuperGun.velocity((int) s[1]), dist, s[2]); }

    @Override
    protected int reloadOwn(int ticks) { return Balance.SUPER_GUN_RELOAD_TICKS; }

    @Override
    protected double spreadOwn(double spread) { return SuperGun.spread(spread); }

    @Override
    protected boolean logMissionOwn(int rounds, String by) {
        if (level instanceof ServerLevel sl && getOwner() != null)
            CcBattleLog.add(sl.getServer(), getOwner(), CcBattleLog.LAUNCH_OUT, "log.wmd.super_gun_fire", rounds < 0 ? "#gui.wmd.art.all" : Integer.toString(rounds),
                    Integer.toString(targetX()), Integer.toString(targetZ()));
        return true;
    }

    @Override
    void testLoad(int t) {
        shells.stack = new ItemStack(wmd(SuperGun.SHELL), shells.max);
        shells.sync();
        charges.stack = new ItemStack(wmd(SuperGun.CHARGE), charges.max);
        charges.sync();
        setChanged();
    }

    @Override
    protected boolean process() {
        long before = lastFire();
        boolean busy = super.process();
        if (lastFire() != before && level instanceof ServerLevel sl) SuperGunServer.onFired(sl, this);
        return busy;
    }

    @Override protected List<String> labelKeys() { return List.of("super_shells", "super_charges"); }

    @Override protected String[] iconIds() { return new String[] { SuperGun.SHELL, SuperGun.CHARGE }; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.art_gun", minRange(), range()),
                Component.translatable("gui.wmd.info.super_gun", Balance.SUPER_GUN_CHARGES, Balance.SUPER_GUN_RELOAD_TICKS / 20));
    }

    @Override
    protected String detail() {
        return super.detail() + ", charges " + charges.count() + ", super gun";
    }

    public int reloadLeft(long now) {
        long left = lastFire() + Balance.SUPER_GUN_RELOAD_TICKS - now;
        return left <= 0 || lastFire() < 0 ? 0 : (int) left;
    }

    static float loadFactor(double since) {
        if (since < T_RUNOUT || since > T_RAISED) return 0.0F;
        if (since < T_DOWN) return smooth((since - T_RUNOUT) / (T_DOWN - T_RUNOUT));
        if (since <= T_UP) return 1.0F;
        return 1.0F - smooth((since - T_UP) / (T_RAISED - T_UP));
    }

    static float loaderDrop(double since) {
        if (since < T_DOWN || since > T_UP) return 0.0F;
        if (since < T_LOADER) return smooth((since - T_DOWN) / (T_LOADER - T_DOWN));
        if (since <= T_RAM) return 1.0F;
        return 1.0F - smooth((since - T_RAM) / (T_UP - T_RAM));
    }

    private static float smooth(double k) { float f = (float) Mth.clamp(k, 0.0, 1.0); return f * f * (3.0F - 2.0F * f); }

    public static void clientTickSuper(Level level, BlockPos pos, BlockState state, SuperGunBlockEntity be) {
        boolean init = Float.isNaN(be.shownEl);
        float az0 = be.shownAz, el0 = be.shownEl;
        ArtilleryGunBlockEntity.clientTickGun(level, pos, state, be);
        if (init) return;
        float aimEl = be.aimElevation();
        boolean noAim = Float.isNaN(aimEl);
        float dAz = noAim ? (float) LauncherGeometry.wrap(0.0 - az0) : (float) LauncherGeometry.wrap(be.shownAz - az0);
        boolean turning = Math.abs(dAz) > 1.0E-3F;
        float targetEl = noAim ? 0.0F : Math.min(SuperGun.MAX_ELEVATION, aimEl);
        float drawn = el0 * (1.0F - loadFactor(level.getGameTime() - be.lastFire()));
        float az = az0, el;
        if (turning) {
            if (drawn >= SuperGun.CLEAR_ELEVATION - 0.01F) az = az0 + Mth.clamp(dAz, -AZ_SLEW, AZ_SLEW);
            float want = Math.max(targetEl, SuperGun.CLEAR_ELEVATION);
            el = el0 + Mth.clamp(want - el0, -EL_SLEW, EL_SLEW);
        } else el = el0 + Mth.clamp(targetEl - el0, -EL_SLEW, EL_SLEW);
        be.prevAz = az0;
        be.prevEl = el0;
        be.shownAz = az;
        be.shownEl = el;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double since = sinceFire(tt);
        final float az = (float) Math.toRadians(AZ_SIGN * az(pt));
        final float el = (float) Math.toRadians(EL_SIGN * el(pt) * (1.0F - loadFactor(since)));
        model.getBone("yaw").ifPresent(b -> b.setRotY(az));
        model.getBone("cradle").ifPresent(b -> b.setRotX(el));
        float recoil = 0.0F;
        if (since >= 0.0 && since < 3.0) recoil = (float) (since / 3.0);
        else if (since >= 3.0 && since < T_RUNOUT) recoil = (float) (1.0 - (since - 3.0) / (T_RUNOUT - 3.0));
        final float r = RECOIL_SIGN * RECOIL_PX * recoil * recoil * (3.0F - 2.0F * recoil);
        model.getBone("barrel").ifPresent(b -> b.setPosZ(r));
        model.getBone("blast").ifPresent(b -> b.setHidden(since < 0.0 || since > 4.0));
        final float drop = -LOAD_DROP_PX * loaderDrop(since);
        model.getBone("loader").ifPresent(b -> b.setPosY(drop));
        final boolean loaded = since >= T_RAM || lastFire() < 0;
        model.getBone("loader_shell").ifPresent(b -> b.setHidden(loaded));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        int st = gunStatus();
        boolean ready = hasTarget() && (st == S_READY || st == S_FIRING || st == S_RELOADING || st == S_LAYING);
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready || dark));
    }
}

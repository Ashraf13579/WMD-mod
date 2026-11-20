package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class PlasmaMortarBlockEntity extends ArtilleryGunBlockEntity {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 2, Z0 = -1, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0).fluid(1, 0, 0, Direction.EAST, "hydrogen", false)
            .power(0, 0, Z1, Direction.SOUTH, false);

    public static final int S_NO_H2 = 12, S_CHARGING = 13;

    public static final float REST_EL = 35.0F;
    static final double PIV_Y = 27.3, PIV_Z = 3.5, RAM_FOOT_Y = 18.5, RAM_FOOT_Z = -5.0, PIN_ALONG = 18.0, PIN_ABOVE = -12.2, BARREL_PX = 30.0;
    static final float RECOIL_PX = 2.5F;

    static final float RECOIL_SIGN = 1.0F, FLAP_SIGN = 1.0F, RAM_SIGN = 1.0F;
    static final double ROD_REST = ramLen(REST_EL);

    private final FluidTank hydrogen = new FluidTank(Balance.PMORT_TANK_MB, fs -> fs.getFluid() == WmdFluids.HYDROGEN.source().get());
    private final IFluidHandler h2In = fillOnly(hydrogen);
    private final List<FluidTank> tanks = List.of(hydrogen);
    private final Slot[] slots = {};

    private long sealAt = -1L;
    private int chargeQ = -1;

    public PlasmaMortarBlockEntity(BlockPos pos, BlockState state) {
        super(PlasmaMortarBlocks.PLASMA_MORTAR_BE.get(), pos, state, Balance.PMORT_IDLE_FE, Balance.PMORT_SHOT_FE + 2 * Balance.PMORT_IDLE_FE, Balance.PMORT_RECEIVE_FE);
    }

    @Override public int gun() { return Artillery.PLASMA; }
    @Override protected int shellTypeHook(int p) { return Artillery.PLASMA_BALL; }
    @Override protected int magazine() { return 1; }
    @Override protected int layTicks() { return Balance.PMORT_LAY; }
    @Override protected double[] pivotLocal() { return new double[] { 0.0, PIV_Y / 16.0, PIV_Z / 16.0 }; }
    @Override protected double trunnionHeight() { return 0.0; }
    @Override protected double barrelLength() { return BARREL_PX / 16.0; }
    @Override protected float restElevation() { return REST_EL; }
    @Override public int range() { return Balance.PMORT_RANGE; }
    @Override public int minRange() { return Balance.PMORT_MIN; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override public IEnergyStorage guiEnergy() { return energy(); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }
    @Override protected SoundEvent runSound() { return WmdSounds.PM_CHARGE_LOOP.get(); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.HYDROGEN.source().get()); }
    @Override protected int inputTanks() { return 1; }
    @Override public String guiTankUnit(int t) { return "mB"; }
    @Override protected List<String> labelKeys() { return List.of("hydrogen"); }
    @Override protected String[] iconIds() { return new String[0]; }
    @Override protected int reloadOwn(int ticks) { return Balance.PMORT_RELOAD; }
    @Override protected double spreadOwn(double spread) { return spread * Balance.PMORT_SPREAD_MUL; }

    @Override public IItemHandler itemPort(BlockPos part, Direction side) { return null; }
    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, Z1, Direction.SOUTH) ? energy() : null; }
    @Override public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 0, Direction.EAST) ? h2In : null; }

    public int hydrogenMb() { return hydrogen.getFluidAmount(); }

    public float charge01() {
        if (level != null && level.isClientSide) return Math.max(0, chargeQ) / 100.0F;
        return Math.min(1.0F, energy().getEnergyStored() / (float) Balance.PMORT_SHOT_FE);
    }
    boolean charged() { return energy().getEnergyStored() >= Balance.PMORT_SHOT_FE + Balance.PMORT_IDLE_FE; }
    public long sealAt() { return sealAt; }

    public int shotsInTank() { return hydrogen.getFluidAmount() / Balance.PMORT_H2_MB; }
    @Override protected int ammoHook(int t) { return shotsInTank(); }
    @Override public int roundsOf(int p) { return shotsInTank(); }

    static double speed(double x, double dy, double elDeg) {
        double t = Math.toRadians(elDeg), c = Math.cos(t), den = 2.0 * c * c * (x * Math.tan(t) - dy);
        if (x < 0.5 || den <= 1.0E-6) return Double.NaN;
        return Math.sqrt(Balance.ART_GRAVITY * x * x / den);
    }

    @Override
    protected double[] solveOwn(int tx, int tz, int pick, int charge) {
        double[] pl = pivotLocal();
        double[] p = worldPointW(pl[0], pl[1], pl[2]);
        double dx = tx + 0.5 - p[0], dz = tz + 0.5 - p[2];
        double d = Math.sqrt(dx * dx + dz * dz);
        BlockPos probe = new BlockPos(tx, level.getMinBuildHeight(), tz);
        double endY = level.hasChunkAt(probe) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) : floorYW();
        double v = speed(d, endY - p[1], Balance.PMORT_EL);
        double el = Double.isNaN(v) ? Double.NaN : Balance.PMORT_EL;
        int ticks = Double.isNaN(v) ? 0 : Artillery.flightTicks(v, d, Balance.PMORT_EL);
        return new double[] { d, 0, el, ticks, endY, dx, dz };
    }

    @Override
    protected int checkHook(int x, int z, int type) {
        if (hydrogen.getFluidAmount() < Balance.PMORT_H2_MB) return S_NO_H2;
        if (!hasPower()) return S_NO_POWER;
        if (level == null) return S_NO_TARGET;
        double[] s = solveOwn(x, z, 0, 0);
        if (s[0] < Balance.PMORT_MIN) return S_TOO_CLOSE;
        if (s[0] > Balance.PMORT_RANGE + 0.5 || Double.isNaN(s[2])) return S_TOO_FAR;
        return S_READY;
    }

    @Override
    protected int fireHook() {
        if (!hasTarget()) return S_NO_TARGET;
        int why = checkHook(targetX(), targetZ(), Artillery.PLASMA_BALL);
        if (why != S_READY) return why;
        if (!charged()) return S_CHARGING;
        if (!(level instanceof ServerLevel sl)) return S_NO_TARGET;
        double[] s = solveOwn(targetX(), targetZ(), 0, 0);
        boolean spot = Artillery.spotted(sl.getServer(), ArtilleryManager.get(sl), getOwner(), targetX() + 0.5, targetZ() + 0.5, level.getGameTime());
        double spread = spreadOwn(Artillery.spread(s[0], spot));
        double a = level.random.nextDouble() * Math.PI * 2.0, r = spread * level.random.nextDouble();
        double ix = targetX() + 0.5 + Math.cos(a) * r, iz = targetZ() + 0.5 + Math.sin(a) * r;
        if (OfflineGuard.refuse(sl, ix, iz) != null) return S_PROTECTED;
        double[] pl = pivotLocal();
        double[] p = worldPointW(pl[0], pl[1], pl[2]);
        double len = Math.max(1.0E-6, Math.hypot(s[5], s[6])), el = Math.toRadians(Balance.PMORT_EL), L = barrelLength();
        Vec3 muzzle = new Vec3(p[0] + s[5] / len * L * Math.cos(el), p[1] + L * Math.sin(el), p[2] + s[6] / len * L * Math.cos(el));
        double dImpact = Math.hypot(ix - muzzle.x, iz - muzzle.z);
        double v = speed(dImpact, s[4] - muzzle.y, Balance.PMORT_EL);
        if (Double.isNaN(v)) return S_TOO_FAR;
        int ticks = Artillery.flightTicks(v, dImpact, Balance.PMORT_EL);
        boolean last = mission() == 1 || shotsInTank() <= 1;
        ArtShell shell = ArtilleryManager.get(sl).fire(sl, getOwner(), getOwnerName(), Artillery.PLASMA, Artillery.PLASMA_BALL, muzzle, ix, s[4], iz, ticks, worldPosition,
                roundNo() + 1, last);
        if (shell == null) return S_SKY_FULL;
        useExtraEnergy(Balance.PMORT_SHOT_FE);
        hydrogen.drain(Balance.PMORT_H2_MB, IFluidHandler.FluidAction.EXECUTE);
        sealAt = -1L;
        return S_FIRED;
    }

    @Override
    protected boolean logMissionOwn(int rounds, String by) {
        if (level instanceof ServerLevel sl && getOwner() != null)
            CcBattleLog.add(sl.getServer(), getOwner(), CcBattleLog.LAUNCH_OUT, "log.wmd.plasma_mortar_fire", rounds < 0 ? "#gui.wmd.art.all" : Integer.toString(rounds),
                    Integer.toString(targetX()), Integer.toString(targetZ()));
        return true;
    }

    @Override
    protected boolean process() {
        long now = level.getGameTime();
        if (mission() != 0 && busyTicks() == 0) {
            if (!charged()) { holdFire(20); sealAt = -1L; }
            else if (sealAt < 0L) { sealAt = now + Balance.PMORT_SEAL; holdFire(Balance.PMORT_SEAL); sync(); }
        } else if (mission() == 0 && sealAt >= 0L) { sealAt = -1L; sync(); }
        boolean busy = super.process();
        int q = Math.round(charge01() * 100.0F);
        if (q != chargeQ && (now % 10 == 0 || q >= 100)) { chargeQ = q; setChanged(); sync(); }
        return busy || (hasTarget() && !charged());
    }

    @Override public int gunStatus() { int st = super.gunStatus(); return st == S_RELOADING && mission() != 0 && charge01() < 0.999F ? S_CHARGING : st; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse()), Component.translatable("gui.wmd.info.art_gun", minRange(), range()),
                Component.translatable("gui.wmd.info.plasma_mortar", String.format("%,d", Balance.PMORT_SHOT_FE), Balance.PMORT_H2_MB, Balance.PMORT_RELOAD / 20));
    }

    @Override
    protected String detail() {
        return super.detail() + ", charge " + Math.round(charge01() * 100) + "%, hydrogen " + hydrogen.getFluidAmount() + " mB, plasma mortar";
    }

    @Override
    void testLoad(int t) {
        hydrogen.setFluid(new net.neoforged.neoforge.fluids.FluidStack(WmdFluids.HYDROGEN.source().get(), Balance.PMORT_TANK_MB));
        chargeDirect(Balance.PMORT_SHOT_FE + 2 * Balance.PMORT_IDLE_FE);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        super.saveExtra(tag);
        tag.putLong("pmSeal", sealAt);
        tag.putInt("pmCharge", Math.round(charge01() * 100.0F));
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        super.loadExtra(tag);
        sealAt = tag.contains("pmSeal") ? tag.getLong("pmSeal") : -1L;
        chargeQ = tag.getInt("pmCharge");
    }

    static double[] pinAt(double el) {
        double t = Math.toRadians(el);
        double ay = Math.sin(t), az = -Math.cos(t), uy = Math.cos(t), uz = Math.sin(t);
        return new double[] { PIV_Y + ay * PIN_ALONG + uy * PIN_ABOVE, PIV_Z + az * PIN_ALONG + uz * PIN_ABOVE };
    }

    static double ramAngle(double el) { double[] p = pinAt(el); return Math.toDegrees(Math.atan2(p[1] - RAM_FOOT_Z, p[0] - RAM_FOOT_Y)); }

    static double ramLen(double el) { double[] p = pinAt(el); return Math.hypot(p[0] - RAM_FOOT_Y, p[1] - RAM_FOOT_Z); }

    public static void clientTickPlasma(Level level, BlockPos pos, BlockState state, PlasmaMortarBlockEntity be) {
        ArtilleryGunBlockEntity.clientTickGun(level, pos, state, be);
        ClientPlasmaMortar.tick(be);
    }

    public Vec3 breechPoint() { double[] pl = pivotLocal(); double[] p = worldPoint(pl[0], pl[1], pl[2]); return new Vec3(p[0], p[1], p[2]); }

    public float shutters(double tt) {
        double since = sinceFire(tt);
        if (!hasTarget() || charge01() < 0.6F || (since >= 0 && since < 40)) return 0.0F;
        if (sealAt >= 0L) return (float) Mth.clamp((sealAt - tt) / Balance.PMORT_SEAL, 0.0, 1.0);
        return smooth((charge01() - 0.6F) / 0.08F);
    }

    public float ball(double tt) { float o = shutters(tt); return o <= 0.0F && sealAt < 0L ? 0.0F : smooth((charge01() - 0.6F) / 0.4F); }

    private static float smooth(double k) { float f = (float) Mth.clamp(k, 0.0, 1.0); return f * f * (3.0F - 2.0F * f); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float azD = az(pt), elD = el(pt);
        model.getBone("yaw").ifPresent(b -> b.setRotY((float) Math.toRadians(AZ_SIGN * azD)));
        model.getBone("cradle").ifPresent(b -> b.setRotX((float) Math.toRadians(EL_SIGN * (elD - REST_EL))));
        final float ram = (float) Math.toRadians(RAM_SIGN * ramAngle(elD));
        final float rod = (float) (ramLen(elD) - ROD_REST);
        for (String n : new String[] { "ram0", "ram1" }) model.getBone(n).ifPresent(b -> b.setRotX(ram));
        for (String n : new String[] { "rod0", "rod1" }) model.getBone(n).ifPresent(b -> b.setPosY(rod));
        double since = sinceFire(tt);
        float recoil = 0.0F;
        if (since >= 0.0 && since < 3.0) recoil = 1.0F;
        else if (since >= 3.0 && since < 40.0) recoil = smooth(1.0 - (since - 3.0) / 37.0);
        final float rY = (float) (-Math.sin(Math.toRadians(REST_EL)) * RECOIL_PX * recoil * RECOIL_SIGN), rZ = (float) (Math.cos(Math.toRadians(REST_EL)) * RECOIL_PX * recoil * RECOIL_SIGN);
        model.getBone("barrel").ifPresent(b -> { b.setPosY(rY); b.setPosZ(rZ); });
        final float open = shutters(tt);
        model.getBone("shellb").ifPresent(b -> b.setRotX((float) Math.toRadians(100.0 * open)));
        model.getBone("shella").ifPresent(b -> b.setRotX((float) Math.toRadians(140.0 * open)));
        float c = charge01();
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean armed = hasTarget() && !dark;
        boolean fireFlash = since >= 0 && since < 6;
        for (int k = 0; k < 6; k++) {
            final boolean lit = armed && (fireFlash || c >= 0.8F + 0.2F * (5 - k) / 6.0F);
            model.getBone("ringglow" + k).ifPresent(b -> b.setHidden(!lit));
        }
        final boolean crown = armed && (fireFlash || c >= 0.8F);
        model.getBone("crownglow").ifPresent(b -> b.setHidden(!crown));
        final boolean pinch = armed && open > 0.05F;
        model.getBone("pinch").ifPresent(b -> b.setHidden(!pinch));
        final float flaps = since < 0 || since > Balance.PMORT_VENT ? 0.0F : since < 12 ? smooth(since / 12.0) : since > Balance.PMORT_VENT - 24 ? smooth((Balance.PMORT_VENT - since) / 24.0) : 1.0F;
        final float fz = (float) Math.toRadians(55.0 * flaps * FLAP_SIGN);
        model.getBone("flap0").ifPresent(b -> b.setRotZ(fz));
        model.getBone("flap1").ifPresent(b -> b.setRotZ(fz));
        model.getBone("flap2").ifPresent(b -> b.setRotZ(-fz));
        model.getBone("flap3").ifPresent(b -> b.setRotZ(-fz));
        final boolean vent = !dark && since >= 0 && since < Balance.PMORT_VENT - 30;
        model.getBone("ventglow0").ifPresent(b -> b.setHidden(!vent));
        model.getBone("ventglow1").ifPresent(b -> b.setHidden(!vent));
        final boolean charging = !dark && c > 0.02F && c < 0.999F;
        final boolean full = !dark && c >= 0.999F;
        final boolean blink = ((long) tt / 10) % 2 == 0;
        model.getBone("canglow").ifPresent(b -> b.setHidden(!(full || (charging && blink))));
        model.getBone("beacon").ifPresent(b -> b.setHidden(!(armed && charging && blink)));
        final boolean ready = armed && full && hydrogenClient() >= Balance.PMORT_H2_MB;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready));
        final boolean amber = !dark && (charging || vent);
        model.getBone("lamp_a").ifPresent(b -> b.setHidden(!amber));

    }

    int hydrogenClient() { return hydrogen.getFluidAmount(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("pmH2s", hydrogen.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("pmH2s")) hydrogen.readFromNBT(registries, tag.getCompound("pmH2s"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("pmH2s", hydrogen.writeToNBT(registries, new CompoundTag()));
        return tag;
    }
}

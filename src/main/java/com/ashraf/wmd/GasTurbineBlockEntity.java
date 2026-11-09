package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class GasTurbineBlockEntity extends ItemProcessBlockEntity implements CcAnimated {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final FluidTank fuel = new FluidTank(Balance.TURBINE_TANK_MB, fs -> fs.getFluid() == WmdFluids.LPG.source().get() || fs.getFluid() == WmdFluids.GASOLINE.source().get() || fs.getFluid() == WmdFluids.NAPHTHA.source().get());
    private final IFluidHandler fuelPort = fillOnly(fuel);
    private final Slot blades = new Slot(4, false, s -> s.is(wmd("turbine_blades")));
    private final Slot[] slots = { blades };
    private final List<FluidTank> tanks = List.of(fuel, blades.gauge);
    private final IItemHandler bladeHatch = inHandler(blades);
    private final GenBuffer out = new GenBuffer(Balance.TURBINE_BUFFER_FE, Balance.TURBINE_OUT_PER_TICK, this::setChanged);

    private int life, credit;

    protected static final int NO_BLADES = 11;

    private static final double[] MOUTH = { -2.5, 1.25, 1.0 };
    private static final double INTAKE_DEPTH = 3.0, INTAKE_KILL = 0.9;

    private static final double[] EXHAUST = { 0.125, 3.0, 1.0 };

    public GasTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.GAS_TURBINE_BE.get(), pos, state, 0);
    }

    @Override
    protected boolean process() {
        boolean ok = step();
        syncAnim();
        return ok;
    }

    private boolean step() {
        if (life <= 0) {
            if (blades.count() < 1) return fail(NO_BLADES);
            blades.take(1);
            life = Balance.TURBINE_BLADE_LIFE;
            setChanged();
        }
        if (!out.hasRoom(Balance.TURBINE_FE_PER_TICK)) return fail(OUT_FULL);
        if (credit < Balance.TURBINE_FE_PER_TICK) {
            if (fuel.getFluidAmount() < 1) return fail(NO_INPUT);
            credit += fePerMb(fuel.getFluid().getFluid());
            fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
            flowAcc += 1;
        }
        credit -= Balance.TURBINE_FE_PER_TICK;
        out.stored += Balance.TURBINE_FE_PER_TICK;
        life--;
        if (life % 200 == 0) setChanged();
        suck();
        return true;
    }

    private net.minecraft.world.phys.AABB intakeBox;
    private net.minecraft.world.phys.Vec3 mouth;

    private void suck() {
        if (level == null) return;
        if (intakeBox == null || SableBridge.isOnShip(level, worldPosition)) {
            double[] m = worldPoint(MOUTH[0], MOUTH[1], MOUTH[2]);
            double[] a = worldPoint(MOUTH[0] - INTAKE_DEPTH, MOUTH[1] - 1.5, MOUTH[2] - 1.5), b = worldPoint(MOUTH[0], MOUTH[1] + 1.5, MOUTH[2] + 1.5);
            double[] c = worldPoint(MOUTH[0] - INTAKE_DEPTH, MOUTH[1] + 1.5, MOUTH[2] + 1.5), d = worldPoint(MOUTH[0], MOUTH[1] - 1.5, MOUTH[2] - 1.5);
            mouth = new net.minecraft.world.phys.Vec3(m[0], m[1], m[2]);
            intakeBox = new net.minecraft.world.phys.AABB(a[0], a[1], a[2], b[0], b[1], b[2]).minmax(new net.minecraft.world.phys.AABB(c[0], c[1], c[2], d[0], d[1], d[2]));
        }
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, intakeBox)) {
            if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) continue;
            net.minecraft.world.phys.Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            net.minecraft.world.phys.Vec3 to = mouth.subtract(c);
            double d = to.length();
            if (d <= INTAKE_KILL) {
                e.hurt(new net.minecraft.world.damagesource.DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(INTAKE_DAMAGE)), 1000.0F);
                continue;
            }
            double pull = 0.06 + 0.14 * Math.max(0.0, 1.0 - d / (INTAKE_DEPTH + 1.0));
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(to.normalize().scale(pull)));
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
        }
    }

    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> INTAKE_DAMAGE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turbine_intake"));

    public int lifeFraction1000() { return life <= 0 ? 0 : Math.min(1000, (int) (life * 1000L / Balance.TURBINE_BLADE_LIFE)); }

    @Override protected String[] iconIds() { return new String[] { "turbine_blades" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return life <= 0 ? -1.0F : lifeFraction1000() / 1000.0F; }
    @Override protected List<FluidTank> tanks() { return tanks; }

    static int fePerMb(Fluid f) {
        if (f == WmdFluids.LPG.source().get()) return Balance.TURBINE_FE_PER_MB_LPG;
        if (f == WmdFluids.GASOLINE.source().get()) return Balance.TURBINE_FE_PER_MB_GASOLINE;
        return Balance.TURBINE_FE_PER_MB_NAPHTHA;
    }

    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.LPG.source().get(), Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("fuel", "blades"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public void empDrain() { out.stored = 0; setChanged(); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.turbine", Balance.TURBINE_FE_PER_TICK * 20, Balance.TURBINE_FE_PER_MB_LPG, Balance.TURBINE_FE_PER_MB_GASOLINE, Balance.TURBINE_FE_PER_MB_NAPHTHA),
                Component.translatable("gui.wmd.info.turbine_life", Balance.TURBINE_BLADE_LIFE / 1200),
                Component.translatable("gui.wmd.info.turbine_danger"));
    }

    private int sentLoadQ = -1, sentReason = -1;

    int loadQ() { return Math.round(10.0F * (1.0F - Mth.clamp(out.stored / (float) Balance.TURBINE_BUFFER_FE, 0.0F, 1.0F))); }

    private void syncAnim() {
        if (level == null || level.isClientSide || level.getGameTime() % 10 != 0) return;
        int q = loadQ(), r = isRunning() ? 0 : reason;
        if (q == sentLoadQ && r == sentReason) return;
        sentLoadQ = q; sentReason = r;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("gtl", loadQ());
        tag.putInt("gtr", isRunning() ? 0 : reason);
        return tag;
    }

    static final float SPD_FULL = 0.9F, SPD_CRANK = 0.11F, SPD_EASE = 0.04F, SPD_UP = 0.009F, SPD_DOWN = 0.0048F;

    static final int IGNITE_AT = 50, FLICKER = 12, STARTER_CUT = 90;
    static final float STARTER_SPD = 1.6F;

    static final float IGV_SHUT = 65.0F, IGV_FROM = 0.6F, PHI = 8.0F, ROD_LIFT = 1.7257F, IGV_SIGN = 1.0F;
    static final int NV = 12;

    private float shaft, shaftSpeed, starter, starterSpeed, igvOpen, heat, loadShown = 1.0F, loadTarget = 1.0F;
    private int seq = -1, ticks, reasonShown;

    public static void clientTickTurbine(Level level, BlockPos pos, BlockState state, GasTurbineBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        boolean run = be.isRunning();
        be.ticks++;
        if (!run) be.seq = -1;
        else if (be.seq >= 0) be.seq++;
        if (run && be.seq == IGNITE_AT) be.ignite(level);
        boolean lit = be.lit();
        float target = !run ? 0.0F : lit ? SPD_FULL : SPD_CRANK;
        if (be.ticks == 1 && run) { be.shaftSpeed = SPD_FULL; be.igvOpen = be.loadTarget; be.heat = 1.0F; }
        be.shaftSpeed += Mth.clamp((target - be.shaftSpeed) * SPD_EASE, -SPD_DOWN, SPD_UP);
        if (Math.abs(target - be.shaftSpeed) < 0.0005F) be.shaftSpeed = target;
        be.shaft = (be.shaft + be.shaftSpeed) % (Mth.TWO_PI * 64);
        boolean crank = run && be.seq >= 0 && be.seq < STARTER_CUT;
        be.starterSpeed += ((crank ? STARTER_SPD : 0.0F) - be.starterSpeed) * (crank ? 0.08F : 0.03F);
        if (!crank && be.starterSpeed < 0.002F) be.starterSpeed = 0.0F;
        be.starter = (be.starter + be.starterSpeed) % (Mth.TWO_PI * 64);
        be.loadShown += (be.loadTarget - be.loadShown) * 0.1F;
        float vt = lit && be.rotor01() > IGV_FROM ? be.loadShown : 0.0F;
        be.igvOpen += (vt - be.igvOpen) * 0.04F;
        float ht = lit ? be.rotor01() : 0.0F;
        be.heat += (ht - be.heat) * (ht > be.heat ? 0.02F : 0.008F);
        if (be.heat > 0.05F) {
            double[] h = be.worldPoint(EXHAUST[0], EXHAUST[1] + 0.8, EXHAUST[2]);
            VeilBridge.haze(h[0], h[1], h[2], 1.4, 40.0, 0.35F * be.heat);
        }
    }

    boolean lit() { return isRunning() && (seq < 0 || seq >= IGNITE_AT); }

    float rotor01() { return Mth.clamp(shaftSpeed / SPD_FULL, 0.0F, 1.0F); }

    @Override
    public float spinAngle(float partialTick) { return shaft + shaftSpeed * partialTick; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float shut = (float) Math.toRadians(IGV_SHUT * (1.0F - igvOpen));
        for (int k = 0; k < NV; k++) model.getBone("igv" + k).ifPresent(b -> b.setRotY(shut));
        model.getBone("igvring").ifPresent(b -> b.setRotX(IGV_SIGN * (float) Math.toRadians(PHI * igvOpen)));
        model.getBone("igvrod").ifPresent(b -> b.setPosY(ROD_LIFT * igvOpen));
        model.getBone("starter").ifPresent(b -> b.setRotX(starter + starterSpeed * partialTick));
        model.getBone("needle").ifPresent(b -> b.setRotZ((float) Math.toRadians(-135.0 + 270.0 * rotor01())));
        boolean dark = ClientHemp.darkAt(getBlockPos().getX() + 0.5, getBlockPos().getZ() + 0.5);
        boolean lit = lit(), flick = seq >= IGNITE_AT && seq < IGNITE_AT + FLICKER && ((long) tt) % 3 == 0;
        model.getBone("glow").ifPresent(b -> b.setHidden(dark || !lit || flick));
        model.getBone("glow1").ifPresent(b -> b.setHidden(dark || !lit || heat < 0.3F));
        boolean run = isRunning(), runLamp = lit && rotor01() > 0.9F, fast = ((long) tt / 6) % 2 == 0, slow = ((long) tt / 20) % 2 == 0;
        boolean fault = !run && (reasonShown == NO_INPUT || reasonShown == NO_BLADES);
        boolean ready = run ? !runLamp && fast : reasonShown == OUT_FULL;
        model.getBone("lamp_run").ifPresent(b -> b.setHidden(dark || !runLamp));
        model.getBone("lamp_ready").ifPresent(b -> b.setHidden(dark || !ready));
        model.getBone("lamp_fault").ifPresent(b -> b.setHidden(dark || !fault || !slow));
    }

    @Override
    protected void runStarted(Level level) {
        seq = 0;
        playOnce(level, WmdSounds.GT_STARTER.get(), Balance.SND_MECH, 0.8F);
    }

    private void ignite(Level level) {
        playOnce(level, WmdSounds.GT_IGNITE.get(), Balance.SND_MECH, 0.85F);
        double[] c = worldPoint(-0.56, 1.25, 1.0);
        VeilBridge.flash(c[0], c[1], c[2], 1.0F, 0.55F, 0.2F, 6.0F, 2.0F, 10);
        puff(level, ParticleTypes.LARGE_SMOKE, 8);
        var r = level.random;
        for (int i = 0; i < 4; i++)
            level.addParticle(ParticleTypes.FLAME, c[0] + (r.nextDouble() - 0.5) * 1.4, c[1] + (r.nextDouble() - 0.5) * 1.4, c[2] + (r.nextDouble() - 0.5) * 1.4, 0, 0.02, 0);
    }

    @Override
    protected void runStopped(Level level) { if (heat > 0.3F) puff(level, ParticleTypes.SMOKE, 6); }

    private void puff(Level level, net.minecraft.core.particles.SimpleParticleType type, int n) {
        var r = level.random;
        double[] w = worldPoint(EXHAUST[0], EXHAUST[1], EXHAUST[2]);
        for (int i = 0; i < n; i++)
            level.addParticle(type, w[0] + (r.nextDouble() - 0.5) * 0.3, w[1], w[2] + (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.03, 0.12 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.03);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.GT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(-2, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY * Math.max(0.08F, 0.15F + 0.85F * rotor01()); }
    @Override protected float soundPitch() { return 0.6F + 0.4F * rotor01(); }
    @Override protected boolean soundAlive() { return isRunning() || rotor01() > 0.12F; }
    @Override protected float levelFraction() { return fuel.getFluidAmount() / (float) Balance.TURBINE_TANK_MB; }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        long t = level.getGameTime(), key = getBlockPos().asLong();
        float n = rotor01();
        boolean lit = lit();
        if (lit) {
            double[] comb = worldPoint(-0.56, 1.25, 1.0), stack = worldPoint(EXHAUST[0], EXHAUST[1], EXHAUST[2]);
            VeilBridge.machineGlow(key, comb[0], comb[1], comb[2], 1.0F, 0.45F, 0.15F, 3.0F, 1.2F, t);
            VeilBridge.machineGlow(key * 31 + 7, stack[0], stack[1], stack[2], 1.0F, 0.5F, 0.2F, 4.0F, (1.0F + 0.25F * (float) Math.sin(t * 0.15)) * Math.max(0.2F, heat), t);
        }
        double[] m = worldPoint(MOUTH[0], MOUTH[1], MOUTH[2]);
        if (r.nextFloat() < 0.2F + 0.8F * n) for (int i = 0; i < 2; i++) {
            double[] p = worldPoint(MOUTH[0] - 0.6 - r.nextDouble() * INTAKE_DEPTH, MOUTH[1] + (r.nextDouble() - 0.5) * 2.4, MOUTH[2] + (r.nextDouble() - 0.5) * 2.4);
            double k = 0.04 + 0.1 * n + r.nextDouble() * 0.05;
            level.addParticle(ParticleTypes.CLOUD, p[0], p[1], p[2], (m[0] - p[0]) * k, (m[1] - p[1]) * k, (m[2] - p[2]) * k);
        }
        if (!lit || t % 3 != 0) return;
        double[] w = worldPoint(EXHAUST[0], EXHAUST[1], EXHAUST[2]);
        level.addParticle(ParticleTypes.SMOKE, w[0] + (r.nextDouble() - 0.5) * 0.3, w[1], w[2] + (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.01, 0.08 + 0.06 * n, (r.nextDouble() - 0.5) * 0.01);
        if (!VeilBridge.active() && heat > 0.5F && t % 6 == 0)
            level.addParticle(ParticleTypes.WHITE_ASH, w[0] + (r.nextDouble() - 0.5) * 0.4, w[1] + 0.3, w[2] + (r.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
    }

    @Override
    protected String detail() { return "fuel " + mb(fuel) + ", stored " + out.stored + " FE, blades " + (life <= 0 ? "none" : lifeFraction1000() / 10 + "%"); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 2, Direction.SOUTH) ? fuelPort : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 0, 1, 0, Direction.NORTH) ? bladeHatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 1, Direction.EAST) ? out : null; }

    @Override
    protected void saveExtra(CompoundTag tag) { tag.putInt("life", life); tag.putInt("credit", credit); tag.putInt("stored", out.stored); }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("life")) { life = tag.getInt("life"); credit = tag.getInt("credit"); out.stored = tag.getInt("stored"); }
        if (tag.contains("gtl")) { loadTarget = tag.getInt("gtl") / 10.0F; reasonShown = tag.getInt("gtr"); }
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).fluid(-1, 0, 2, Direction.SOUTH, "fuel", false).item(0, 1, 0, Direction.NORTH, false).power(2, 0, 1, Direction.EAST, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}

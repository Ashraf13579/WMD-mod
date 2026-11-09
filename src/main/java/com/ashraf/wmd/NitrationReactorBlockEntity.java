package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class NitrationReactorBlockEntity extends ItemProcessBlockEntity implements CcAnimated {

    private final FluidTank naphtha = new FluidTank(Balance.NITRO_TANK_MB, fs -> fs.getFluid() == WmdFluids.NAPHTHA.source().get());
    private final Slot dust = new Slot(64, false, s -> s.is(wmd("nitrate_dust")));
    private final Slot charge = new Slot(64, true, s -> false).chute(1, 0, 1, Direction.EAST);
    private final Slot[] slots = { dust, charge };
    private final List<FluidTank> tanks = List.of(naphtha, dust.gauge, charge.gauge);
    private final IFluidHandler naphthaPort = fillOnly(naphtha);
    private final IItemHandler in = inHandler(dust), outH = outHandler(charge);
    private int progress;

    public NitrationReactorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.NITRATION_REACTOR_BE.get(), pos, state, Balance.NITRO_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (dust.count() < Balance.NITRO_DUST_PER_CYCLE || naphtha.getFluidAmount() < Balance.NITRO_NAPHTHA_MB) return fail(NO_INPUT);
            if (!charge.fits(wmd("he_charge"), Balance.NITRO_HE_PER_CYCLE)) return fail(OUT_FULL);
            dust.take(Balance.NITRO_DUST_PER_CYCLE);
            naphtha.drain(Balance.NITRO_NAPHTHA_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 3;
        if (++progress > Balance.NITRO_CYCLE_TICKS) {
            progress = 0;
            charge.add(wmd("he_charge"), Balance.NITRO_HE_PER_CYCLE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "nitrate_dust", "he_charge" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.NITRO_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.NAPHTHA.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("naphtha", "nitrate", "he_charge"); }
    @Override protected int rateMbPerTick() { return 3; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.NITRO_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.nitration", Balance.NITRO_DUST_PER_CYCLE, Balance.NITRO_NAPHTHA_MB, Balance.NITRO_HE_PER_CYCLE, Balance.NITRO_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.NR_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 1); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected float levelFraction() { return naphtha.getFluidAmount() / (float) Balance.NITRO_TANK_MB; }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        long t = level.getGameTime();
        if (t % 12 == 0) {
            double[] w = worldPoint(0.0, 6.1, 1.0);
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.01, 0.03, (r.nextDouble() - 0.5) * 0.01);
        }
        double p = cycle01(t);
        if (lastFxP >= 0.0 && p >= lastFxP) {
            for (float s : NitrationAnim.PULSE_AT)
                if (lastFxP < s && p >= s) for (int k = 0; k < 3; k++) {
                    double[] h = worldPoint(1.0 + (r.nextDouble() - 0.5) * 0.5, 5.9, 1.0 + (r.nextDouble() - 0.5) * 0.5);
                    level.addParticle(ParticleTypes.WHITE_ASH, h[0], h[1], h[2], 0, 0.02, 0);
                }
            if (lastFxP < NitrationAnim.GATE_OPEN && p >= NitrationAnim.GATE_OPEN) {
                playOnce(level, WmdSounds.CATAPULT_LATCH.get(), Balance.SND_MECH, 0.8F);
                for (int k = 0; k < 6; k++) nox(level, 2.0F, 0.05);
            }
        }
        lastFxP = p;
        if (p > NitrationAnim.DOSE_END && p < NitrationAnim.DRAIN_TO && t % 5 == 0) nox(level, 1.2F, 0.03);
        if (t % 10 == 0) {
            double[] c = worldPoint(-1.0 + (r.nextDouble() - 0.5) * 0.4, 0.92, 1.86 + (r.nextDouble() - 0.5) * 0.4);
            level.addParticle(ParticleTypes.CLOUD, c[0], c[1], c[2], 0, 0.025, 0);
        }
    }

    private static final net.minecraft.core.particles.DustParticleOptions NOX = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.72F, 0.38F, 0.16F), 1.2F);
    private static final net.minecraft.core.particles.DustParticleOptions NOX_BIG = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.6F, 0.3F, 0.12F), 2.0F);

    private void nox(Level level, float size, double vy) {
        var r = level.random;
        double[] w = worldPoint(-1.3125 + (r.nextDouble() - 0.5) * 0.1, 5.85, 2.4125 + (r.nextDouble() - 0.5) * 0.1);
        level.addAlwaysVisibleParticle(size > 1.5F ? NOX_BIG : NOX, true, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.02, vy, (r.nextDouble() - 0.5) * 0.02);
    }

    @Override
    protected void runStopped(Level level) { lastFxP = -1.0; }

    private int syncP, syncReason;
    private boolean syncFresh;
    private double syncAt, lastCycle, animT = -1.0, lastFxP = -1.0;
    private float fanSpeed, fanAngle, liqC = -1.0F, gateC, starC, wheelC;

    private static final float FAN_FULL = 0.55F, FAN_UP = 20.0F, FAN_DOWN = 60.0F;

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("np", progress);
        tag.putInt("nr", reason);
        return tag;
    }

    double cycle01(double tt) {
        if (syncFresh) { syncAt = tt; syncFresh = false; }
        if (!isRunning()) return lastCycle;
        double p = (syncP + Math.max(0.0, tt - syncAt)) / Balance.NITRO_CYCLE_TICKS;
        lastCycle = p - Math.floor(p);
        return lastCycle;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        double dt = animT < 0.0 ? 0.0 : Math.max(0.0, Math.min(20.0, tt - animT));
        animT = tt;
        final boolean run = isRunning();
        final boolean dark = ClientHemp.darkAt(getBlockPos().getX() + 0.5, getBlockPos().getZ() + 0.5);
        final double p = cycle01(tt);
        fanSpeed += (float) (((run ? FAN_FULL : 0.0F) - fanSpeed) * (1.0 - Math.exp(-dt / (run ? FAN_UP : FAN_DOWN))));
        fanAngle = (float) ((fanAngle + fanSpeed * dt) % (Math.PI * 128.0));
        float lv = NitrationAnim.level(p) + (run ? 0.012F * (float) Math.sin(tt * 0.7) : 0.0F);
        if (liqC < 0.0F) liqC = lv;
        liqC += (float) ((lv - liqC) * Math.min(1.0, dt * 0.3));
        if (run) { gateC = NitrationAnim.gate01(p); starC = NitrationAnim.star(p); wheelC = NitrationAnim.wheel01(p); }
        else { gateC *= (float) Math.exp(-dt / 6.0); wheelC *= (float) Math.exp(-dt / 10.0); }
        final float gate = gateC, star = starC, wheel = wheelC, liq = liqC, fan = fanAngle;
        model.getBone("nrotor").ifPresent(b -> b.setRotZ(star));
        model.getBone("nwheel").ifPresent(b -> b.setRotZ(NitrationAnim.WHEEL_TURN * wheel));
        model.getBone("nliq").ifPresent(b -> b.setScaleY(Math.max(0.02F, liq)));
        model.getBone("nfan").ifPresent(b -> b.setRotY(fan));
        model.getBone("nflag").ifPresent(b -> b.setRotY((float) (Math.PI / 2.0) * gate));
        model.getBone("nslide").ifPresent(b -> b.setPosX(NitrationAnim.XS * -NitrationAnim.GATE_TRAVEL * gate));
        final float[] c = run ? NitrationAnim.charge(p) : new float[] { 0.0F, 0.0F, 0.0F };
        model.getBone("ncharge").ifPresent(b -> {
            b.setHidden(c[0] < 0.5F);
            float tilt = (float) Math.toRadians(NitrationAnim.TROUGH_TILT);
            b.setPosX(NitrationAnim.XS * c[1]);
            b.setPosY(c[2] - c[1] * (float) Math.tan(tilt));
            b.setRotZ(c[1] > 0.0F ? -NitrationAnim.TILT_SIGN * tilt : 0.0F);
        });
        final boolean waiting = !run && (syncReason == 3 || syncReason == 4);
        model.getBone("nlg").ifPresent(b -> b.setHidden(dark || !run));
        model.getBone("nla").ifPresent(b -> b.setHidden(dark || !run || !NitrationAnim.beat(p)));
        model.getBone("nlr").ifPresent(b -> b.setHidden(dark || !waiting));
    }

    @Override
    protected String detail() { return "naphtha " + mb(naphtha) + ", dust " + dust.count() + ", HE charge " + charge.count() + ", cycle " + progress * 100 / Balance.NITRO_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 1, 1, Direction.WEST) ? naphthaPort : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 1, 4, 1, Direction.UP)) return in;
        if (at(part, side, 1, 0, 1, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) {
        progress = tag.getInt("progress");
        if (tag.contains("np")) { syncP = tag.getInt("np"); syncReason = tag.getInt("nr"); syncFresh = true; }
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 1, 1, Direction.WEST, "naphtha", false).item(1, 4, 1, Direction.UP, false).item(1, 0, 1, Direction.EAST, true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}

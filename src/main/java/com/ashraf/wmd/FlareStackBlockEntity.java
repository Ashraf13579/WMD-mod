package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FlareStackBlockEntity extends ProcessUnitBlockEntity implements CcAnimated {

    static final float STEM_LIFT = 2.0F, FLAP_OPEN = 38.0F, LIP = 193.7F, FLAME_H = 44.0F;
    static final float FLAP_SIGN = -1.0F;

    private final FluidTank gas = new FluidTank(Balance.FLARE_TANK_MB, fs -> fs.getFluid() == WmdFluids.LPG.source().get());
    private final IFluidHandler gasPort = fillOnly(gas);
    private final GenBuffer out = new GenBuffer(Balance.FLARE_BUFFER_FE, Balance.FLARE_OUT_PER_TICK, this::setChanged);
    private int phase;

    public FlareStackBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FLARE_STACK_BE.get(), pos, state, 0);
    }

    @Override
    protected boolean work() {
        if (gas.getFluidAmount() < 1) return false;
        int ticksPerMb = Math.max(1, 20 / Balance.FLARE_MB_PER_SEC);
        if (!out.hasRoom(Balance.FLARE_FE_PER_MB)) return false;
        if (++phase >= ticksPerMb) {
            phase = 0;
            gas.drain(1, IFluidHandler.FluidAction.EXECUTE);
            out.stored += Balance.FLARE_FE_PER_MB;
        }
        flowAcc += 1;
        return true;
    }

    @Override protected List<FluidTank> tanks() { return List.of(gas); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.LPG.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("lpg"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public IEnergyStorage guiEnergy() { return out; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.flare_output", Balance.FLARE_FE_PER_MB * Balance.FLARE_MB_PER_SEC, Balance.FLARE_MB_PER_SEC),
                Component.translatable("gui.wmd.info.flare_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.FS_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override protected float levelFraction() { int mb = gas.getFluidAmount(); return mb <= 0 ? 0.0F : Math.max(1.0F / 32.0F, mb / (float) Balance.FLARE_TANK_MB); }

    private double lastTt = Double.NaN;
    private float flameSize, steamShown, fanSpeed, fanAngle;
    private long startTick = Long.MIN_VALUE / 2;

    boolean gasShown() { return levelShown() > 0.5F / 32.0F || isRunning(); }

    float flameTarget() { return isRunning() ? 0.3F + 0.7F * flowFraction() : 0.0F; }

    float steamTarget(long now) {
        if (!isRunning()) return 0.0F;
        float f = Math.max(0.0F, Math.min(1.0F, (flowFraction() - 0.25F) / 0.5F));
        return f * Math.max(0.0F, Math.min(1.0F, (now - startTick) / 120.0F));
    }

    static float ease(float v, float target, float upK, float downK, float dt) {
        float k = target > v ? upK : downK;
        return v + (target - v) * (1.0F - (float) Math.pow(1.0F - k, dt));
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float dt = Double.isNaN(lastTt) ? 100.0F : (float) Math.max(0.0, Math.min(5.0, tt - lastTt));
        lastTt = tt;
        boolean run = isRunning(), gasOn = gasShown();
        flameSize = ease(flameSize, flameTarget(), 0.05F, 0.07F, dt);
        steamShown = ease(steamShown, steamTarget((long) tt), 0.03F, 0.05F, dt);
        fanSpeed = ease(fanSpeed, run ? 1.0F : 0.0F, 0.02F, 0.006F, dt);
        fanAngle = (fanAngle + fanSpeed * 0.45F * dt) % (float) (Math.PI * 200.0);
        final float size = flameSize;
        final float flick = 0.93F + 0.05F * (float) Math.sin(tt * 0.9) + 0.03F * (float) Math.sin(tt * 2.3 + 1.0);
        final float wob = 0.96F + 0.04F * (float) Math.sin(tt * 1.3);
        final float wide = 0.55F + 0.45F * size;
        model.getBone("flame").ifPresent(b -> {
            b.setHidden(size < 0.02F);
            b.setScaleY(Math.max(0.01F, size * flick));
            b.setScaleX(wide * wob);
            b.setScaleZ(wide * (2.0F - wob));
            b.setRotY((float) (tt * 0.03 % (Math.PI * 2.0)));
        });
        final float lick = 0.5F + 0.5F * (float) Math.sin(tt * 0.47);
        model.getBone("tongue").ifPresent(b -> {
            b.setHidden(size < 0.2F);
            b.setScaleY(0.55F + 0.55F * lick);
            b.setRotZ(0.07F * (float) Math.sin(tt * 0.31));
        });
        final float pil = 0.78F + 0.18F * (float) Math.sin(tt * 1.9) + 0.1F * (float) Math.sin(tt * 4.7 + 2.0);
        model.getBone("pilot").ifPresent(b -> { b.setHidden(!gasOn); b.setScaleY(pil); });
        final float st = steamShown;
        model.getBone("sstem").ifPresent(b -> b.setPosY(st * STEM_LIFT));
        final float fa = fanAngle, open = fanSpeed * (0.82F + 0.12F * (float) Math.sin(tt * 2.2) + 0.06F * (float) Math.sin(tt * 7.1));
        model.getBone("cfan").ifPresent(b -> b.setRotY(fa));
        model.getBone("exflap").ifPresent(b -> b.setRotX(FLAP_SIGN * (float) Math.toRadians(FLAP_OPEN) * open));
        boolean dark = ClientHemp.darkAt(getBlockPos().getX() + 0.5, getBlockPos().getZ() + 0.5);
        boolean blink = ((long) tt) % 40 < 30;
        model.getBone("lamp").ifPresent(b -> b.setHidden(dark || !run));
        model.getBone("lampidle").ifPresent(b -> b.setHidden(dark || run || !gasOn || !blink));
    }

    private double[] tip(double above) { return worldPoint(0.0, (LIP + above) / 16.0, 1.0); }

    @Override
    protected void runStarted(Level level) {
        startTick = level.getGameTime();
        playOnce(level, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, Balance.SND_RELEASE, 0.6F + level.random.nextFloat() * 0.1F);
        var r = level.random;
        double[] w = tip(12.0);
        for (int i = 0; i < 14; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.8, w[1] + r.nextDouble() * 1.2, w[2] + (r.nextDouble() - 0.5) * 0.8,
                    (r.nextDouble() - 0.5) * 0.06, 0.08 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.06);
        for (int i = 0; i < 8; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, w[0] + (r.nextDouble() - 0.5) * 0.6, w[1], w[2] + (r.nextDouble() - 0.5) * 0.6,
                    (r.nextDouble() - 0.5) * 0.08, 0.06 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.08);
    }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        long now = level.getGameTime();
        float size = Math.max(flameSize, 0.3F), flow = flowFraction(), steam = steamShown;
        double[] top = tip(FLAME_H * size * 0.85);
        if (r.nextFloat() < 0.35F + 0.4F * size)
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, top[0] + (r.nextDouble() - 0.5) * 0.5 * size, top[1], top[2] + (r.nextDouble() - 0.5) * 0.5 * size,
                    (r.nextDouble() - 0.5) * 0.02, 0.05 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.02);
        if (r.nextFloat() < 0.05F + 0.45F * flow * (1.0F - steam))
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, top[0], top[1] + 0.6, top[2], (r.nextDouble() - 0.5) * 0.03, 0.07 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.03);
        if (now % 3 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, top[0] + (r.nextDouble() - 0.5) * 0.6, top[1] + 0.9, top[2] + (r.nextDouble() - 0.5) * 0.6, 0.0, 0.05, 0.0);
        if (steam > 0.05F && r.nextFloat() < 0.6F * steam) {
            double a = Math.toRadians(30.0 + 60.0 * r.nextInt(6));
            double[] n = worldPoint(Math.cos(a) * 7.9 / 16.0, (LIP + 2.4) / 16.0, 1.0 + Math.sin(a) * 7.9 / 16.0);
            double[] c = tip(3.0);
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, n[0], n[1], n[2], (c[0] - n[0]) * 0.08, 0.05 + r.nextDouble() * 0.03, (c[2] - n[2]) * 0.08);
        }
        if (now % 3 == 0) {
            double[] e = worldPoint(4.0 / 16.0, 30.0 / 16.0, 35.0 / 16.0);
            level.addParticle(ParticleTypes.SMOKE, e[0] + (r.nextDouble() - 0.5) * 0.1, e[1], e[2] + (r.nextDouble() - 0.5) * 0.1, 0.0, 0.06, 0.0);
        }
    }

    @Override protected String detail() { return "gas " + mb(gas) + ", stored " + out.stored + " FE"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 1, Direction.WEST) ? gasPort : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.EAST) ? out : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        out.stored = tag.getInt("stored");
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 0, 1, Direction.WEST, "lpg", false).power(1, 0, 1, Direction.EAST, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}

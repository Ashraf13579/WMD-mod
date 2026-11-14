package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class TurbofanBlockEntity extends AirEngineBlockEntity {

    public static final int OFF = 0, STARTING = 1, RUNNING = 2;
    private static final PortMap PORT_MAP = new PortMap(-1, 1, 5).power(0, 2, 2, Direction.UP, false).fluid(0, 2, 3, Direction.UP, "jet_fuel", false);
    static final int NV = 14, NP = 12;

    static final double FAN_Z = 9.0 / 16.0, NOZZLE_Z = 71.0 / 16.0;
    static final float IGV_SHUT = 55.0F, PET_IDLE = -6.0F, PET_CRUISE = 9.0F;

    private final FluidTank fuel = new FluidTank(Balance.TF_TANK, fs -> fs.getFluid() == WmdFluids.JET_FUEL.source().get());

    private int state = OFF, stateTicks, stopTicks;
    private float n1, nzYaw, nzPitch;
    private double fuelAcc, share;
    private int lastStep = STOP;

    private int cState = OFF;
    private float tN1, tYaw, tPitch, cN1, cYaw, cPitch, pYaw, pPitch, cIgv, pIgv, cPet, pPet;
    private double fanAngle, pFan;

    public TurbofanBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.TURBOFAN_BE.get(), pos, state, 0, Balance.TF_STARTER_FE * Balance.TF_START_TICKS * 2, Balance.TF_STARTER_FE * 2);
    }

    private double axisY() { return (nacelle ? 24.0 : 21.0) / 16.0; }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, axisY(), -0.45 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, axisY(), 61.0 / 16.0 }; }
    @Override protected double airflow() { return Balance.TF_AIRFLOW; }

    @Override protected boolean contrails() { return true; }
    @Override protected double hitSize() { return 2.0; }
    @Override protected boolean hotExhaust() { return true; }
    @Override protected double[] exhaustLocal() { return new double[] { 0.0, (cNacelle ? 24.0 : 21.0) / 16.0, NOZZLE_Z + 0.4 }; }
    @Override protected double heatSignature() { return afterburner ? Balance.HEAT_SIG_HOT : Balance.HEAT_SIG_TURBOFAN; }
    @Override protected double noiseDb() { return Balance.AIR_NOISE_DB_TURBOFAN; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            report(sl, share, state == RUNNING, nzYaw, nzPitch);
        }
        boolean wantRun = throttle > 1.0E-3;
        stopTicks = step == STOP && redstone == 0 ? stopTicks + 1 : 0;
        stateTicks++;
        float idle = (float) Balance.TF_IDLE_N1;
        switch (state) {
            case OFF -> {
                n1 = approach(n1, 0.0F, 1.0F / Balance.TF_SPOOL_DOWN);
                if (wantRun && dry && fuel.getFluidAmount() > 0) { state = STARTING; stateTicks = 0; }
            }
            case STARTING -> {
                boolean starter = useExtraEnergy(Balance.TF_STARTER_FE) || ShipPower.draw(sl, ship, Balance.TF_STARTER_FE);
                if (!starter || !dry || fuel.getFluidAmount() <= 0) { state = OFF; stateTicks = 0; break; }
                n1 = Math.min(idle, n1 + idle / Balance.TF_START_TICKS);
                if (n1 >= idle - 1.0E-4) { state = RUNNING; stateTicks = 0; }
            }
            default -> {
                float target = idle + (1.0F - idle) * (float) Math.max(0.0, throttle);
                float rate = (1.0F - idle) / (target > n1 ? Balance.TF_SPOOL_UP : Balance.TF_SPOOL_DOWN);
                n1 = approach(n1, target, rate);
                double thrShare = Math.max(0.0, (n1 - idle) / (1.0 - idle));
                fuelAcc += Balance.TF_FUEL_IDLE + (Balance.TF_FUEL_FULL - Balance.TF_FUEL_IDLE) * thrShare;
                int burn = (int) fuelAcc;
                if (burn > 0) {
                    fuelAcc -= burn;
                    FluidStack got = fuel.drain(burn, IFluidHandler.FluidAction.EXECUTE);
                    if (got.getAmount() < burn) { state = OFF; stateTicks = 0; }
                }
                if (!dry) { state = OFF; stateTicks = 0; }
                if (stopTicks > Balance.TF_SHUTDOWN) { state = OFF; stateTicks = 0; }
            }
        }

        double[] s = state == RUNNING ? steerLocal(true) : new double[] { 0.0, 0.0 };
        float r = (float) Balance.TF_NOZZLE_RATE * 0.05F;
        nzYaw = approach(nzYaw, (float) (s[0] * Balance.TF_NOZZLE_MAX), r);
        nzPitch = approach(nzPitch, (float) (s[1] * Balance.TF_NOZZLE_MAX), r);
        double thrust = state == RUNNING ? Balance.TF_THRUST * Math.max(0.0, (n1 - idle) / (1.0 - idle)) * airShare(1.0) : 0.0;
        share = thrust / Balance.TF_THRUST;
        double ty = Math.toRadians(nzYaw), tp = Math.toRadians(nzPitch);
        setForce(thrust * Math.cos(ty) * Math.cos(tp), thrust * Math.sin(ty), thrust * Math.sin(tp));
        if (Math.abs(step - lastStep) >= 2 && state == RUNNING && step > lastStep) spoolCue = true;
        lastStep = step;
        syncMaybe(sl.getGameTime());
        return state != OFF || n1 > 1.0E-3;
    }

    private boolean spoolCue;
    private int sentState = -1;
    private float sentN1 = -9, sentYaw = 999, sentPitch = 999;

    private void syncMaybe(long now) {
        boolean big = state != sentState || spoolCue;
        boolean small = Math.abs(n1 - sentN1) > 0.04F || Math.abs(nzYaw - sentYaw) > 1.0F || Math.abs(nzPitch - sentPitch) > 1.0F;
        if (!big && !(small && now % 4 == 0)) return;
        sentState = state; sentN1 = n1; sentYaw = nzYaw; sentPitch = nzPitch;
        syncNow();
        spoolCue = false;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 2, 2, Direction.UP) ? energy() : null; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 0, 2, 3, Direction.UP) ? fillOnly(fuel) : null; }

    private boolean cSpoolCue, cDownCue;
    private long nextBlink;

    @Override
    protected void easeTick(Level lv) {
        pYaw = cYaw; pPitch = cPitch; pFan = fanAngle; pIgv = cIgv; pPet = cPet;
        float k = (float) Balance.TF_NOZZLE_RATE / 20.0F * 1.3F;
        cYaw = approach(cYaw, tYaw, k);
        cPitch = approach(cPitch, tPitch, k);
        float idle = (float) Balance.TF_IDLE_N1;
        cN1 = approach(cN1, tN1, (tN1 > cN1 ? (cState == STARTING ? idle / Balance.TF_START_TICKS : (1.0F - idle) / Balance.TF_SPOOL_UP) : (1.0F - idle) / Balance.TF_SPOOL_DOWN) * 1.3F);

        cIgv = approach(cIgv, cN1 >= 0.6F ? 0.0F : IGV_SHUT, IGV_SHUT / 20.0F);

        float sched = Mth.clamp((cN1 - idle) / (0.7F - idle), 0.0F, 1.0F);
        cPet = approach(cPet, cState == OFF && cN1 < 0.05F ? PET_IDLE : PET_IDLE + (PET_CRUISE - PET_IDLE) * sched, 1.5F);
        fanAngle += Math.PI * 2.0 * 3.0 / 20.0 * cN1;
        if (fanAngle > Math.PI * 2000.0) { fanAngle -= Math.PI * 2000.0; pFan -= Math.PI * 2000.0; }
        if (cSpoolCue) { cSpoolCue = false; playOnce(lv, WmdSounds.TURBOFAN_SPOOL_UP.get(), Balance.SND_RELEASE, 1.0F); }
        if (cDownCue) { cDownCue = false; playOnce(lv, WmdSounds.TURBOFAN_SPOOL_DOWN.get(), Balance.SND_RELEASE, 1.0F); }
        if (cState != OFF && cN1 > 0.15F) {
            double[] c = worldPoint(0.0, (cNacelle ? 24.0 : 21.0) / 16.0, NOZZLE_Z);
            boolean abLit = cAb && AirGate.AVAILABLE;
            VeilBridge.glow(0x5446_0000_0000_0000L ^ worldPosition.asLong(), c[0], c[1], c[2], 1.0F, abLit ? 0.45F : 0.55F, abLit ? 0.1F : 0.2F, abLit ? 10.0F : 3.0F + 4.0F * cN1, abLit ? 2.2F : 0.6F + 0.8F * cN1, lv.getGameTime());
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float fan = (float) Mth.lerp(partialTick, pFan, fanAngle) * RZ;
        model.getBone("fan").ifPresent(b -> b.setRotZ(fan));
        float igv = (float) Math.toRadians(Mth.lerp(partialTick, pIgv, cIgv)) * RY;
        for (int k = 0; k < NV; k++) model.getBone("igv" + k).ifPresent(b -> b.setRotY(igv));
        float pet = (float) Math.toRadians(Mth.lerp(partialTick, pPet, cPet)) * RX;
        for (int k = 0; k < NP; k++) {
            model.getBone("pet" + k).ifPresent(b -> b.setRotX(pet));
            model.getBone("seal" + k).ifPresent(b -> b.setRotX(pet));
        }
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, pYaw, cYaw)), pitch = (float) Math.toRadians(Mth.lerp(partialTick, pPitch, cPitch));
        model.getBone("nozzle").ifPresent(b -> { b.setRotX(pitch * RX); b.setRotY(yaw * RY); });
        boolean lit = cState == RUNNING || (cState != OFF && cN1 > 0.15F);
        model.getBone("glow").ifPresent(b -> b.setHidden(!lit));
        model.getBone("ab").ifPresent(b -> b.setHidden(!(AirGate.AVAILABLE && lit && cAb)));
        boolean blink = cState == STARTING && ((long) (tt / 5.0)) % 2 == 0;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!(cState == RUNNING || blink)));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cState == OFF || cN1 < 0.15F) return;
        var r = lv.random;
        if (r.nextFloat() > 0.25F + 0.5F * cN1) return;
        double ay = (cNacelle ? 24.0 : 21.0) / 16.0;
        double[] c = worldPoint(0.0, ay, NOZZLE_Z + 0.2), a = worldPoint(0.0, ay, NOZZLE_Z + 1.2);
        double sp = 0.3 + 0.6 * cN1;
        lv.addParticle(ParticleTypes.SMOKE, c[0] + (r.nextDouble() - 0.5) * 0.4, c[1] + (r.nextDouble() - 0.5) * 0.4, c[2] + (r.nextDouble() - 0.5) * 0.4,
                (a[0] - c[0]) * sp, (a[1] - c[1]) * sp, (a[2] - c[2]) * sp);
    }

    @Override protected void runStarted(Level lv) { playOnce(lv, WmdSounds.TURBOFAN_START.get(), Balance.SND_RELEASE, 1.0F); }
    @Override protected SoundEvent runSound() { return WmdSounds.TURBOFAN_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_GIANT * (0.35F + 0.65F * cN1); }
    @Override protected float soundPitch() { return 0.8F + 0.4F * cN1; }
    @Override protected boolean soundAlive() { return cState != OFF || cN1 > 0.05F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 2); }

    @Override protected List<FluidTank> tanks() { return List.of(fuel); }

    public int fuelMb() { return fuel.getFluidAmount(); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.JET_FUEL.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("jet_fuel"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.tf_use", Balance.TF_STARTER_FE * 20, Math.round(Balance.TF_FUEL_FULL * 20 * 10) / 10.0),
                Component.translatable("gui.wmd.screw.order", Component.translatable("gui.wmd.prop.step" + cStep), sourceName(cSource)),
                Component.translatable("gui.wmd.tf.state" + cState, Math.round(cN1 * 100.0F), Math.round(cYaw), Math.round(cPitch)),
                Component.translatable(!SableBridge.loaded() ? "gui.wmd.screw.no_sable" : "gui.wmd.air.air_rule"));
    }

    @Override protected String detail() { return "state " + state + ", N1 " + Math.round(n1 * 100) + "%, nozzle " + Math.round(nzYaw) + " / " + Math.round(nzPitch) + ", fuel " + fuel.getFluidAmount() + (dry ? "" : " (intake wet)"); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        tag.putInt("tfStop", stopTicks);
    }

    private void put(CompoundTag tag) {
        tag.putInt("tfState", state);
        tag.putFloat("tfN1", n1);
        tag.putFloat("tfYaw", nzYaw);
        tag.putFloat("tfPitch", nzPitch);
        if (spoolCue) tag.putBoolean("tfCue", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int st = Mth.clamp(tag.getInt("tfState"), OFF, RUNNING);
        float n = Mth.clamp(tag.getFloat("tfN1"), 0.0F, 1.0F);
        float lim = (float) Balance.TF_NOZZLE_MAX;
        float ya = Mth.clamp(tag.getFloat("tfYaw"), -lim, lim), pi = Mth.clamp(tag.getFloat("tfPitch"), -lim, lim);
        if (level != null && level.isClientSide) {
            if (cState == RUNNING && st == OFF) cDownCue = true;
            cState = st; tN1 = n; tYaw = ya; tPitch = pi;
            if (tag.getBoolean("tfCue")) cSpoolCue = true;
            return;
        }
        state = st == STARTING ? OFF : st; n1 = n; nzYaw = ya; nzPitch = pi; stopTicks = tag.getInt("tfStop");
        cState = state; tN1 = cN1 = n; tYaw = cYaw = pYaw = ya; tPitch = cPitch = pPitch = pi;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}

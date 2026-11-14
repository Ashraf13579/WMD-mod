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
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DuctedFanBlockEntity extends AirEngineBlockEntity {

    private static final PortMap PORT_MAP = new PortMap(0, 1, 3).power(0, 1, 1, Direction.UP, false);

    static final double AX = 0.5, FAN_Z = 9.0 / 16.0, EXIT_Z = 38.6 / 16.0;

    private float spool, vYaw, vPitch, vRoll;
    private boolean active;
    private double share;

    private float tSpool, tYaw, tPitch, tRoll, cSpool, cYaw, cPitch, cRoll, pYaw, pPitch, pRoll;
    private double fanAngle, pFan;
    private boolean cActive;

    public DuctedFanBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.DUCTED_FAN_BE.get(), pos, state, Balance.EDF_FE, (Balance.EDF_FE + Balance.EDF_FE_LOAD) * 400, (Balance.EDF_FE + Balance.EDF_FE_LOAD) * 10);
    }

    private double axisY() { return (nacelle ? 16.0 : 13.0) / 16.0; }

    @Override protected double[] intakeLocal() { return new double[] { AX, axisY(), -0.45 }; }
    @Override protected double[] thrustLocal() { return new double[] { AX, axisY(), FAN_Z }; }
    @Override protected double airflow() { return Balance.EDF_AIRFLOW; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        tickAir(sl);
        boolean wantsTurn = Math.abs(throttle) > 1.0E-3;
        boolean centred = Math.abs(vYaw) < 0.5F && Math.abs(vPitch) < 0.5F && Math.abs(vRoll) < 0.5F;
        if (!wantsTurn && Math.abs(spool) < 1.0E-3 && centred && Math.abs(rudderOrd) < 0.01F && Math.abs(planesOrd) < 0.01F && Math.abs(rollOrd) < 0.01F) {
            spool = 0.0F;
            active = false;
            after(sl);
            return false;
        }
        active = true;
        float target = (float) (wantsTurn && useLoad() ? throttle : 0.0);
        float rate = 1.0F / (Math.abs(target) > Math.abs(spool) ? Balance.EDF_SPOOL_UP : Balance.EDF_SPOOL_DOWN);
        spool = approach(spool, target, rate);
        steer(0.05F);
        after(sl);
        return true;
    }

    private boolean useLoad() {
        int fe = (int) Math.round(Balance.EDF_FE_LOAD * Math.abs(throttle));
        return fe <= 0 || useExtraEnergy(fe) || (getLevel() instanceof ServerLevel sl && ShipPower.draw(sl, ship, fe));
    }

    @Override
    protected void idle() {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        orders(sl);
        tickAir(sl);
        if (Math.abs(spool) > 1.0E-3 && ShipPower.draw(sl, ship, Balance.EDF_FE)) {
            float target = (float) (Math.abs(throttle) > 1.0E-3 && ShipPower.draw(sl, ship, (int) Math.round(Balance.EDF_FE_LOAD * Math.abs(throttle))) ? throttle : 0.0);
            spool = approach(spool, target, 1.0F / (Math.abs(target) > Math.abs(spool) ? Balance.EDF_SPOOL_UP : Balance.EDF_SPOOL_DOWN));
            steer(0.05F);
            active = true;
        } else {
            spool = approach(spool, 0.0F, 1.0F / Balance.EDF_COAST);
            active = Math.abs(spool) > 1.0E-3;
        }
        after(sl);
    }

    private void steer(float dt) {
        double[] s = steerLocal(true);
        float yawT = (float) (s[0] * Balance.EDF_VANE_MAX), pitchT = (float) (s[1] * Balance.EDF_VANE_MAX);
        float rollT = (float) (rollSide == 0 ? rollOrd * Balance.EDF_VANE_ROLL : 0.0);
        float r = (float) Balance.EDF_VANE_RATE * dt;
        vYaw = approach(vYaw, yawT, r);
        vPitch = approach(vPitch, pitchT, r);
        vRoll = approach(vRoll, rollT, r);
    }

    private void tickAir(ServerLevel sl) {
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            report(sl, share, active && Math.abs(spool) > 0.2F, vYaw, vPitch);
        }
    }

    private void after(ServerLevel sl) {
        double thrust = Balance.EDF_THRUST * spool * (spool < 0.0F ? 0.5 : 1.0) * airShare(spool);
        double mag = Math.abs(thrust) * Balance.EDF_VANE_SIDE;
        share = thrust / Balance.EDF_THRUST;
        setForce(thrust, mag * Math.sin(Math.toRadians(vYaw)), mag * Math.sin(Math.toRadians(vPitch)));
        syncMaybe(sl.getGameTime());
    }

    private float sentSpool = -9, sentYaw = 999, sentPitch = 999, sentRoll = 999;
    private boolean sentActive;

    private void syncMaybe(long now) {
        boolean big = active != sentActive || (Math.abs(spool) < 0.01F) != (Math.abs(sentSpool) < 0.01F) || (Math.abs(spool) > 0.99F) != (Math.abs(sentSpool) > 0.99F);
        boolean small = Math.abs(spool - sentSpool) > 0.08F || Math.abs(vYaw - sentYaw) > 2.0F || Math.abs(vPitch - sentPitch) > 2.0F || Math.abs(vRoll - sentRoll) > 2.0F;
        if (!big && !(small && now % 4 == 0)) return;
        sentActive = active; sentSpool = spool; sentYaw = vYaw; sentPitch = vPitch; sentRoll = vRoll;
        syncNow();
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 1, 1, Direction.UP) ? energy() : null; }

    @Override
    protected void easeTick(Level lv) {
        pYaw = cYaw; pPitch = cPitch; pRoll = cRoll; pFan = fanAngle;
        float k = (float) Balance.EDF_VANE_RATE / 20.0F * 1.3F;
        cYaw = approach(cYaw, tYaw, k);
        cPitch = approach(cPitch, tPitch, k);
        cRoll = approach(cRoll, tRoll, k);
        float up = 1.0F / Balance.EDF_SPOOL_UP * 1.3F, down = 1.0F / Balance.EDF_SPOOL_DOWN * 1.3F;
        cSpool = Math.abs(tSpool) > Math.abs(cSpool) ? approach(cSpool, tSpool, up) : approach(cSpool, tSpool, down);
        fanAngle += Math.PI * 2.0 * 4.0 / 20.0 * ease(cSpool);
        if (Math.abs(fanAngle) > Math.PI * 2000.0) { double d = Math.signum(fanAngle) * Math.PI * 2000.0; fanAngle -= d; pFan -= d; }
    }

    private static float ease(float s) { float a = Math.abs(s); return Math.signum(s) * a * a * (3.0F - 2.0F * a); }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float fan = (float) Mth.lerp(partialTick, pFan, fanAngle) * RZ;
        model.getBone("fan").ifPresent(b -> b.setRotZ(fan));
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, pYaw, cYaw)), pitch = (float) Math.toRadians(Mth.lerp(partialTick, pPitch, cPitch));
        float roll = (float) Math.toRadians(Mth.lerp(partialTick, pRoll, cRoll));

        float lim = (float) Math.toRadians(Balance.EDF_VANE_MAX);
        float u = Mth.clamp(yaw + roll * 0.5F, -lim, lim), d = Mth.clamp(yaw - roll * 0.5F, -lim, lim);
        float l = Mth.clamp(pitch + roll, -lim, lim), r = Mth.clamp(pitch - roll, -lim, lim);
        model.getBone("vane_u").ifPresent(b -> b.setRotY(u * RY));
        model.getBone("vane_d").ifPresent(b -> b.setRotY(d * RY));
        model.getBone("vane_l").ifPresent(b -> b.setRotX(l * RX));
        model.getBone("vane_r").ifPresent(b -> b.setRotX(r * RX));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!cActive));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (Math.abs(cSpool) < 0.4F || lv.random.nextFloat() > Math.abs(cSpool) * 0.5F) return;
        var r = lv.random;
        double ay = (cNacelle ? 16.0 : 13.0) / 16.0, sgn = cSpool >= 0.0F ? 1.0 : -1.0;
        double[] c = worldPoint(AX, ay, sgn > 0 ? EXIT_Z + 0.2 : -0.6), a = worldPoint(AX, ay, sgn > 0 ? EXIT_Z + 1.2 : -1.6);
        double sp = 0.25 + 0.3 * Math.abs(cSpool);
        lv.addParticle(ParticleTypes.WHITE_ASH, c[0] + (r.nextDouble() - 0.5) * 0.5, c[1] + (r.nextDouble() - 0.5) * 0.5, c[2] + (r.nextDouble() - 0.5) * 0.5,
                (a[0] - c[0]) * sp, (a[1] - c[1]) * sp, (a[2] - c[2]) * sp);
    }

    @Override protected void runStarted(Level lv) { playOnce(lv, WmdSounds.DUCTED_FAN_START.get(), Balance.SND_MECH, 1.0F); }
    @Override protected SoundEvent runSound() { return WmdSounds.DUCTED_FAN_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE * (0.3F + 0.7F * Math.abs(cSpool)); }
    @Override protected float soundPitch() { return 0.7F + 0.6F * Math.abs(cSpool); }
    @Override protected boolean soundAlive() { return cActive || Math.abs(cSpool) > 0.05F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.edf_use", Balance.EDF_FE * 20, (Balance.EDF_FE + Balance.EDF_FE_LOAD) * 20),
                Component.translatable("gui.wmd.screw.order", Component.translatable("gui.wmd.prop.step" + cStep), sourceName(cSource)),
                Component.translatable("gui.wmd.edf.state", Math.round(cSpool * 100.0F), Math.round(cYaw), Math.round(cPitch)),
                Component.translatable(!SableBridge.loaded() ? "gui.wmd.screw.no_sable" : "gui.wmd.air.air_rule"));
    }

    @Override protected String detail() { return "step " + step + ", fan " + Math.round(spool * 100) + "%, vanes " + Math.round(vYaw) + " / " + Math.round(vPitch) + (dry ? "" : " (intake wet)"); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
    }

    private void put(CompoundTag tag) {
        tag.putFloat("edSpool", spool);
        tag.putFloat("edYaw", vYaw);
        tag.putFloat("edPitch", vPitch);
        tag.putFloat("edRoll", vRoll);
        tag.putBoolean("edAct", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        float sp = Mth.clamp(tag.getFloat("edSpool"), -1.0F, 1.0F);
        float lim = (float) Balance.EDF_VANE_MAX;
        float ya = Mth.clamp(tag.getFloat("edYaw"), -lim, lim), pi = Mth.clamp(tag.getFloat("edPitch"), -lim, lim), ro = Mth.clamp(tag.getFloat("edRoll"), -lim, lim);
        boolean act = tag.getBoolean("edAct");
        if (level != null && level.isClientSide) { tSpool = sp; tYaw = ya; tPitch = pi; tRoll = ro; cActive = act; return; }
        spool = sp; vYaw = ya; vPitch = pi; vRoll = ro; active = act;
        tSpool = cSpool = sp; tYaw = cYaw = pYaw = ya; tPitch = cPitch = pPitch = pi; tRoll = cRoll = pRoll = ro; cActive = act;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}

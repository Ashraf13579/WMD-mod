package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class AzimuthPodBlockEntity extends P6DriveBlockEntity {

    static final float HEADING_SIGN = -1.0F;

    static final double POD_X = 0.5, POD_Y = -38.0 / 16.0, POD_Z = 26.5 / 16.0, AXIS_Z = 0.5;

    private float heading;
    private float tHeading, cHeading, pHeading;
    private float sentHeading = 999;
    private boolean powered = true;

    public AzimuthPodBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.AZIMUTH_POD_BE.get(), pos, state, Balance.AZ_FE, (Balance.AZ_FE + Balance.AZ_FE_LOAD) * 200, (Balance.AZ_FE + Balance.AZ_FE_LOAD) * 4);
    }

    @Override protected double[] intakeLocal() { return new double[] { POD_X, POD_Y, AXIS_Z }; }
    @Override protected double[] thrustLocal() { return new double[] { POD_X, POD_Y, AXIS_Z }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 2.0; }
    @Override protected int spoolUp() { return 40; }
    @Override protected int spoolDown() { return 60; }
    @Override protected double spinRps() { return 4.0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        powered = true;
        orders(sl);
        if (beat(sl)) {
            airBeat(sl);
            report(sl, spool * Math.signum(throttle), spool > 0.2F, heading, 0.0);
        }
        double lx = steerLocal(false)[0];
        float target = (float) (lx * Balance.AZ_STEER_MAX) + (throttle < -0.02 ? 180.0F : 0.0F);
        heading = slew(heading, target, (float) Balance.AZ_SLEW / 20.0F);
        boolean load = Math.abs(throttle) < 0.02 || useExtraEnergy((int) Math.round(Balance.AZ_FE_LOAD * Math.min(1.0, Math.abs(throttle))));
        spoolTo(load && damageStage() < 3 && ship != null ? (float) Math.min(1.0, Math.abs(throttle)) : 0.0F);
        double t = ship != null && !dry ? Balance.AZ_THRUST * spool : 0.0;
        double rad = Math.toRadians(heading);
        setForce(t * Math.cos(rad), t * Math.sin(rad), 0.0);
        long now = sl.getGameTime();
        if (now % 4 == 0 && Math.abs(Mth.wrapDegrees(heading - sentHeading)) > 1.5F) { sentHeading = heading; syncNow(); }
        else syncSpool(now);
        return spool > 1.0E-3F || Math.abs(throttle) > 0.02 || Math.abs(Mth.wrapDegrees(heading - target)) > 0.5F;
    }

    @Override
    protected void idle() {
        powered = false;
        super.idle();
    }

    static float slew(float cur, float target, float max) {
        float d = Mth.wrapDegrees(target - cur);
        return Mth.wrapDegrees(cur + Mth.clamp(d, -max, max));
    }

    @Override
    public Component status() {
        String k = !powered ? "message.wmd.p6.no_power" : spool > 0.05F ? (dry ? "message.wmd.azimuth_pod.dry" : "message.wmd.azimuth_pod.on") : "message.wmd.azimuth_pod.off";
        return Component.translatable("block.wmd.azimuth_pod").append(": ").append(Component.translatable(k, Math.round(spool * 100.0F), Math.round(heading)));
    }

    @Override protected String detail() { return "spool " + Math.round(spool * 100) + "%, heading " + Math.round(heading) + ", wet " + !dry; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.UP) ? energy() : null; }

    @Override
    protected void clientExtra(Level lv) {
        pHeading = cHeading;
        cHeading = slew(cHeading, tHeading, (float) Balance.AZ_SLEW / 20.0F * 1.3F);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float h = pHeading + Mth.wrapDegrees(cHeading - pHeading) * pt;
        float az = (float) Math.toRadians(h) * RY * HEADING_SIGN, a = spinAt(pt) * RZ;
        model.getBone("azi").ifPresent(b -> b.setRotY(az));
        model.getBone("aprop").ifPresent(b -> b.setRotZ(a));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.2F || lv.random.nextFloat() > cSpool * 0.7F) return;
        double rad = Math.toRadians(cHeading);
        double r = POD_Z + 0.6 - AXIS_Z;
        double[] w = worldPoint(POD_X - Math.sin(rad) * r, POD_Y, AXIS_Z + Math.cos(rad) * r);
        lv.addParticle(ParticleTypes.BUBBLE, w[0] + (lv.random.nextDouble() - 0.5), w[1] + (lv.random.nextDouble() - 0.5), w[2] + (lv.random.nextDouble() - 0.5), 0.0, 0.02, 0.0);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.AZIMUTH_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }

    @Override protected void putExtra(CompoundTag tag) { tag.putFloat("azHeading", heading); }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        float h = Mth.wrapDegrees(tag.getFloat("azHeading"));
        if (client) tHeading = h; else heading = h;
    }
}

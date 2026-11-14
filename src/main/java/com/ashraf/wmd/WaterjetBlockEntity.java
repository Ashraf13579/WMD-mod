package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class WaterjetBlockEntity extends P6DriveBlockEntity {

    static final float STEER_SIGN = 1.0F;

    private float steer, bucket;
    private float tSteer, tBucket, cSteer, cBucket, pSteer, pBucket;
    private float sentSteer = 999, sentBucket = 999;

    public WaterjetBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.WATERJET_BE.get(), pos, state, 0, 1, 0);
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, -0.2, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, 2.25 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 1.0; }
    @Override protected int spoolUp() { return 30; }
    @Override protected int spoolDown() { return 50; }
    @Override protected double spinRps() { return 12.0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if (beat(sl)) {
            airBeat(sl);
            report(sl, spool * Math.signum(throttle), spool > 0.2F, steer, 0.0);
        }
        boolean wet = ship != null && !dry;
        float drive = (float) ShipTurbineBlockEntity.drive(sl, ship);
        spoolTo(damageStage() < 3 ? drive * (float) Math.min(1.0, Math.abs(throttle)) : 0.0F);
        double lx = steerLocal(false)[0];
        steer = approach(steer, (float) (lx * Balance.WJ_STEER_MAX), (float) Balance.WJ_STEER_RATE / 20.0F);
        bucket = approach(bucket, throttle < -0.02 ? 70.0F : 0.0F, 70.0F / 20.0F);
        double t = wet ? Balance.WJ_THRUST * spool : 0.0;
        double along = bucket > 35.0F ? -t * Balance.WJ_ASTERN_SHARE : t;
        double rad = Math.toRadians(steer);
        setForce(along * Math.cos(rad), Math.abs(along) * Math.sin(rad), 0.0);
        long now = sl.getGameTime();
        if (now % 4 == 0 && (Math.abs(steer - sentSteer) > 1.0F || Math.abs(bucket - sentBucket) > 3.0F)) { sentSteer = steer; sentBucket = bucket; syncNow(); }
        else syncSpool(now);
        return spool > 1.0E-3F || Math.abs(steer) > 0.1F || bucket > 0.1F;
    }

    @Override
    public Component status() {
        String k = spool > 0.05F ? (dry ? "message.wmd.waterjet.dry" : bucket > 35.0F ? "message.wmd.waterjet.astern" : "message.wmd.waterjet.ahead")
                : "message.wmd.waterjet.no_drive";
        return Component.translatable("block.wmd.waterjet").append(": ").append(Component.translatable(k, Math.round(spool * 100.0F), Math.round(steer)));
    }

    @Override protected String detail() { return "spool " + Math.round(spool * 100) + "%, steer " + Math.round(steer) + ", bucket " + Math.round(bucket) + ", wet " + !dry; }

    @Override
    protected void clientExtra(Level lv) {
        pSteer = cSteer; pBucket = cBucket;
        cSteer = approach(cSteer, tSteer, (float) Balance.WJ_STEER_RATE / 20.0F * 1.3F);
        cBucket = approach(cBucket, tBucket, 70.0F / 20.0F * 1.3F);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = spinAt(pt) * RZ;
        float s = (float) Math.toRadians(Mth.lerp(pt, pSteer, cSteer)) * RY * STEER_SIGN, b = (float) Math.toRadians(Mth.lerp(pt, pBucket, cBucket)) * RX;
        model.getBone("shaft").ifPresent(x -> x.setRotZ(a));
        model.getBone("imp").ifPresent(x -> x.setRotZ(a));
        model.getBone("steer").ifPresent(x -> x.setRotY(s));
        model.getBone("bucket").ifPresent(x -> x.setRotX(b));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.15F || lv.random.nextFloat() > cSpool) return;
        boolean astern = cBucket > 35.0F;
        double[] w = worldPoint(0.0, 0.5, astern ? 2.55 : 2.6);
        double[] d = worldPoint(0.0, 0.5, astern ? 1.6 : 3.6);
        double k = 0.35 * cSpool;
        lv.addParticle(ParticleTypes.SPLASH, w[0], w[1], w[2], (d[0] - w[0]) * k, 0.05, (d[2] - w[2]) * k);
        if (lv.random.nextInt(3) == 0) lv.addParticle(ParticleTypes.BUBBLE, w[0], w[1], w[2], (d[0] - w[0]) * k, 0.0, (d[2] - w[2]) * k);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.WATERJET_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }

    @Override
    protected void putExtra(CompoundTag tag) {
        tag.putFloat("wjSteer", steer);
        tag.putFloat("wjBucket", bucket);
    }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        float s = Mth.clamp(tag.getFloat("wjSteer"), -90.0F, 90.0F), b = Mth.clamp(tag.getFloat("wjBucket"), 0.0F, 70.0F);
        if (client) { tSteer = s; tBucket = b; } else { steer = s; bucket = b; }
    }
}

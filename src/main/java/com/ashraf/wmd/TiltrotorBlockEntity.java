package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TiltrotorBlockEntity extends P6DriveBlockEntity {

    private float tilt;
    private float tTilt, cTilt, pTilt;
    private boolean hoverOn, turning;
    private Vec3 upPrev;

    public TiltrotorBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.TILTROTOR_BE.get(), pos, state, 0, 1, 0);
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 0.9, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, -0.4 }; }
    @Override protected double airflow() { return Balance.TR_AIRFLOW; }
    @Override protected double hitSize() { return 2.0; }
    @Override protected double heatSignature() { return Balance.HEAT_SIG_COLD; }
    @Override protected int spoolUp() { return Balance.TR_SPOOL_UP; }
    @Override protected int spoolDown() { return Balance.TR_SPOOL_DOWN; }
    @Override protected double spinRps() { return 5.0; }

    float tiltShown(float pt) { return Mth.lerp(pt, pTilt, cTilt); }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if (beat(sl)) {
            airBeat(sl);
            report(sl, spool, spool > 0.2F, rudderOrd, tilt);
        }
        hoverOn = ShipModes.hover(ship);
        tilt = approach(tilt, hoverOn ? 90.0F : 0.0F, 90.0F / Balance.TR_TILT_TICKS);
        boolean want = Math.abs(throttle) > 0.02 && damageStage() < 3 && ship != null;
        turning = want && ShipPower.draw(sl, ship, Balance.TR_FE);
        spoolTo(turning ? 1.0F : 0.0F);
        push(sl);
        long now = sl.getGameTime();
        if (now % 4 == 0 && Math.abs(tilt - tTilt) > 1.0F) { tTilt = tilt; syncNow(); }
        else syncSpool(now);
        return spool > 1.0E-3F || tilt > 0.5F;
    }

    private void push(ServerLevel sl) {
        double t = Balance.TR_THRUST * spool * Mth.clamp(throttle, 0.0, 1.0);
        double rad = Math.toRadians(tilt);
        if (tilt < Balance.TR_HOVER_DEG) { upPrev = null; setForce(t * Math.cos(rad) * airShare(t), 0.0, 0.0); return; }
        double lift = t * Math.sin(rad) * pressure * damageShare() * devThrustScale, fwd = t * Math.cos(rad) * airShare(t) * damageShare() * devThrustScale;
        Vec3 up = shipUp(sl);
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 com = info == null ? null : SableBridge.toShip(sl, info, info.worldCenter());
        if (ship == null || !dry || lift < 1.0E-3 || up == null || com == null) { force = null; return; }
        Vec3 du = upPrev == null ? Vec3.ZERO : up.subtract(upPrev).scale(20.0);
        upPrev = up;
        double ox = -Balance.LIFT_LEVEL_K * up.x - Balance.LIFT_LEVEL_D * du.x, oz = -Balance.LIFT_LEVEL_K * up.z - Balance.LIFT_LEVEL_D * du.z;
        double len = Math.sqrt(ox * ox + oz * oz);
        if (len > Balance.LIFT_LEVEL_MAX) { ox *= Balance.LIFT_LEVEL_MAX / len; oz *= Balance.LIFT_LEVEL_MAX / len; }
        Vec3 f = unit(front()).scale(fwd).add(0.0, lift, 0.0);
        force = new double[] { com.x + ox, com.y, com.z + oz, f.x, f.y, f.z };
    }

    @Override
    public Component status() {
        String k = spool > 0.05F ? (tilt >= Balance.TR_HOVER_DEG ? "message.wmd.tiltrotor.hover" : "message.wmd.tiltrotor.cruise")
                : ship != null && Math.abs(throttle) > 0.02 && !turning ? "message.wmd.p6.no_core" : "message.wmd.tiltrotor.off";
        return Component.translatable("block.wmd.tiltrotor").append(": ").append(Component.translatable(k, Math.round(spool * 100.0F), Math.round(tilt)));
    }

    @Override protected String detail() { return "spool " + Math.round(spool * 100) + "%, tilt " + Math.round(tilt) + ", hover " + hoverOn; }

    @Override
    protected void clientExtra(Level lv) {
        pTilt = cTilt;
        cTilt = approach(cTilt, tTilt, 90.0F / Balance.TR_TILT_TICKS * 1.3F);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float tl = (float) Math.toRadians(tiltShown(pt)) * RX, a = spinAt(pt) * RZ;
        model.getBone("housing_nacelle").ifPresent(b -> b.setRotX(tl));
        model.getBone("trotor").ifPresent(b -> b.setRotZ(a));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.3F || cTilt < Balance.TR_HOVER_DEG) return;
        double[] c = worldPoint(0.0, 0.5, 0.5);
        AirFx.downwash(lv, c[0], c[1], c[2], cSpool, 2.5);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.TILTROTOR_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }

    @Override protected void putExtra(CompoundTag tag) { tag.putFloat("trTilt", tilt); }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        float tl = Mth.clamp(tag.getFloat("trTilt"), 0.0F, 90.0F);
        if (client) tTilt = tl; else tilt = tl;
    }
}

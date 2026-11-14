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

public class TurbopropBlockEntity extends P6DriveBlockEntity {

    static final int[] MODE_BLADES = { 3, 4, 6, 4, 6 };
    static final int MODES = MODE_BLADES.length;

    private int mode = 1;
    private int cMode = 1;
    private boolean turning;

    public TurbopropBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.TURBOPROP_BE.get(), pos, state, 0, 1, 0);
    }

    int blades() { return MODE_BLADES[level != null && level.isClientSide ? cMode : mode]; }
    boolean contra() { return (level != null && level.isClientSide ? cMode : mode) >= 3; }

    private static double bladeShare(int n) {
        for (int i = 0; i < Balance.TP_BLADES.length; i++) if (Balance.TP_BLADES[i] == n) return Balance.TP_BLADE_SHARE[i];
        return 1.0;
    }

    String cycleBlades() {
        if (spool > 0.05F) return "message.wmd.turboprop.stop_first";
        mode = (mode + 1) % MODES;
        syncNow();
        return null;
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 0.3, 0.2 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, -0.75 }; }
    @Override protected double airflow() { return Balance.TP_AIRFLOW; }
    @Override protected double hitSize() { return 1.5; }
    @Override protected double heatSignature() { return (Balance.HEAT_SIG_COLD + Balance.HEAT_SIG_TURBOFAN) * 0.5; }
    @Override protected boolean hotExhaust() { return true; }
    @Override protected double[] exhaustLocal() { return new double[] { 0.45, 0.55, 0.9 }; }
    @Override protected int spoolUp() { return Balance.TP_SPOOL_UP; }
    @Override protected int spoolDown() { return Balance.TP_SPOOL_DOWN; }
    @Override protected double spinRps() { return 14.0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if (beat(sl)) {
            airBeat(sl);
            report(sl, spool * Math.signum(throttle), spool > 0.2F, rudderOrd, 0.0);
        }
        int fe = (int) Math.round(Balance.TP_FE * (contra() ? Balance.TP_CONTRA_FE : 1.0));
        boolean want = Math.abs(throttle) > 0.02 && damageStage() < 3 && ship != null;
        turning = want && ShipPower.draw(sl, ship, fe);
        spoolTo(turning ? 1.0F : 0.0F);
        double t = Balance.TP_THRUST * bladeShare(blades()) * (contra() ? Balance.TP_CONTRA : 1.0) * spool * throttle;
        setForce(t * airShare(t), 0.0, 0.0);
        syncSpool(sl.getGameTime());
        return spool > 1.0E-3F;
    }

    @Override
    public Component status() {
        String k = spool > 0.05F ? "message.wmd.turboprop.on" : ship != null && Math.abs(throttle) > 0.02 && !turning ? "message.wmd.p6.no_core" : "message.wmd.turboprop.off";
        return Component.translatable("block.wmd.turboprop").append(": ").append(Component.translatable(k, Math.round(spool * 100.0F), blades(), Component.translatable(contra() ? "message.wmd.turboprop.contra" : "message.wmd.turboprop.single")));
    }

    @Override protected String detail() { return "spool " + Math.round(spool * 100) + "%, blades " + blades() + (contra() ? " contra" : ""); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = spinAt(pt) * RZ;
        model.getBone("prop").ifPresent(b -> b.setRotZ(a));
        model.getBone("prop2").ifPresent(b -> b.setRotZ(-a));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.3F || lv.random.nextFloat() > cSpool * 0.3F) return;
        double[] w = worldPoint(0.45, 0.55, 0.9);
        lv.addParticle(ParticleTypes.SMOKE, w[0], w[1], w[2], 0.0, 0.03, 0.0);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.TURBOPROP_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }

    @Override protected void putExtra(CompoundTag tag) { tag.putInt("tpMode", mode); }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        int m = Mth.clamp(tag.contains("tpMode") ? tag.getInt("tpMode") : 1, 0, MODES - 1);
        if (client) cMode = m; else mode = m;
    }
}

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

public class BowThrusterBlockEntity extends P6DriveBlockEntity {

    static final double SIDE = 1.0;

    private float push;
    private int cSign = 1;
    private boolean powered = true;

    public BowThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.BOW_THRUSTER_BE.get(), pos, state, Balance.BOW_FE, (Balance.BOW_FE + Balance.BOW_FE_LOAD) * 200, (Balance.BOW_FE + Balance.BOW_FE_LOAD) * 4);
    }

    @Override protected double[] intakeLocal() { return new double[] { 1.0, 0.44, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.44, 0.0 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 0.8; }
    @Override protected int spoolUp() { return Balance.BOW_SPOOL; }
    @Override protected int spoolDown() { return Balance.BOW_SPOOL; }
    @Override protected double spinRps() { return 10.0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        powered = true;
        orders(sl);
        if (beat(sl)) {
            airBeat(sl);
            report(sl, 0.0, spool > 0.2F, 0.0, 0.0);
        }
        float want = ship == null || damageStage() >= 3 ? 0.0F : ShipControl.bow(sl, ship);
        if (Math.abs(want) > 0.01F && !useExtraEnergy((int) Math.round(Balance.BOW_FE_LOAD * Math.abs(want)))) want = 0.0F;
        int before = push > 0.0F ? 1 : push < 0.0F ? -1 : 0;
        push = approach(push, want, 1.0F / Balance.BOW_SPOOL);
        spool = Math.abs(push);
        double t = ship != null && !dry ? SIDE * Balance.BOW_THRUST * push : 0.0;
        setForce(0.0, t, 0.0);
        int after = push > 0.0F ? 1 : push < 0.0F ? -1 : 0;
        if (after != before && after != 0) syncNow();
        else syncSpool(sl.getGameTime());
        return spool > 1.0E-3F;
    }

    @Override
    protected void idle() {
        powered = false;
        push = approach(push, 0.0F, 1.0F / Balance.BOW_SPOOL);
        super.idle();
        spool = Math.abs(push);
    }

    @Override
    public Component status() {
        String k = !powered ? "message.wmd.p6.no_power" : spool > 0.05F ? (dry ? "message.wmd.bow_thruster.dry" : "message.wmd.bow_thruster.on") : "message.wmd.bow_thruster.off";
        return Component.translatable("block.wmd.bow_thruster").append(": ").append(Component.translatable(k, Math.round(push * 100.0F)));
    }

    @Override protected String detail() { return "push " + Math.round(push * 100) + "%, wet " + !dry; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.UP) ? energy() : null; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = spinAt(pt) * RZ * cSign;
        model.getBone("tbp").ifPresent(b -> b.setRotZ(a));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.2F || lv.random.nextFloat() > cSpool * 0.6F) return;
        double[] w = worldPoint(cSign > 0 ? -0.55 : 0.55, 0.44, 0.0);
        lv.addParticle(ParticleTypes.BUBBLE, w[0], w[1] + (lv.random.nextDouble() - 0.5) * 0.5, w[2] + (lv.random.nextDouble() - 0.5) * 0.5, 0.0, 0.02, 0.0);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.BOW_THRUSTER_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }

    @Override protected void putExtra(CompoundTag tag) { tag.putInt("btSign", push < 0.0F ? -1 : 1); }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        if (client) { cSign = tag.getInt("btSign") < 0 ? -1 : 1; return; }
        push = Mth.clamp(spool, 0.0F, 1.0F) * (tag.getInt("btSign") < 0 ? -1.0F : 1.0F);
    }
}

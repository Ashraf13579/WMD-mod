package com.ashraf.wmd;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class RefuelProbeBlockEntity extends P6DriveBlockEntity implements P7Parts {

    private static final Set<String> MOVED = Set.of("probe");

    private static final Set<RefuelProbeBlockEntity> LIVE = Statics.server(new HashSet<>());
    static final float TIP_Z = -9.6F / 16.0F, TRAVEL = 12.0F / 16.0F;

    private float out;
    private long flowAt = -100;

    private float tOut, cOut, pOut;

    public RefuelProbeBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.REFUEL_PROBE_BE.get(), pos, state, 0, 1, 0);
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 0.5, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, 0.0 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected boolean damageable() { return false; }
    @Override protected int spoolUp() { return Balance.PROBE_TICKS; }
    @Override protected int spoolDown() { return Balance.PROBE_TICKS; }

    static Set<RefuelProbeBlockEntity> live() {
        LIVE.removeIf(p -> p.isRemoved() || p.level == null);
        return LIVE;
    }

    Vec3 tipWorld() { return worldVec(0.0, 0.5, TIP_Z - TRAVEL * out); }

    java.util.UUID shipId() { return ship; }

    void flowing(long now) { flowAt = now; }

    int hudState(long now) { return now - flowAt < 10 ? 2 : out > 0.5F ? 1 : 0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (now % 10 == (worldPosition.asLong() & 7)) ship = SableBridge.shipIdAt(sl, worldPosition);
        float want = ship != null && ShipModes.refuel(ship) ? 1.0F : 0.0F;
        float before = out;
        out = approach(out, want, 1.0F / Balance.PROBE_TICKS);
        if ((before == 0.0F && out > 0.0F) || (before == 1.0F && out < 1.0F)) playOnce(sl, WmdSounds.PROBE_SLIDE.get(), Balance.SND_MECH, out > before ? 1.05F : 0.95F);
        if (out >= 1.0F) LIVE.add(this); else LIVE.remove(this);
        spool = out;
        force = null;
        syncSpool(now);
        return true;
    }

    @Override
    public void setRemoved() {
        LIVE.remove(this);
        super.setRemoved();
    }

    @Override
    public Component status() {
        long now = level == null ? 0 : level.getGameTime();
        String k = hudState(now) == 2 ? "message.wmd.probe.flowing" : out >= 1.0F ? "message.wmd.probe.out" : out > 0.0F ? "message.wmd.probe.moving" : "message.wmd.probe.in";
        return Component.translatable("block.wmd.refuel_probe").append(": ").append(Component.translatable(k));
    }

    @Override protected String detail() { return "out " + Math.round(out * 100) + "%"; }

    @Override
    protected void clientExtra(Level lv) {
        pOut = cOut;
        cOut = approach(cOut, tSpool, 1.0F / Balance.PROBE_TICKS);
    }

    @Override public void ccAnimate(CcBones model, double tt, float pt) {}

    @Override public Set<String> movedBones() { return MOVED; }

    @Override public float[] boneShift(String bone, float pt) { return new float[] { 0.0F, 0.0F, -TRAVEL * Mth.lerp(pt, pOut, cOut) }; }

    @Override protected SoundEvent runSound() { return WmdSounds.COCKPIT_SWITCH.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected boolean soundAlive() { return false; }

    @Override protected void putExtra(CompoundTag tag) { tag.putFloat("rpOut", out); }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        float o = Mth.clamp(tag.getFloat("rpOut"), 0.0F, 1.0F);
        if (client) tOut = o; else out = o;
    }
}

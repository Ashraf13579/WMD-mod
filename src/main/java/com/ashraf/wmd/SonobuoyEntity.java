package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SonobuoyEntity extends Entity {

    private static final EntityDataAccessor<Float> EAR = SynchedEntityData.defineId(SonobuoyEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;
    private int freq = 1, life;
    private Vec3 vel = Vec3.ZERO;

    public SonobuoyEntity(EntityType<? extends SonobuoyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static SonobuoyEntity toss(ServerLevel level, Vec3 at, Vec3 v, UUID owner, int freq) {
        SonobuoyEntity e = new SonobuoyEntity(Wmd.SONOBUOY_ENTITY.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.vel = v;
        e.owner = owner;
        e.freq = Math.max(1, freq);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(EAR, 0.0F); }

    public float earDrop() { return entityData.get(EAR); }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().expandTowards(0.0, -Math.max(0.0F, earDrop()) - 0.5, 0.0);
    }

    private long sensorId() { return getUUID().getMostSignificantBits() | Long.MIN_VALUE; }

    private long[] held;

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sr && !isRemoved()) held = RunnerChunks.follow(sr, getUUID(), position(), getDeltaMovement(), held);
        Level l = level();
        Vec3 pos = position();
        boolean wet = l.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (!(l instanceof ServerLevel sl)) return;
        life++;
        if (life > Balance.SONOBUOY_TICKS || pos.y < sl.getMinBuildHeight()) { scuttle(sl); return; }
        int top = NavalLayer.waterTop(sl, Mth.floor(pos.x), Mth.floor(pos.z));
        if (top == NavalLayer.NO_WATER && !wet) {
            vel = new Vec3(vel.x * 0.98, vel.y - 0.05, vel.z * 0.98);
            Vec3 next = pos.add(vel);
            if (!sl.getBlockState(BlockPos.containing(next)).getCollisionShape(sl, BlockPos.containing(next)).isEmpty()) vel = Vec3.ZERO;
            else setPos(next.x, next.y, next.z);
            return;
        }
        double surface = top + 1.0 - 0.75;
        if (pos.y > surface + 0.05) {
            vel = new Vec3(vel.x * 0.98, vel.y - 0.05, vel.z * 0.98);
            Vec3 next = pos.add(vel);
            if (next.y <= surface) {
                next = new Vec3(next.x, surface, next.z);
                vel = Vec3.ZERO;
                sl.sendParticles(ParticleTypes.SPLASH, next.x, top + 1.0, next.z, 30, 0.3, 0.1, 0.3, 0.2);
                sl.playSound(null, next.x, next.y, next.z, WmdSounds.BUOY_SPLASH.get(), SoundSource.NEUTRAL, 1.5F, 1.0F);
            }
            setPos(next.x, next.y, next.z);
            return;
        }
        if (Math.abs(pos.y - surface) > 0.01) setPos(pos.x, surface, pos.z);

        float want = (float) Math.min(Balance.SONOBUOY_EAR_DEPTH, Math.max(0.0, seabedBelow(sl, position()) - 1.0));
        float ear = earDrop();
        if (ear < want) entityData.set(EAR, Math.min(want, ear + 0.25F));
        else if (ear > want + 0.5F) entityData.set(EAR, want);
        if ((sl.getGameTime() + (getId() & 15)) % Balance.SONAR_SCAN_TICKS != 0 || earDrop() < 1.0F) return;
        Vec3 e = position().add(0.0, -earDrop(), 0.0);
        SonarNet.beat(sl, owner, freq, sensorId(), e, SonarNet.BUOY, Balance.SONOBUOY_RANGE, null);
        List<NoiseNet.Heard> h = new ArrayList<>();
        for (NoiseNet.Heard x : NoiseNet.heard(sl, e, Balance.SONOBUOY_RANGE, Balance.NOISE_FLOOR_DB, null, null)) {
            double lv = x.level() - SonarNet.layerLoss(sl, e, x.src().pos());
            if (lv >= Balance.NOISE_FLOOR_DB) h.add(new NoiseNet.Heard(x.src(), lv, x.dist(), x.bearing()));
        }
        SonarNet.report(sl, owner, freq, sensorId(), e, h);
    }

    private static double seabedBelow(ServerLevel sl, Vec3 p) {
        int x = Mth.floor(p.x), z = Mth.floor(p.z), y0 = Mth.floor(p.y);
        for (int d = 1; d <= Balance.SONOBUOY_EAR_DEPTH + 2; d++)
            if (!sl.getFluidState(new BlockPos(x, y0 - d, z)).is(FluidTags.WATER)) return d;
        return Balance.SONOBUOY_EAR_DEPTH + 2;
    }

    private void scuttle(ServerLevel sl) {
        SonarNet.forget(sl, owner, freq, sensorId());
        sl.sendParticles(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 20, 0.2, 0.2, 0.2, 0.05);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sr) held = RunnerChunks.release(sr, getUUID(), held);
        if (level() instanceof ServerLevel sl) SonarNet.forget(sl, owner, freq, sensorId());
        super.remove(reason);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160.0 * 160.0; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        freq = Math.max(1, tag.getInt("freq"));
        life = tag.getInt("life");
        entityData.set(EAR, tag.getFloat("ear"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("freq", freq);
        tag.putInt("life", life);
        tag.putFloat("ear", earDrop());
    }
}

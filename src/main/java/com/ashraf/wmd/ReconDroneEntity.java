package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class ReconDroneEntity extends Entity {

    private static final EntityDataAccessor<Integer> DRONE_ID = SynchedEntityData.defineId(ReconDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ReconDroneEntity.class, EntityDataSerializers.INT);

    private int lerpSteps;
    private double lx, ly, lz;
    private float lyaw;

    public ReconDroneEntity(EntityType<? extends ReconDroneEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DRONE_ID, 0);
        builder.define(PHASE, 0);
    }

    void setup(int droneId, int phase) { entityData.set(DRONE_ID, droneId); entityData.set(PHASE, phase); }

    void setPhase(int phase) { if (entityData.get(PHASE) != phase) entityData.set(PHASE, phase); }

    public int droneId() { return entityData.get(DRONE_ID); }

    public int phase() { return entityData.get(PHASE); }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            if (tickCount % 20 == 5 && !Drones.alive(sl.getServer(), droneId())) discard();
            return;
        }
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
            setYRot(getYRot() + net.minecraft.util.Mth.wrapDegrees(lyaw - getYRot()) * (float) k);
            lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lx = x; ly = y; lz = z; lyaw = yRot;
        lerpSteps = Math.max(1, steps + 1);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) return false;
        if (level() instanceof ServerLevel sl) Drones.hurt(sl, droneId(), amount, source.getEntity());
        return true;
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isAttackable() { return true; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 320.0 * 320.0; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

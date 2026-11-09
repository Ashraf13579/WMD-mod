package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CommsBuoyEntity extends Entity {

    private static final EntityDataAccessor<Float> FX = SynchedEntityData.defineId(CommsBuoyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FY = SynchedEntityData.defineId(CommsBuoyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FZ = SynchedEntityData.defineId(CommsBuoyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(CommsBuoyEntity.class, EntityDataSerializers.INT);

    public static final int M_RISING = 0, M_FLOATING = 1, M_HANGING = 2, M_DRY = 3, M_REELING = 4;

    private BlockPos winch;

    private int lerpSteps;
    private double lx, ly, lz;

    public CommsBuoyEntity(EntityType<? extends CommsBuoyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static CommsBuoyEntity release(ServerLevel sl, CommsBuoyBlockEntity w) {
        CommsBuoyEntity e = new CommsBuoyEntity(Wmd.COMMS_BUOY.get(), sl);
        Vec3 f = w.fairlead();
        e.winch = w.getBlockPos();
        e.setPos(f.x, f.y + 0.4, f.z);
        e.setAnchor(f);
        sl.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(FX, 0.0F);
        b.define(FY, 0.0F);
        b.define(FZ, 0.0F);
        b.define(MODE, M_RISING);
    }

    private void setAnchor(Vec3 f) {
        entityData.set(FX, (float) f.x);
        entityData.set(FY, (float) f.y);
        entityData.set(FZ, (float) f.z);
    }

    public Vec3 anchor() { return new Vec3(entityData.get(FX), entityData.get(FY), entityData.get(FZ)); }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        Vec3 a = anchor();
        if (a.lengthSqr() == 0.0) return super.getBoundingBoxForCulling();
        return super.getBoundingBoxForCulling().minmax(new net.minecraft.world.phys.AABB(a, a).inflate(0.5));
    }

    public int mode() { return entityData.get(MODE); }
    public boolean floating() { return mode() == M_FLOATING; }
    public boolean hanging() { return mode() == M_HANGING; }
    public boolean dry() { return mode() == M_DRY; }

    void reel() { if (mode() != M_REELING) entityData.set(MODE, M_REELING); }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (winch == null || !(sl.getBlockEntity(winch) instanceof CommsBuoyBlockEntity w) || w.isRemoved()) { discard(); return; }
        Vec3 f = w.fairlead();
        setAnchor(f);
        Vec3 pos = position();
        int mode = mode();
        Vec3 target;
        if (mode == M_REELING) {
            target = f;
            if (pos.distanceTo(f) < 0.6) { w.stowed(); discard(); return; }
        } else {
            int top = NavalLayer.waterTop(sl, Mth.floor(f.x), Mth.floor(f.z));
            if (top == NavalLayer.NO_WATER || top + 1.0 < f.y) { target = f.add(0.0, 1.0, 0.0); mode = M_DRY; }
            else if (top + 1.0 - f.y <= Balance.BUOY_CABLE) { target = new Vec3(f.x, top + 0.95, f.z); mode = pos.distanceTo(target) < 0.6 ? M_FLOATING : M_RISING; }
            else { target = f.add(0.0, Balance.BUOY_CABLE, 0.0); mode = pos.distanceTo(target) < 0.6 ? M_HANGING : M_RISING; }
            if (mode != mode()) entityData.set(MODE, mode);
        }
        Vec3 d = target.subtract(pos);
        double side = Math.max(Balance.BUOY_RISE, 1.5), up = Balance.BUOY_RISE;
        Vec3 step = new Vec3(Mth.clamp(d.x, -side, side), Mth.clamp(d.y, -up * (mode == M_REELING ? 2.0 : 1.0), up), Mth.clamp(d.z, -side, side));
        if (mode() == M_FLOATING) step = step.add(0.0, Math.sin(tickCount * 0.12) * 0.004, 0.0);
        setPos(pos.add(step));
        setDeltaMovement(step);
    }

    private void clientTick() {
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
            lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lx = x; ly = y; lz = z;
        lerpSteps = Math.max(1, steps);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160.0 * 160.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

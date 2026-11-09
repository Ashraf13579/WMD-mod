package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DredgeBucketEntity extends Entity {

    private static final EntityDataAccessor<Float> TOP_X = SynchedEntityData.defineId(DredgeBucketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TOP_Y = SynchedEntityData.defineId(DredgeBucketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TOP_Z = SynchedEntityData.defineId(DredgeBucketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> JAW = SynchedEntityData.defineId(DredgeBucketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LOADED = SynchedEntityData.defineId(DredgeBucketEntity.class, EntityDataSerializers.BOOLEAN);

    private int lastFed;

    public DredgeBucketEntity(EntityType<? extends DredgeBucketEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(TOP_X, 0.0F).define(TOP_Y, 0.0F).define(TOP_Z, 0.0F).define(JAW, 1.0F).define(LOADED, false);
    }

    void follow(double x, double y, double z, Vec3 top, float yaw, float jawOpen, boolean loaded) {
        setPos(x, y, z);
        setYRot(yaw);
        entityData.set(TOP_X, (float) top.x);
        entityData.set(TOP_Y, (float) top.y);
        entityData.set(TOP_Z, (float) top.z);
        entityData.set(JAW, jawOpen);
        entityData.set(LOADED, loaded);
        lastFed = tickCount;
    }

    public Vec3 top() { return new Vec3(entityData.get(TOP_X), entityData.get(TOP_Y), entityData.get(TOP_Z)); }
    public float jaw() { return entityData.get(JAW); }
    public boolean loaded() { return entityData.get(LOADED); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount - lastFed > 40) discard();
            return;
        }
        if (tickCount % 3 == 0 && level().getFluidState(BlockPos.containing(position())).is(FluidTags.WATER))
            level().addParticle(ParticleTypes.BUBBLE, getX() + (random.nextDouble() - 0.5) * 0.6, getY() - 0.4, getZ() + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.08, 0.0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 t = top();
        return getBoundingBox().inflate(0.8).minmax(new AABB(t.x - 0.3, t.y - 0.3, t.z - 0.3, t.x + 0.3, t.y + 0.3, t.z + 0.3));
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160.0 * 160.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

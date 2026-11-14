package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EjectedCanopyEntity extends Entity {

    private static final EntityDataAccessor<Boolean> TANDEM = SynchedEntityData.defineId(EjectedCanopyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TINT = SynchedEntityData.defineId(EjectedCanopyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TUMBLE = SynchedEntityData.defineId(EjectedCanopyEntity.class, EntityDataSerializers.FLOAT);

    private int age, landedAt = -1;
    private float spin = 14.0F, tumbleO;

    public EjectedCanopyEntity(EntityType<? extends EjectedCanopyEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    void setup(boolean tandem, int tint, Vec3 at, Vec3 vel, float yaw) {
        entityData.set(TANDEM, tandem);
        entityData.set(TINT, tint);
        moveTo(at.x, at.y, at.z, yaw, 0.0F);
        setDeltaMovement(vel);
        spin = 10.0F + random.nextFloat() * 8.0F;
    }

    public boolean tandem() { return entityData.get(TANDEM); }
    public int tint() { return entityData.get(TINT); }
    public float tumble(float pt) { return Mth.lerp(pt, tumbleO, entityData.get(TUMBLE)); }

    @Override
    public void tick() {
        super.tick();
        tumbleO = entityData.get(TUMBLE);
        if (level().isClientSide) return;
        age++;
        if (landedAt >= 0) {
            if (age - landedAt > Balance.EJECT_PROP_TICKS) discard();
            return;
        }
        Vec3 v = getDeltaMovement();
        v = new Vec3(v.x * 0.98, v.y - Balance.EJECT_GRAVITY, v.z * 0.98);
        setDeltaMovement(v);
        if (age <= Balance.EJECT_ROCKET_TICKS) setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        else move(MoverType.SELF, v);
        entityData.set(TUMBLE, entityData.get(TUMBLE) + spin);
        if (onGround() || isInWater() || age > Balance.EJECT_MAX_TICKS) {
            landedAt = age;

            float t = ((entityData.get(TUMBLE) % 360.0F) + 360.0F) % 360.0F;
            entityData.set(TUMBLE, t < 90.0F || t > 270.0F ? 0.0F : 180.0F);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(TANDEM, false); b.define(TINT, 0); b.define(TUMBLE, 0.0F); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { age = tag.getInt("age"); landedAt = tag.contains("landed") ? tag.getInt("landed") : -1; entityData.set(TANDEM, tag.getBoolean("tandem")); entityData.set(TINT, tag.getInt("tint")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("age", age); if (landedAt >= 0) tag.putInt("landed", landedAt); tag.putBoolean("tandem", tandem()); tag.putInt("tint", tint()); }
    @Override public boolean isPickable() { return false; }
}

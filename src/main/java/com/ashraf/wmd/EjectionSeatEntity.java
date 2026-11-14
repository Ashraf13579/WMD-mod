package com.ashraf.wmd;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EjectionSeatEntity extends Entity {

    private static final EntityDataAccessor<Boolean> TANDEM = SynchedEntityData.defineId(EjectionSeatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BACK = SynchedEntityData.defineId(EjectionSeatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CHUTE = SynchedEntityData.defineId(EjectionSeatEntity.class, EntityDataSerializers.INT);

    private Vec3 up = new Vec3(0.0, 1.0, 0.0);
    private int age, landedAt = -1;

    public EjectionSeatEntity(EntityType<? extends EjectionSeatEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    void setup(boolean tandem, boolean back, Vec3 at, Vec3 vel, float yaw, Vec3 launchUp) {
        entityData.set(TANDEM, tandem);
        entityData.set(BACK, back);
        up = launchUp.lengthSqr() > 1.0E-6 ? launchUp.normalize() : new Vec3(0.0, 1.0, 0.0);
        moveTo(at.x, at.y, at.z, yaw, 0.0F);
        setDeltaMovement(vel);
    }

    public boolean tandem() { return entityData.get(TANDEM); }
    public boolean back() { return entityData.get(BACK); }

    public float chuteOpen(float pt) {
        int c = entityData.get(CHUTE);
        if (c < 0) return 0.0F;
        return Math.min(1.0F, (tickCount + pt - c) / (float) Balance.EJECT_CHUTE_OPEN_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Vec3 v = getDeltaMovement();
        boolean rocket = age <= Balance.EJECT_ROCKET_TICKS && landedAt < 0;
        if (level().isClientSide) {
            if (rocket) {
                Vec3 p = position();
                for (int i = 0; i < 3; i++) {
                    level().addParticle(ParticleTypes.FLAME, p.x - up.x * 0.4 + (random.nextDouble() - 0.5) * 0.2, p.y - 0.2, p.z - up.z * 0.4 + (random.nextDouble() - 0.5) * 0.2, -v.x * 0.2, -0.15, -v.z * 0.2);
                    level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y - 0.3, p.z, (random.nextDouble() - 0.5) * 0.05, 0.02, (random.nextDouble() - 0.5) * 0.05);
                }
            } else if (entityData.get(CHUTE) < 0 && landedAt < 0 && tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY() - 0.2, getZ(), 0.0, 0.0, 0.0);
            }
            return;
        }
        if (landedAt >= 0) {
            setDeltaMovement(Vec3.ZERO);
            if (age - landedAt > Balance.EJECT_PROP_TICKS) discard();
            return;
        }
        boolean chute = entityData.get(CHUTE) >= 0;
        if (rocket) v = v.add(up.scale(Balance.EJECT_ROCKET_ACC));
        if (!chute) {
            v = new Vec3(v.x * 0.99, v.y - Balance.EJECT_GRAVITY, v.z * 0.99);
            if (!rocket && v.y < 0.0) {
                entityData.set(CHUTE, tickCount);
                level().playSound(null, blockPosition(), WmdSounds.CHUTE_OPEN.get(), SoundSource.PLAYERS, Balance.SND_MECH, 1.0F);
            }
        } else {
            float open = chuteOpen(0.0F);
            double fall = -Balance.EJECT_CHUTE_FALL - (1.0 - open) * 0.3;
            v = new Vec3(v.x * 0.93 + Math.sin(tickCount * 0.05) * 0.004, Math.max(v.y - Balance.EJECT_GRAVITY * 0.5, fall), v.z * 0.93);
        }
        setDeltaMovement(v);

        if (rocket) setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        else move(MoverType.SELF, v);
        for (Entity p : getPassengers()) p.resetFallDistance();
        if (onGround() || isInWater() || age > Balance.EJECT_MAX_TICKS) {
            landedAt = age;
            ejectPassengers();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) { return new Vec3(0.0, 0.24, 0.0); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(TANDEM, false); b.define(BACK, false); b.define(CHUTE, -1); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { age = tag.getInt("age"); landedAt = tag.contains("landed") ? tag.getInt("landed") : -1; entityData.set(TANDEM, tag.getBoolean("tandem")); entityData.set(BACK, tag.getBoolean("back")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("age", age); if (landedAt >= 0) tag.putInt("landed", landedAt); tag.putBoolean("tandem", tandem()); tag.putBoolean("back", back()); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRiderSit() { return true; }
}

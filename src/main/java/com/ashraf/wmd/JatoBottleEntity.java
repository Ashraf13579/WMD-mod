package com.ashraf.wmd;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class JatoBottleEntity extends Entity {

    private static final EntityDataAccessor<Integer> LANDED = SynchedEntityData.defineId(JatoBottleEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Float> TUMBLE = SynchedEntityData.defineId(JatoBottleEntity.class, EntityDataSerializers.FLOAT);

    public JatoBottleEntity(EntityType<? extends JatoBottleEntity> type, Level level) {
        super(type, level);
    }

    JatoBottleEntity(ServerLevel level, Vec3 pos, Vec3 nose, Vec3 vel) {
        this(Wmd.JATO_BOTTLE.get(), level);
        setPos(pos.x, pos.y - 0.2, pos.z);
        Vec3 n = nose.lengthSqr() > 1.0E-6 ? nose.normalize() : new Vec3(0.0, 0.0, -1.0);
        setYRot((float) (Mth.atan2(-n.x, n.z) * Mth.RAD_TO_DEG));
        setXRot((float) (-Math.asin(Mth.clamp(n.y, -1.0, 1.0)) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
        setDeltaMovement(vel);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(LANDED, -1);
        b.define(TUMBLE, 0.0F);
    }

    public boolean landed() { return entityData.get(LANDED) >= 0; }

    public float tumble(float partialTick) {
        if (landed()) return entityData.get(TUMBLE);
        return (tickCount + partialTick) * Balance.JATO_TUMBLE_DEG / 20.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!landed()) {
            Vec3 v = getDeltaMovement().add(0.0, -0.04, 0.0).scale(0.98);
            setDeltaMovement(v);
            move(MoverType.SELF, v);
            if (level().isClientSide && tickCount < 40) {
                var r = random;
                level().addParticle(WmdParticles.TRAIL_SMOKE.get(), getX() + (r.nextDouble() - 0.5) * 0.2, getY() + 0.3, getZ() + (r.nextDouble() - 0.5) * 0.2, 0.0, 0.02, 0.0);
            }
            if (!level().isClientSide && (onGround() || isInWater())) {
                entityData.set(TUMBLE, tumble(0.0F) % 360.0F);
                entityData.set(LANDED, tickCount);
                level().playSound(null, getX(), getY(), getZ(), WmdSounds.JATO_BOTTLE_LAND.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.9F + random.nextFloat() * 0.2F);
            }
        } else {
            Vec3 v = getDeltaMovement().multiply(0.5, 1.0, 0.5).add(0.0, -0.04, 0.0);
            setDeltaMovement(v);
            move(MoverType.SELF, v);
            if (onGround()) setDeltaMovement(Vec3.ZERO);
        }
        if (!level().isClientSide && tickCount > Balance.JATO_PROP_LIFE) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 6, 0.3, 0.1, 0.3, 0.01);
            discard();
        }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128.0 * 128.0; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        tickCount = tag.getInt("age");
        entityData.set(LANDED, tag.getBoolean("landed") ? 0 : -1);
        entityData.set(TUMBLE, tag.getFloat("tumble"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("age", tickCount);
        tag.putBoolean("landed", landed());
        tag.putFloat("tumble", entityData.get(TUMBLE));
    }
}

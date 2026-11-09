package com.ashraf.wmd;

import java.util.UUID;

import org.joml.Vector3f;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class NoisemakerEntity extends Entity {

    static final int EJECT_TICKS = 14, TIP_TICKS = 20, SINK_TICKS = 60;
    static final double EJECT_SPEED = 0.2;
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(NoisemakerEntity.class, EntityDataSerializers.VECTOR3);

    private UUID owner;
    private int life;

    public NoisemakerEntity(EntityType<? extends NoisemakerEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static NoisemakerEntity drop(ServerLevel level, Vec3 at, UUID owner) { return spawn(level, at, Vec3.ZERO, owner); }

    static NoisemakerEntity eject(ServerLevel level, Vec3 at, Vec3 dir, UUID owner) { return spawn(level, at, dir, owner); }

    private static NoisemakerEntity spawn(ServerLevel level, Vec3 at, Vec3 dir, UUID owner) {
        NoisemakerEntity e = new NoisemakerEntity(Wmd.NOISEMAKER_ENTITY.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.owner = owner;
        e.entityData.set(DIR, new Vector3f((float) dir.x, (float) dir.y, (float) dir.z));
        level.addFreshEntity(e);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 2.0F, 1.4F);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 30, 0.3, 0.3, 0.3, 0.1);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(DIR, new Vector3f()); }

    public Vec3 launchDir() { Vector3f d = entityData.get(DIR); return new Vec3(d.x, d.y, d.z); }

    public boolean running() { return age() <= Balance.TORP_DECOY_TICKS; }

    private int age() { return level().isClientSide ? tickCount : life; }

    Object loop;

    @Override
    public void tick() {
        super.tick();
        Level l = level();
        if (!l.isClientSide) life++;
        int age = age();
        Vec3 p = position();
        boolean wet = l.getFluidState(BlockPos.containing(p)).is(FluidTags.WATER);

        Vec3 dir = launchDir();
        if (age <= EJECT_TICKS && dir.lengthSqr() > 1e-4) {
            Vec3 next = p.add(dir.scale(EJECT_SPEED * (1.0 - (age - 1) / (double) EJECT_TICKS)));
            BlockPos nb = BlockPos.containing(next);
            if (l.getBlockState(nb).getCollisionShape(l, nb).isEmpty()) setPos(next.x, next.y, next.z);
        } else if (l.getFluidState(BlockPos.containing(p.x, p.y - 0.7, p.z)).is(FluidTags.WATER)) {
            setPos(p.x, p.y - (age > Balance.TORP_DECOY_TICKS ? 0.04 : 0.01), p.z);
        }
        if (l.isClientSide) {
            if (wet && running() && age > EJECT_TICKS / 2) jets(l, position(), age);
            if (running()) loop = LoopSound.keep(loop, tickCount, WmdSounds.NM_LOOP.get(), 2.0F, 1.0F, this::position, () -> !isRemoved() && running());
            return;
        }
        ServerLevel sl = (ServerLevel) l;
        if (age > Balance.TORP_DECOY_TICKS + SINK_TICKS || !wet) { NoiseNet.silence(sl, getUUID()); discard(); return; }
        if (age == Balance.TORP_DECOY_TICKS + 1) NoiseNet.silence(sl, getUUID());
        if (running() && age % 10 == 1) NoiseNet.emit(sl, getUUID(), position(), Balance.TORP_DECOY_DB, NoiseNet.Kind.DECOY, owner, null);
    }

    private void jets(Level l, Vec3 p, int age) {
        double a = age * 0.7;
        for (int k = 0; k < 2; k++) {
            double ang = a + k * Math.PI, cx = Math.cos(ang), cz = Math.sin(ang);
            l.addParticle(ParticleTypes.BUBBLE, p.x + cx * 0.22, p.y - 0.45, p.z + cz * 0.22, cx * 0.08, 0.05, cz * 0.08);
        }
        if (age % 2 == 0) l.addParticle(ParticleTypes.BUBBLE, p.x + (random.nextDouble() - 0.5) * 0.3, p.y - 0.3 + random.nextDouble() * 0.8, p.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.1, 0);
        if (age % 4 == 0) l.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, p.x, p.y + 0.65, p.z, 0, 0.1, 0);
    }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(0.4, 0.8, 0.4); }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

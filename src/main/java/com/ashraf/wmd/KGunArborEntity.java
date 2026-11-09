package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class KGunArborEntity extends Entity {

    static final int SEP_TICKS = 3, LIFE = 60, MAX_AGE = 20 * 20;
    static final double DRAG = 0.95, GRAV = 0.07, KICK_BACK = 0.12, KICK_SIDE = 0.05, SINK = 0.12;

    private Vec3 vel = Vec3.ZERO;
    private int age, rest = -1;
    private boolean wasWet, landed;
    private BlockPos gun;

    public KGunArborEntity(EntityType<? extends KGunArborEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static KGunArborEntity toss(ServerLevel level, BlockPos gun, Vec3 at, Vec3 v, float headingDeg, float tiltDeg) {
        KGunArborEntity e = new KGunArborEntity(Wmd.KGUN_ARBOR_ENTITY.get(), level);
        e.gun = gun;
        e.setPos(at.x, at.y, at.z);
        e.vel = v;
        e.setYRot(headingDeg);
        e.setXRot(Math.max(-89.0F, Math.min(89.0F, tiltDeg)));
        e.setDeltaMovement(v);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Level l = level();
        Vec3 pos = position();
        boolean wet = l.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (!(l instanceof ServerLevel sl)) {
            if (wet && tickCount % 3 == 0) l.addParticle(ParticleTypes.BUBBLE, pos.x, pos.y + 0.2, pos.z, 0, 0.08, 0);
            return;
        }
        age++;
        if (age > MAX_AGE || pos.y < sl.getMinBuildHeight() || (rest >= 0 && ++rest > LIFE)) { discard(); return; }
        if (landed) return;
        if (age == SEP_TICKS) separate(sl, pos);
        if (!wet) {
            boolean free = age >= SEP_TICKS;
            double d = free ? DRAG : 0.98;
            vel = new Vec3(vel.x * d, vel.y - (free ? GRAV : 0.06), vel.z * d);
        } else {
            if (!wasWet) {
                sl.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.5, pos.z, 24, 0.3, 0.15, 0.3, 0.15);
                sl.sendParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y + 0.4, pos.z, 8, 0.25, 0.05, 0.25, 0.02);
                sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.25F + sl.random.nextFloat() * 0.15F);
                rest = 0;
            }
            vel = new Vec3(vel.x * 0.8, vel.y + (-SINK - vel.y) * 0.3, vel.z * 0.8);
        }
        wasWet = wet;
        Vec3 next = pos.add(vel);
        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK && !ownGun(sl, hit.getBlockPos())) {
            Vec3 h = hit.getLocation();
            setPos(h.x, h.y, h.z);
            vel = Vec3.ZERO;
            setDeltaMovement(Vec3.ZERO);
            landed = true;
            if (!wet) sl.playSound(null, h.x, h.y, h.z, SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 0.9F, 0.7F);
            if (rest < 0) rest = 0;
            return;
        }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private boolean ownGun(ServerLevel sl, BlockPos p) {
        if (gun == null) return false;
        if (p.equals(gun)) return true;
        return sl.getBlockEntity(p) instanceof MachinePartBlockEntity part && gun.equals(part.getController());
    }

    private void separate(ServerLevel sl, Vec3 pos) {
        Vec3 flat = new Vec3(vel.x, 0.0, vel.z);
        if (flat.lengthSqr() > 1e-6) {
            Vec3 f = flat.normalize(), side = new Vec3(-f.z, 0.0, f.x);
            double s = (sl.random.nextFloat() * 2.0F - 1.0F) * KICK_SIDE;
            vel = vel.subtract(f.scale(KICK_BACK)).add(side.scale(s));
        }
        sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.5F);
        sl.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 4, 0.08, 0.08, 0.08, 0.01);
    }

    float tumbleTicks(float pt) { return Math.max(0.0F, tickCount + pt - SEP_TICKS); }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 96.0 * 96.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

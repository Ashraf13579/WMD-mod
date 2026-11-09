package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RisingMineEntity extends Entity {

    private UUID owner, target;
    private Vec3 vel = Vec3.ZERO, start;

    public RisingMineEntity(EntityType<? extends RisingMineEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static RisingMineEntity launch(ServerLevel level, Vec3 at, UUID ship, UUID owner) {
        RisingMineEntity e = new RisingMineEntity(Wmd.RISING_MINE.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.vel = new Vec3(0.0, 0.25, 0.0);
        e.target = ship;
        e.owner = owner;
        e.start = at;
        level.addFreshEntity(e);
        level.playSound(null, at.x, at.y, at.z, WmdSounds.LC_COLD_LAUNCH.get(), SoundSource.BLOCKS, 3.0F, 1.3F);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 60, 0.4, 0.3, 0.4, 0.3);
        NoiseNet.pulse(level, at, Balance.TORP_DB + 10.0, NoiseNet.Kind.LAUNCH, owner, 40);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Level l = level();
        Vec3 pos = position();
        if (!(l instanceof ServerLevel sl)) {
            l.addParticle(ParticleTypes.BUBBLE, pos.x, pos.y - 0.4, pos.z, 0, -0.05, 0);
            return;
        }
        if (start == null) { discard(); return; }
        if (tickCount > 200 || pos.y - start.y > Balance.MINE_BOT_LISTEN + 8.0 || pos.y < sl.getMinBuildHeight()) { boom(sl, pos, "climb"); return; }
        if (!sl.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER) && tickCount > 2) { boom(sl, pos, "surface"); return; }
        SableBridge.ShipInfo s = target == null || !SableBridge.active() ? null : SableBridge.shipInfo(sl, target);
        Vec3 want = new Vec3(0.0, 1.0, 0.0);
        if (s != null && tickCount > 4) {
            Vec3 c = SeaMines.closest(s.worldBounds(), pos);
            if (c.distanceToSqr(pos) > 1e-4) want = c.subtract(pos).normalize();
        }
        double speed = Math.min(Balance.MINE_BOT_SPEED, 0.25 + tickCount * 0.05);
        vel = vel.add(want.scale(speed).subtract(vel).scale(0.3));
        Vec3 next = pos.add(vel);
        if (SableBridge.active()) {
            List<SableBridge.ShipInfo> cands = SableBridge.shipsIntersecting(sl, new AABB(pos, next).inflate(Balance.MINE_BOT_PROX + 1.0));
            if (!cands.isEmpty()) {
                Vec3 ahead = vel.lengthSqr() > 1e-6 ? vel.normalize().scale(Balance.MINE_BOT_PROX) : Vec3.ZERO;
                ProjectilePath.PathHit h = SableBridge.exactHit(sl, pos, next.add(ahead), cands);
                if (h != null) { boom(sl, h.pos().subtract(vel.normalize().scale(0.3)), "hit"); return; }
            }
        }
        if (tickCount > 3 && sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.BLOCK) {
            boom(sl, pos, "ground");
            return;
        }
        if (tickCount % 4 == 0) NoiseNet.emit(sl, getUUID(), next, Balance.TORP_DB, NoiseNet.Kind.TORPEDO, owner, null);
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private void boom(ServerLevel sl, Vec3 at, String why) {
        if (isRemoved()) return;
        discard();
        SeaMineBlockEntity.blast(sl, at, Balance.TORP_POWER[0], Balance.TORP_REACH, UnderwaterBlast.TORPEDO, owner, "rising " + why);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 192.0 * 192.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

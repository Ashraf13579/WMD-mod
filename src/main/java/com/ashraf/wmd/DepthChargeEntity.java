package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DepthChargeEntity extends Entity {

    private UUID owner, ownShip;
    private BlockPos source;
    private double fuseDepth = Balance.DC_DEPTH_DEFAULT;
    private Vec3 vel = Vec3.ZERO;
    private int age;

    public DepthChargeEntity(EntityType<? extends DepthChargeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static DepthChargeEntity drop(ServerLevel level, Vec3 at, Vec3 v, double fuseDepth, UUID owner, UUID ownShip) { return drop(level, at, v, fuseDepth, owner, ownShip, null); }

    static DepthChargeEntity drop(ServerLevel level, Vec3 at, Vec3 v, double fuseDepth, UUID owner, UUID ownShip, BlockPos source) {
        DepthChargeEntity e = new DepthChargeEntity(Wmd.DEPTH_CHARGE_ENTITY.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.vel = v;
        e.fuseDepth = Mth.clamp(fuseDepth, Balance.DC_DEPTH_MIN, Balance.DC_DEPTH_MAX);
        e.owner = owner;
        e.ownShip = ownShip;
        e.source = source;
        e.setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    private long[] held;

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sr && !isRemoved()) held = RunnerChunks.follow(sr, getUUID(), position(), getDeltaMovement(), held);
        Level l = level();
        Vec3 pos = position();
        boolean wet = l.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (!(l instanceof ServerLevel sl)) {
            if (wet && tickCount % 2 == 0) l.addParticle(ParticleTypes.BUBBLE, pos.x, pos.y + 0.3, pos.z, 0, 0.1, 0);
            return;
        }
        age++;
        if (age > 20 * 60 || pos.y < sl.getMinBuildHeight()) { discard(); return; }
        if (!wet) vel = new Vec3(vel.x * 0.98, vel.y - 0.06, vel.z * 0.98);
        else {
            if (!wasWet) { sl.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6, pos.z, 40, 0.4, 0.2, 0.4, 0.2); sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.5F, 0.8F); }
            vel = new Vec3(vel.x * 0.85, vel.y + (-Balance.DC_SINK - vel.y) * 0.3, vel.z * 0.85);
            if (NavalLayer.depthBelowSurface(sl, pos) >= fuseDepth) { detonate(sl, pos, "depth"); return; }
        }
        wasWet = wet;
        Vec3 next = pos.add(vel);
        if (SableBridge.active() && age > Balance.DC_ARM_TICKS) {

            java.util.List<SableBridge.ShipInfo> cands = new java.util.ArrayList<>();
            for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, pos, next)) if (!(s.id().equals(ownShip) && age < Balance.DC_ARM_TICKS * 4)) cands.add(s);
            ProjectilePath.PathHit h = SableBridge.exactHit(sl, pos, next.add(vel.lengthSqr() > 1e-6 ? vel.normalize().scale(0.6) : Vec3.ZERO), cands);
            if (h != null) { detonate(sl, h.pos(), "ship"); return; }
        }
        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK && age <= Balance.DC_SOURCE_PASS_TICKS && ownLauncher(sl, hit.getBlockPos())) hit = BlockHitResult.miss(next, hit.getDirection(), BlockPos.containing(next));
        if (LaidMineEntity.ownShipHit(sl, ownShip, hit, pos, next)) hit = BlockHitResult.miss(next, hit.getDirection(), BlockPos.containing(next));
        if (hit.getType() == HitResult.Type.BLOCK && wet) { detonate(sl, hit.getLocation(), "bottom"); return; }
        if (hit.getType() == HitResult.Type.BLOCK) { vel = Vec3.ZERO; return; }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private boolean wasWet;

    private boolean ownLauncher(ServerLevel sl, BlockPos p) {
        if (source == null) return false;
        if (p.equals(source)) return true;
        return sl.getBlockEntity(p) instanceof MachinePartBlockEntity part && source.equals(part.getController());
    }

    private void detonate(ServerLevel sl, Vec3 at, String why) {
        if (isRemoved()) return;
        discard();
        int hull = ShipStrike.blast(sl, at, Balance.DC_REACH, Balance.DC_POWER, owner);
        UnderwaterBlast.go(sl, at, UnderwaterBlast.DEPTH_CHARGE, Balance.DC_POWER, owner, this);
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p)
            p.displayClientMessage(Component.translatable("message.wmd.depth_charge." + why, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z),
                    (int) Math.round(NavalLayer.depthBelowSurface(sl, at)), hull), false);
        Wmd.LOGGER.info("Depth charge {} at {} {} {} (depth {}), {} hull blast(s)", why, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z),
                (int) Math.round(NavalLayer.depthBelowSurface(sl, at)), hull);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128.0 * 128.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sr) held = RunnerChunks.release(sr, getUUID(), held);
        super.remove(reason);
    }
}

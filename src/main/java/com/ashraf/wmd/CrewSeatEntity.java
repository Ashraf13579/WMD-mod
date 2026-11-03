package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CrewSeatEntity extends Entity {

    private BlockPos capsule = BlockPos.ZERO;

    public CrewSeatEntity(EntityType<? extends CrewSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
        getPersistentData().putBoolean("wmd_air", true);
    }

    static boolean seat(ServerLevel level, BlockPos capsule, ServerPlayer p, int k) {
        int used = level.getEntitiesOfClass(CrewSeatEntity.class, new net.minecraft.world.phys.AABB(capsule).inflate(2.0), s -> s.isVehicle()).size();
        if (used >= Balance.CREW_SEATS) return false;
        int i = k >= 0 ? k % Balance.CREW_SEATS : used;
        CrewSeatEntity s = new CrewSeatEntity(Wmd.CREW_SEAT.get(), level);
        s.capsule = capsule;
        double a = Math.PI * 0.5 * i + Math.PI * 0.25;
        s.setPos(capsule.getX() + 0.5 + Math.cos(a) * 0.35, capsule.getY() + 0.05, capsule.getZ() + 0.5 + Math.sin(a) * 0.35);
        level.addFreshEntity(s);
        if (p.level() != level) p.teleportTo(level, s.getX(), s.getY(), s.getZ(), p.getYRot(), p.getXRot());
        p.startRiding(s, true);
        level.playSound(null, capsule, WmdSounds.OR_CAPSULE_HATCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    @Override
    protected void removePassenger(net.minecraft.world.entity.Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && capsule != null)
            level().playSound(null, capsule, WmdSounds.OR_CAPSULE_HATCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.1F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!isVehicle() || !(level().getBlockState(capsule).getBlock() instanceof CrewCapsuleBlock)) discard();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(capsule.getX() + 0.5 + 2.2, capsule.getY() + 0.2, capsule.getZ() + 0.5);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) { capsule = BlockPos.of(tag.getLong("capsule")); getPersistentData().putBoolean("wmd_air", true); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putLong("capsule", capsule.asLong()); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldBeSaved() { return isVehicle(); }
}

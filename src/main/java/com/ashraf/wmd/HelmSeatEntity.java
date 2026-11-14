package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HelmSeatEntity extends Entity {

    static final double ATTACH_Y = 0.1;

    private static final EntityDataAccessor<BlockPos> HELM = SynchedEntityData.defineId(HelmSeatEntity.class, EntityDataSerializers.BLOCK_POS);

    public HelmSeatEntity(EntityType<? extends HelmSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    void setHelm(BlockPos helm) { entityData.set(HELM, helm.immutable()); }

    public BlockPos helm() { return entityData.get(HELM); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!isVehicle() || !(level().getBlockEntity(helm()) instanceof HelmStationBlockEntity be)) { discard(); return; }
        Vec3 p = be.seatPoint();
        if (p.distanceToSqr(position()) > 1.0E-6) setPos(p.x, p.y, p.z);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) { return new Vec3(0.0, ATTACH_Y, 0.0); }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (level().getBlockEntity(helm()) instanceof HelmStationBlockEntity be) return be.standPoint();
        return position().add(0.0, 0.5, 0.0);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && level().getBlockEntity(helm()) instanceof HelmStationBlockEntity be) be.left(passenger);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(HELM, BlockPos.ZERO); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { setHelm(BlockPos.of(tag.getLong("helm"))); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putLong("helm", helm().asLong()); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldBeSaved() { return isVehicle(); }
}

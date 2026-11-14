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

public class GunnerSeatEntity extends Entity {

    static final double ATTACH_Y = 0.1;

    private static final EntityDataAccessor<BlockPos> STATION = SynchedEntityData.defineId(GunnerSeatEntity.class, EntityDataSerializers.BLOCK_POS);

    public GunnerSeatEntity(EntityType<? extends GunnerSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    void setStation(BlockPos station) { entityData.set(STATION, station.immutable()); }

    public BlockPos station() { return entityData.get(STATION); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!isVehicle() || !(level().getBlockEntity(station()) instanceof GunnerStationBlockEntity be)) { discard(); return; }
        Vec3 p = be.seatPoint();
        if (p.distanceToSqr(position()) > 1.0E-6) setPos(p.x, p.y, p.z);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) { return new Vec3(0.0, ATTACH_Y, 0.0); }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (level().getBlockEntity(station()) instanceof GunnerStationBlockEntity be) return be.standPoint();
        return position().add(0.0, 1.2, 0.0);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && level().getBlockEntity(station()) instanceof GunnerStationBlockEntity be) be.left(passenger);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(STATION, BlockPos.ZERO); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { setStation(BlockPos.of(tag.getLong("station"))); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putLong("station", station().asLong()); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldBeSaved() { return isVehicle(); }
}

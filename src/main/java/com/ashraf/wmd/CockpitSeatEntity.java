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

public class CockpitSeatEntity extends Entity {

    static final double ATTACH_Y = 0.1;

    private static final EntityDataAccessor<BlockPos> COCKPIT = SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SEAT = SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    public CockpitSeatEntity(EntityType<? extends CockpitSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    void setCockpit(BlockPos cockpit, int seat) { entityData.set(COCKPIT, cockpit.immutable()); entityData.set(SEAT, seat); }

    public BlockPos cockpit() { return entityData.get(COCKPIT); }

    public int seatIndex() { return entityData.get(SEAT); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!isVehicle() || !(level().getBlockEntity(cockpit()) instanceof JetCockpitBlockEntity be)) { discard(); return; }
        Vec3 p = be.seatPoint(seatIndex());
        if (p.distanceToSqr(position()) > 1.0E-6) setPos(p.x, p.y, p.z);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) { return new Vec3(0.0, ATTACH_Y, 0.0); }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (level().getBlockEntity(cockpit()) instanceof JetCockpitBlockEntity be) return be.standPoint(seatIndex());
        return position().add(0.0, 1.2, 0.0);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && level().getBlockEntity(cockpit()) instanceof JetCockpitBlockEntity be) be.left(passenger, seatIndex());
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(COCKPIT, BlockPos.ZERO); b.define(SEAT, 0); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { setCockpit(BlockPos.of(tag.getLong("cockpit")), tag.getInt("seat")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putLong("cockpit", cockpit().asLong()); tag.putInt("seat", seatIndex()); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldBeSaved() { return isVehicle(); }
}

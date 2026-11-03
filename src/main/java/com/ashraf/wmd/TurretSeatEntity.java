package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TurretSeatEntity extends Entity {

    private BlockPos controller = BlockPos.ZERO, hatch = BlockPos.ZERO;

    public TurretSeatEntity(EntityType<? extends TurretSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
        getPersistentData().putBoolean("wmd_air", true);
    }

    void setUp(BlockPos controller, BlockPos hatch) { this.controller = controller; this.hatch = hatch; }

    BlockPos controller() { return controller; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!isVehicle() || !(level().getBlockEntity(controller) instanceof StationModuleBlockEntity be) || !Station.TURRET.equals(be.type())) discard();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(hatch.getX() + 0.5, hatch.getY() + 0.2, hatch.getZ() + 0.5);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        controller = BlockPos.of(tag.getLong("controller")); hatch = BlockPos.of(tag.getLong("hatch"));
        getPersistentData().putBoolean("wmd_air", true);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putLong("controller", controller.asLong()); tag.putLong("hatch", hatch.asLong()); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldBeSaved() { return isVehicle(); }
}

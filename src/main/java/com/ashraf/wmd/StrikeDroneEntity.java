package com.ashraf.wmd;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class StrikeDroneEntity extends ReconDroneEntity {

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(StrikeDroneEntity.class, EntityDataSerializers.INT);

    Object loop;

    public StrikeDroneEntity(EntityType<? extends ReconDroneEntity> type, Level level) { super(type, level); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) DroneSound.tick(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 1);
    }

    void setKind(int kind) { entityData.set(KIND, kind); }

    public DroneType type() { int k = entityData.get(KIND); return k >= 1 && k <= 4 ? DroneType.values()[k - 1] : DroneType.T1; }

    public boolean big() { return entityData.get(KIND) >= 3; }
}

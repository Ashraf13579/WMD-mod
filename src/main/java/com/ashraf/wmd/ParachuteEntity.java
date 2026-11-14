package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ParachuteEntity extends Entity {

    private int age;

    public ParachuteEntity(EntityType<? extends ParachuteEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    void setup(Vec3 at, Vec3 vel, float yaw) {
        moveTo(at.x, at.y + 1.9, at.z, yaw, 0.0F);
        setDeltaMovement(vel);
        level().playSound(null, blockPosition(), WmdSounds.PALLET_CHUTE.get(), SoundSource.PLAYERS, Balance.SND_RELEASE, 1.15F);
    }

    public float chuteOpen(float pt) { return Math.min(1.0F, (tickCount + pt) / (float) Balance.EJECT_CHUTE_OPEN_TICKS); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        age++;
        if (getPassengers().isEmpty() && age > 2) { discard(); return; }
        Vec3 v = getDeltaMovement();
        float open = chuteOpen(0.0F);
        double fall = -Balance.EJECT_CHUTE_FALL - (1.0 - open) * 0.3;
        v = new Vec3(v.x * 0.93, Math.max(v.y - Balance.EJECT_GRAVITY * 0.5, fall), v.z * 0.93);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        for (Entity p : getPassengers()) p.resetFallDistance();
        boolean down = onGround() || isInWater() || getPassengers().stream().anyMatch(p -> p.onGround() || p.isInWater());
        if (down || age > Balance.EJECT_MAX_TICKS) { ejectPassengers(); discard(); }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) { return new Vec3(0.0, -1.9, 0.0); }

    @Override public boolean shouldRiderSit() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) { age = tag.getInt("age"); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("age", age); }
}

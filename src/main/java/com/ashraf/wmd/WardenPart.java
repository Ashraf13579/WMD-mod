package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

public class WardenPart extends PartEntity<WardenEntity> {

    final String name;
    private final EntityDimensions size;

    WardenPart(WardenEntity parent, String name, float width, float height) {
        super(parent);
        this.name = name;
        this.size = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { }
    @Override protected void readAdditionalSaveData(CompoundTag t) { }
    @Override protected void addAdditionalSaveData(CompoundTag t) { }
    @Override public boolean isPickable() { return true; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean is(Entity e) { return this == e || getParent() == e; }
    @Override public EntityDimensions getDimensions(Pose pose) { return size; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurtPart(this, source, amount);
    }
}

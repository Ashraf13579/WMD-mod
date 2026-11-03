package com.ashraf.wmd;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class PilotBodyEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PilotBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public PilotBodyEntity(EntityType<? extends PilotBodyEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(OWNER, Optional.empty()); }

    void setup(ServerPlayer p) {
        entityData.set(OWNER, Optional.of(p.getUUID()));
        setCustomName(p.getName());
        setCustomNameVisible(false);
    }

    public UUID owner() { return entityData.get(OWNER).orElse(null); }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl && tickCount % 20 == 3) {
            UUID o = owner();
            ServerPlayer p = o == null ? null : sl.getServer().getPlayerList().getPlayer(o);
            if (p == null || !DronePilots.piloting(p)) discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableTo(source)) return false;
        if (level() instanceof ServerLevel sl && owner() != null && sl.getServer().getPlayerList().getPlayer(owner()) instanceof ServerPlayer p) {
            DronePilots.bodyHit(p);
            p.hurt(source, amount);
        }
        return true;
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isAttackable() { return true; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

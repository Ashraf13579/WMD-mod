package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EscapePodEntity extends Entity {

    private static final EntityDataAccessor<Boolean> SURFACED = SynchedEntityData.defineId(EscapePodEntity.class, EntityDataSerializers.BOOLEAN);

    private int emptyTicks;
    private UUID owner;

    public EscapePodEntity(EntityType<? extends EscapePodEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static EscapePodEntity launch(ServerLevel sl, Vec3 at, UUID owner) {
        EscapePodEntity e = new EscapePodEntity(Wmd.ESCAPE_POD_ENTITY.get(), sl);
        e.setPos(at.x, at.y, at.z);
        e.owner = owner;
        sl.addFreshEntity(e);
        sl.sendParticles(ParticleTypes.BUBBLE, at.x, at.y + 1.0, at.z, 60, 0.6, 1.0, 0.6, 0.2);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(SURFACED, false); }

    public boolean surfaced() { return entityData.get(SURFACED); }

    @Override
    public void tick() {
        super.tick();
        Vec3 p = position();
        if (level().isClientSide) {
            if (!surfaced() && tickCount % 2 == 0 && level().getFluidState(BlockPos.containing(p.add(0, 1, 0))).is(FluidTags.WATER))
                level().addParticle(ParticleTypes.BUBBLE_COLUMN_UP, p.x + (random.nextDouble() - 0.5) * 1.2, p.y + random.nextDouble() * 2.0, p.z + (random.nextDouble() - 0.5) * 1.2, 0, 0.2, 0);
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        int top = NavalLayer.waterTop(sl, Mth.floor(p.x), Mth.floor(p.z));
        boolean wet = sl.getFluidState(BlockPos.containing(p.add(0, 0.5, 0))).is(FluidTags.WATER);
        double float_y = top == NavalLayer.NO_WATER ? Double.NaN : top + 1.0 - Balance.POD_DRAFT;
        Vec3 step;
        if (!Double.isNaN(float_y) && p.y < float_y - 0.05 && (wet || p.y < top + 1.0)) {
            step = new Vec3(0, Math.min(Balance.POD_RISE, float_y - p.y), 0);
            if (surfaced()) entityData.set(SURFACED, false);
        } else if (!Double.isNaN(float_y) && Math.abs(p.y - float_y) <= 0.3) {
            if (!surfaced()) {
                entityData.set(SURFACED, true);
                sl.playSound(null, p.x, p.y, p.z, WmdSounds.BUOY_SPLASH.get(), SoundSource.NEUTRAL, 2.0F, 0.7F);
                sl.sendParticles(ParticleTypes.SPLASH, p.x, top + 1.1, p.z, 80, 1.0, 0.2, 1.0, 0.3);
            }
            step = new Vec3(0, (float_y - p.y) * 0.2 + Math.sin(tickCount * 0.1) * 0.01, 0);
        } else {
            boolean ground = !sl.getBlockState(BlockPos.containing(p.add(0, -0.1, 0))).getCollisionShape(sl, BlockPos.containing(p.add(0, -0.1, 0))).isEmpty();
            step = ground ? Vec3.ZERO : new Vec3(0, -Balance.POD_FALL, 0);
        }
        setPos(p.add(step));
        setDeltaMovement(step);
        for (Entity r : getPassengers()) {
            if (r instanceof LivingEntity le) le.setAirSupply(le.getMaxAirSupply());
            r.fallDistance = 0.0F;
        }
        if (getPassengers().isEmpty()) {
            if (surfaced() && ++emptyTicks > Balance.POD_FLOAT_TICKS) discard();
        } else emptyTicks = 0;
        if (p.y < sl.getMinBuildHeight() - 16) discard();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().size() < Balance.POD_SEATS; }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        int i = getPassengers().indexOf(passenger);
        double a = Math.PI * 0.5 * i + Math.PI * 0.25;
        move.accept(passenger, getX() + Math.cos(a) * 0.35, getY() + 0.3, getZ() + Math.sin(a) * 0.35);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return position().add(0.0, Balance.POD_HEIGHT + 0.1, 0.0); }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || player.getVehicle() == this || getPassengers().size() >= Balance.POD_SEATS) return InteractionResult.PASS;
        if (!level().isClientSide) player.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !getPassengers().isEmpty()) return false;
        if (!(source.getEntity() instanceof Player)) return false;
        level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 1.0, getZ(), new ItemStack(Wmd.ESCAPE_CAPSULE.get())));
        discard();
        return true;
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 192.0 * 192.0; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        emptyTicks = tag.getInt("empty");
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("empty", emptyTicks);
        if (owner != null) tag.putUUID("owner", owner);
    }
}

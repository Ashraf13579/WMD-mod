package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SeabedCrawlerEntity extends Entity {

    private static final EntityDataAccessor<Float> RX = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RY = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RZ = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FX = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FY = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FZ = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEAD = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> MOVING = SynchedEntityData.defineId(SeabedCrawlerEntity.class, EntityDataSerializers.BOOLEAN);

    private int lastFed;

    public float headShown, headShownO;

    public SeabedCrawlerEntity(EntityType<? extends SeabedCrawlerEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(RX, 0.0F).define(RY, 0.0F).define(RZ, 0.0F).define(FX, 0.0F).define(FY, 0.0F).define(FZ, 0.0F).define(HEAD, 0.0F).define(MOVING, false);
    }

    void follow(Vec3 at, float yaw, Vec3 riser, Vec3 foot, float head, boolean moving) {
        setPos(at.x, at.y, at.z);
        setYRot(yaw);
        entityData.set(RX, (float) riser.x); entityData.set(RY, (float) riser.y); entityData.set(RZ, (float) riser.z);
        entityData.set(FX, (float) foot.x); entityData.set(FY, (float) foot.y); entityData.set(FZ, (float) foot.z);
        entityData.set(HEAD, head);
        entityData.set(MOVING, moving);
        lastFed = tickCount;
    }

    public Vec3 riser() { return new Vec3(entityData.get(RX), entityData.get(RY), entityData.get(RZ)); }
    public Vec3 foot() { return new Vec3(entityData.get(FX), entityData.get(FY), entityData.get(FZ)); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount - lastFed > 40) discard();
            return;
        }
        headShownO = headShown;
        headShown += (entityData.get(HEAD) - headShown) * 0.15F;
        if (entityData.get(MOVING) && tickCount % 2 == 0) {
            float yr = getYRot() * Mth.DEG_TO_RAD;
            double bx = getX() - Mth.sin(yr) * -1.8, bz = getZ() + Mth.cos(yr) * -1.8;
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), bx + (random.nextDouble() - 0.5) * 2.0, getY() + 0.2,
                    bz + (random.nextDouble() - 0.5) * 2.0, 0.0, 0.05, 0.0);
        }
        if (entityData.get(HEAD) > 0.5F && tickCount % 2 == 0)
            level().addParticle(ParticleTypes.BUBBLE, getX() + (random.nextDouble() - 0.5) * 3.0, getY() + 0.3, getZ() + (random.nextDouble() - 0.5) * 3.0, 0.0, 0.12, 0.0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 r = riser(), f = foot();
        return getBoundingBox().inflate(2.5).minmax(new AABB(r.x - 0.5, r.y - 0.5, r.z - 0.5, r.x + 0.5, r.y + 0.5, r.z + 0.5))
                .minmax(new AABB(f.x - 0.5, f.y - 0.5, f.z - 0.5, f.x + 0.5, f.y + 0.5, f.z + 0.5));
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 192.0 * 192.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    static Vec3 top(BlockPos floor) { return new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5); }
}

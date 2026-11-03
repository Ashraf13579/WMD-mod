package com.ashraf.wmd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class MissileEntity extends ThrowableItemProjectile {

    private static final double SPEED = 2.0;

    private static final int MAX_AGE_TICKS = 20 * 30;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Wmd.MISSILE.get();
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {

            Vec3 velocity = this.getDeltaMovement();
            if (velocity.lengthSqr() > 1.0E-6) {
                this.setDeltaMovement(velocity.normalize().scale(SPEED));
            }
            if (this.tickCount > MAX_AGE_TICKS) {
                this.discard();
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {

            this.discard();
        }
    }
}

package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class HandMissileEntity extends Entity {

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(HandMissileEntity.class, EntityDataSerializers.INT);

    private UUID owner;
    private HandLock.Target target;
    private Vec3 vel = Vec3.ZERO, lastAim, lastTarget;
    private double apexY;
    private boolean diving, lost;

    private int lerpSteps;
    private double lx, ly, lz;

    public HandMissileEntity(EntityType<? extends HandMissileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(KIND, 0); }

    public int kind() { return entityData.get(KIND); }

    private int ejectTicks() { return kind() == HandLock.MANPADS ? 4 : 6; }

    static void launch(ServerPlayer sp, int kind, HandLock.Target t) {
        ServerLevel level = sp.serverLevel();
        HandMissileEntity e = new HandMissileEntity(Wmd.HAND_MISSILE.get(), level);
        Vec3 look = sp.getLookAngle();
        Vec3 start = sp.getEyePosition().add(look.scale(1.4)).add(0, -0.15, 0);
        e.entityData.set(KIND, kind);
        e.setPos(start.x, start.y, start.z);
        e.owner = sp.getUUID();
        e.target = t;
        e.vel = look.scale(kind == HandLock.MANPADS ? 1.2 : 0.8);
        e.lastAim = t.pos(level);
        if (e.lastAim == null) e.lastAim = start.add(look.scale(50.0));
        double hd = Math.hypot(e.lastAim.x - start.x, e.lastAim.z - start.z);
        e.apexY = Math.max(start.y, e.lastAim.y) + Math.min(Balance.JAV_CLIMB, Math.max(12.0, hd * 0.45));
        level.addFreshEntity(e);
        level.playSound(null, start.x, start.y, start.z, WmdSounds.INT_T1_EJECT.get(), SoundSource.PLAYERS, 3.0F, kind == HandLock.MANPADS ? 1.2F : 0.9F);
        Vec3 back = sp.getEyePosition().subtract(look.scale(1.6));
        level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 16, 0.35, 0.35, 0.35, 0.06);
        level.sendParticles(ParticleTypes.POOF, start.x, start.y, start.z, 6, 0.1, 0.1, 0.1, 0.02);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (target == null) { discard(); return; }
        int life = kind() == HandLock.MANPADS ? Balance.MANPADS_LIFE : Balance.JAV_LIFE;
        if (tickCount > life) { burst(sl, position(), false); return; }
        if (tickCount == ejectTicks())
            sl.playSound(null, getX(), getY(), getZ(), WmdSounds.INT_T1_BOOST.get(), SoundSource.PLAYERS, 4.0F, kind() == HandLock.MANPADS ? 1.15F : 0.95F);
        Vec3 pos = position();
        Vec3 tp = lost ? null : target.pos(sl);
        if (tp != null && SmokeScreens.inside(sl, tp)) { tp = null; if (kind() == HandLock.MANPADS) lost = true; }
        Vec3 tvel = Vec3.ZERO;
        if (tp != null) { if (lastTarget != null) tvel = tp.subtract(lastTarget); lastTarget = tp; lastAim = tp; }
        else if (kind() == HandLock.JAVELIN) tp = lastAim;
        boolean manpads = kind() == HandLock.MANPADS;
        int ej = ejectTicks();
        double speed = manpads ? (tickCount < ej ? 1.2 : Math.min(Balance.MANPADS_SPEED, 1.2 + (tickCount - ej) * 0.6))
                : (tickCount < ej ? 0.8 : Math.min(Balance.JAV_SPEED, 0.8 + (tickCount - ej) * 0.3));
        Vec3 desired = vel;
        if (tickCount >= ej && tp != null) {
            if (manpads) {
                double t = Math.min(40.0, tp.distanceTo(pos) / Math.max(0.5, speed));
                desired = tp.add(tvel.scale(t)).subtract(pos);
            } else {
                Vec3 h = new Vec3(tp.x - pos.x, 0.0, tp.z - pos.z);
                double hd = h.length();
                if (!diving && (pos.y >= apexY || hd < (pos.y - tp.y) * 0.8)) diving = true;
                desired = diving || hd < 1.0E-3 ? tp.subtract(pos) : h.scale(1.0 / hd).add(0.0, 1.3, 0.0);
            }
        }
        double turn = Math.toRadians(manpads ? Balance.MANPADS_TURN : Balance.JAV_TURN);
        vel = turnToward(vel.normalize(), desired.normalize(), turn).scale(speed);
        Vec3 next = pos.add(vel);

        if (tp != null && !lost && tickCount >= ej) {
            double d = segDist(pos, next, tp);
            if (manpads && target.type() == HandLock.T_SHIP) {
                SableBridge.ShipInfo s = SableBridge.active() ? SableBridge.shipInfo(sl, target.ship()) : null;
                if (s != null) d = Math.min(d, Math.sqrt(s.worldBounds().distanceToSqr(next)));
            }
            if (d <= (manpads ? Balance.MANPADS_FUSE : 1.5)) { hitTarget(sl, manpads ? next : tp); return; }
        }

        if (SableBridge.active() && !SableBridge.shipsAlongSegment(sl, pos, next).isEmpty()) {
            SableBridge.ShipInfo s = SableBridge.shipsAlongSegment(sl, pos, next).get(0);
            ShipStrike.blast(sl, pos, (manpads ? Balance.MANPADS_SHIP_POWER : Balance.JAV_POWER) * 1.75, (float) (manpads ? Balance.MANPADS_SHIP_POWER : Balance.JAV_POWER), owner);
            Wmd.LOGGER.info("Hand missile hit the airship {}", s.name());
            burst(sl, pos, true);
            return;
        }

        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK && tickCount > 2) {
            Vec3 at = hit.getLocation();
            if (manpads) burst(sl, at, false);
            else impact(sl, at);
            return;
        }
        if (!ForcefieldBlockEntity.LOADED.isEmpty()) {
            Forcefield.Crossing c = Forcefield.crossing(sl, pos, next);
            if (c != null && Forcefield.shellHit(sl, c, 1, WarheadType.HE)) { discard(); return; }
        }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private static double segDist(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr(), t = l2 < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / l2));
        return a.add(ab.scale(t)).distanceTo(p);
    }

    private static Vec3 turnToward(Vec3 dir, Vec3 want, double max) {
        double c = Math.max(-1.0, Math.min(1.0, dir.dot(want))), a = Math.acos(c);
        if (a <= max || a < 1.0E-6) return want;
        Vec3 axis = dir.cross(want);
        if (axis.lengthSqr() < 1.0E-9) axis = Math.abs(dir.y) < 0.9 ? dir.cross(new Vec3(0, 1, 0)) : dir.cross(new Vec3(1, 0, 0));
        axis = axis.normalize();

        return dir.scale(Math.cos(max)).add(axis.cross(dir).scale(Math.sin(max))).add(axis.scale(axis.dot(dir) * (1.0 - Math.cos(max)))).normalize();
    }

    private void hitTarget(ServerLevel sl, Vec3 at) {
        if (kind() == HandLock.JAVELIN) { impact(sl, at); return; }
        ServerPlayer shooter = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
        switch (target.type()) {
            case HandLock.T_SHIP -> ShipStrike.blast(sl, at, Balance.MANPADS_SHIP_POWER * 1.75, (float) Balance.MANPADS_SHIP_POWER, owner);
            case HandLock.T_DRONE -> { if (sl.getEntity(target.entity()) instanceof ReconDroneEntity d) Drones.hurt(sl, d.droneId(), 10_000.0F, shooter); }
            case HandLock.T_CRUISE -> {
                FlightManager mgr = FlightManager.get(sl);
                Flight f = mgr.find(target.flight());
                if (f != null) mgr.kill(sl, f);
            }
            default -> {}
        }
        Wmd.LOGGER.info("MANPADS hit: target type {} at {} {} {}", target.type(), (int) at.x, (int) at.y, (int) at.z);
        burst(sl, at, true);
    }

    private void impact(ServerLevel sl, Vec3 at) {
        sl.explode(this, at.x, at.y, at.z, (float) Balance.JAV_POWER, Config.Server.blockDamage() && Config.Server.canBreak(sl, net.minecraft.core.BlockPos.containing(at)) ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        sl.playSound(null, at.x, at.y, at.z, WmdSounds.HE_NEAR.get(), SoundSource.BLOCKS, 4.0F, 1.1F);
        discard();
    }

    private void burst(ServerLevel sl, Vec3 at, boolean kill) {
        PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 800, new InterceptFxPayload(at.x, at.y, at.z, kill, 0));
        discard();
    }

    private void clientTick() {
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
            lerpSteps--;
        }
        Vec3 p = position(), q = new Vec3(xo, yo, zo), d = p.subtract(q);
        if (d.lengthSqr() < 1.0E-6) return;
        RandomSource r = random;
        boolean motor = tickCount >= ejectTicks();
        Vec3 dir = d.normalize(), tail = p.subtract(dir.scale(kind() == HandLock.MANPADS ? 0.8 : 0.6));
        if (motor) {
            level().addParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x, tail.y, tail.z, -dir.x * 0.1, -dir.y * 0.1, -dir.z * 0.1);
            int n = (int) Math.min(8, Math.ceil(d.length() / 0.8));
            for (int i = 0; i < n; i++) {
                Vec3 t = tail.subtract(d.scale(i / (double) n));
                level().addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, t.x + r.nextGaussian() * 0.05, t.y + r.nextGaussian() * 0.05, t.z + r.nextGaussian() * 0.05, 0, 0, 0);
            }
            VeilBridge.glow(0x4800_0000_0000_0000L ^ getId(), tail.x, tail.y, tail.z, 1.0F, 0.62F, 0.25F, 10.0F, 2.2F, level().getGameTime());
        } else {
            level().addParticle(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 0, 0.01, 0);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lx = x; ly = y; lz = z;
        lerpSteps = Math.max(1, steps);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 640.0 * 640.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

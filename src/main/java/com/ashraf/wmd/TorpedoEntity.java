package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TorpedoEntity extends Entity {

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TorpedoEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DROP = SynchedEntityData.defineId(TorpedoEntity.class, EntityDataSerializers.INT);
    static final int DROP_NONE = 0, DROP_FALL = 1, DROP_CHUTE = 2;

    private UUID owner, ownShip;
    private Vec3 heading = new Vec3(0, 0, -1), vel = Vec3.ZERO, start;
    private double depth = -1.0, run;

    private Vec3 course;
    private boolean wasWet;

    private Vec3 aim;
    private long aimSeen = -1000L, nextSeek;
    private int wireFreq = -1;

    private Vec3 t4Point;
    private int t4Phase;
    private long t4SearchAt;
    private UUID t4Lock;
    private boolean announced;

    private boolean netRipped;

    private int airTicks;

    int chuteSeen = -1;

    private int lerpSteps;
    private double lx, ly, lz;

    public TorpedoEntity(EntityType<? extends TorpedoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(KIND, 0); builder.define(DROP, DROP_NONE); }

    public int drop() { return entityData.get(DROP); }

    public TorpedoKind kind() { return TorpedoKind.of(entityData.get(KIND)); }

    public UUID owner() { return owner; }

    void wire(int freq) {
        if (kind() == TorpedoKind.T3 || kind().drone()) wireFreq = freq;
        if (kind() == TorpedoKind.T4 && level() instanceof ServerLevel sl) t4Point = FireControlNet.aimOf(sl, owner, freq);
    }

    static TorpedoEntity launch(ServerLevel level, TorpedoKind kind, Vec3 at, Vec3 dir, double speed0, double runDepth, UUID owner, UUID ownShip) {
        return launch(level, kind, at, dir, speed0, runDepth, owner, ownShip, null);
    }

    static TorpedoEntity launch(ServerLevel level, TorpedoKind kind, Vec3 at, Vec3 dir, double speed0, double runDepth, UUID owner, UUID ownShip, Vec3 course) {
        TorpedoEntity e = new TorpedoEntity(Wmd.TORPEDO.get(), level);
        e.entityData.set(KIND, kind.ordinal());
        e.setPos(at.x, at.y, at.z);
        Vec3 h = new Vec3(dir.x, 0.0, dir.z);
        e.heading = h.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : h.normalize();
        e.vel = dir.normalize().scale(speed0);
        e.depth = runDepth;
        e.owner = owner;
        e.ownShip = ownShip;
        e.start = at;
        e.course = course == null ? null : new Vec3(course.x, 0.0, course.z).normalize();
        level.addFreshEntity(e);
        level.playSound(null, at.x, at.y, at.z, WmdSounds.TP_TUBE_FIRE.get(), SoundSource.BLOCKS, 3.0F, 0.95F + level.random.nextFloat() * 0.1F);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 40, 0.4, 0.4, 0.4, 0.2);
        NoiseNet.pulse(level, at, Balance.TORP_DB + 10.0, NoiseNet.Kind.LAUNCH, owner, 40);
        if (kind == TorpedoKind.T4) SeaSound.nf(level, WmdSounds.T4_LAUNCH.get(), WmdSounds.T4_LAUNCH_FAR.get(), 64.0, at, 6.0F, 1.0F);
        return e;
    }

    static TorpedoEntity airDrop(ServerLevel level, TorpedoKind kind, Vec3 at, Vec3 v, Vec3 fwd, UUID owner, UUID ownShip) {
        TorpedoEntity e = new TorpedoEntity(Wmd.TORPEDO.get(), level);
        e.entityData.set(KIND, kind.ordinal());
        e.entityData.set(DROP, DROP_FALL);
        e.setPos(at.x, at.y, at.z);
        Vec3 h = new Vec3(v.x, 0.0, v.z);
        if (h.lengthSqr() < 0.04 && fwd != null) h = new Vec3(fwd.x, 0.0, fwd.z);
        e.heading = h.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : h.normalize();
        e.vel = v;
        e.depth = -1.0;
        e.owner = owner;
        e.ownShip = ownShip;
        e.start = at;
        level.addFreshEntity(e);
        return e;
    }

    private boolean airTick(ServerLevel sl, Vec3 pos) {
        if (sl.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER)) {
            entityData.set(DROP, DROP_NONE);
            vel = new Vec3(vel.x, Math.max(vel.y, -0.4), vel.z);
            sl.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6, pos.z, 40, 0.6, 0.2, 0.6, 0.3);
            sl.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 30, 0.4, 0.4, 0.4, 0.2);
            sl.playSound(null, pos.x, pos.y, pos.z, WmdSounds.BUOY_SPLASH.get(), SoundSource.NEUTRAL, Balance.SND_MECH, 0.8F);
            NoiseNet.pulse(sl, pos, Balance.TORP_DB + 6.0, NoiseNet.Kind.LAUNCH, owner, 30);
            return false;
        }
        airTicks++;
        int ph = entityData.get(DROP);
        if (ph == DROP_FALL && airTicks >= Balance.ATORP_CHUTE_DELAY) {
            entityData.set(DROP, DROP_CHUTE);
            ph = DROP_CHUTE;
            sl.playSound(null, pos.x, pos.y, pos.z, WmdSounds.CHUTE_OPEN.get(), SoundSource.NEUTRAL, Balance.SND_MECH, 1.1F);
        }
        if (ph == DROP_CHUTE) {
            double vy = vel.y + Mth.clamp(-Balance.ATORP_CHUTE_FALL - vel.y, -0.08, 0.12);
            Vec3 hz = new Vec3(vel.x, 0.0, vel.z).scale(0.9);
            if (hz.length() < Balance.ATORP_CHUTE_DRIFT) hz = heading.scale(Balance.ATORP_CHUTE_DRIFT);
            vel = new Vec3(hz.x, vy, hz.z);
        } else vel = UnderwaterMotion.coast(sl, pos, vel);
        Vec3 next = pos.add(vel);
        if (SableBridge.active()) {
            java.util.List<SableBridge.ShipInfo> cands = new java.util.ArrayList<>();
            for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, pos, next)) if (!s.id().equals(ownShip) || airTicks > 40) cands.add(s);
            ProjectilePath.PathHit h = cands.isEmpty() ? null : SableBridge.exactHit(sl, pos, next, cands);
            if (h != null) { dud(sl, h.pos()); return true; }
        }
        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) { dud(sl, hit.getLocation()); return true; }
        if (airTicks > Balance.ATORP_MAX_AIR_TICKS || next.y < sl.getMinBuildHeight()) { discard(); return true; }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (start == null) { discard(); return; }
        held = RunnerChunks.follow(sl, getUUID(), position(), getDeltaMovement().lengthSqr() > 1e-6 ? getDeltaMovement() : vel, held);
        TorpedoKind k = kind();
        if (k.drone()) { droneTick(sl, k); return; }
        if (k == TorpedoKind.ATT) { AntiTorpedo.tick(this, sl); return; }
        Vec3 pos = position();
        if (entityData.get(DROP) != DROP_NONE && airTick(sl, pos)) return;
        if (run >= k.range()) { detonate(sl, pos, null, "end"); return; }
        if (tickCount - airTicks > (int) (k.range() / Math.max(0.1, k.speed())) + 600 || pos.y < sl.getMinBuildHeight()) { discard(); return; }
        boolean wet = sl.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (!wet) {
            vel = UnderwaterMotion.coast(sl, pos, vel);
        } else {
            if (!wasWet && depth <= 0.0) depth = Math.max(Balance.TORP_MIN_DEPTH, NavalLayer.depthBelowSurface(sl, pos));
            if (!wasWet && vel.horizontalDistanceSqr() > 1e-6) heading = new Vec3(vel.x, 0.0, vel.z).normalize();
            if (course != null && run >= Balance.GYRO_STRAIGHT) {
                double cur = Math.atan2(heading.x, -heading.z), want0 = Math.atan2(course.x, -course.z);
                double dA = Mth.wrapDegrees(Math.toDegrees(want0 - cur));
                double step = Mth.clamp(dA, -Balance.GYRO_RATE, Balance.GYRO_RATE);
                double na = cur + Math.toRadians(step);
                heading = new Vec3(Math.sin(na), 0.0, -Math.cos(na));
                if (Math.abs(dA) < 0.5) course = null;
            }
            double want = Mth.clamp(depth <= 0.0 ? Balance.TORP_MIN_DEPTH : depth, Balance.TORP_MIN_DEPTH, Balance.TORP_MAX_DEPTH);
            if (course == null && run >= Balance.TORP_ARM && k.homing()) {
                Vec3 goal = guide(sl, pos, k);
                if (goal != null) {
                    double cur = Math.atan2(heading.x, -heading.z), want0 = Math.atan2(goal.x - pos.x, -(goal.z - pos.z));
                    double dA = Mth.wrapDegrees(Math.toDegrees(want0 - cur));
                    if (Math.hypot(goal.x - pos.x, goal.z - pos.z) > 1.5) {
                        double na = cur + Math.toRadians(Mth.clamp(dA, -Balance.TORP_TURN_RATE, Balance.TORP_TURN_RATE));
                        heading = new Vec3(Math.sin(na), 0.0, -Math.cos(na));
                    }
                    want = Mth.clamp(NavalLayer.depthBelowSurface(sl, goal), Balance.TORP_MIN_DEPTH, Balance.TORP_MAX_DEPTH);
                }
            }
            double speed = k.speed();
            if (k == TorpedoKind.T4 && course == null) {
                double[] w = { want };
                speed = t4Steer(sl, pos, k, w);
                want = w[0];
            }
            Vec3 target = UnderwaterMotion.run(sl, pos, heading, speed, want, Balance.TORP_MAX_VY);
            vel = vel.add(target.subtract(vel).scale(0.35));
        }
        wasWet = wet;
        Vec3 next = pos.add(vel);
        double step = vel.length();
        boolean armed = run >= Balance.TORP_ARM;

        BlockHitResult nh = TorpedoNetBlock.clip(sl, pos, next, this);
        SableBridge.ShipInfo shipBehindNet = null;
        Vec3 shipBehindAt = null;

        if (SableBridge.active()) {

            java.util.List<SableBridge.ShipInfo> cands = new java.util.ArrayList<>();
            for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, pos, next)) if (!s.id().equals(ownShip)) cands.add(s);
            ProjectilePath.PathHit h = SableBridge.exactHit(sl, pos, next.add(vel.normalize().scale(0.6)), cands);
            if (h != null && (nh == null || h.pos().distanceToSqr(pos) <= nh.getLocation().distanceToSqr(pos))) {
                SableBridge.ShipInfo s = cands.stream().filter(c -> c.id().equals(h.shipId())).findFirst().orElse(cands.get(0));
                Vec3 at = h.pos().subtract(vel.normalize().scale(0.3));
                if (armed) detonate(sl, at, s, "hit"); else dud(sl, at);
                return;
            }
            if (h != null) {
                shipBehindNet = cands.stream().filter(c -> c.id().equals(h.shipId())).findFirst().orElse(cands.get(0));
                shipBehindAt = h.pos().subtract(vel.normalize().scale(0.3));
            }

            if (armed && tickCount % 2 == 0) {
                for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(next, next).inflate(Balance.TORP_KEEL_SIDE, Balance.TORP_KEEL_DEPTH, Balance.TORP_KEEL_SIDE))) {
                    if (s.id().equals(ownShip)) continue;
                    AABB b = s.worldBounds();
                    if (next.x < b.minX - Balance.TORP_KEEL_SIDE || next.x > b.maxX + Balance.TORP_KEEL_SIDE || next.z < b.minZ - Balance.TORP_KEEL_SIDE || next.z > b.maxZ + Balance.TORP_KEEL_SIDE) continue;

                    if (b.minY >= next.y - 0.5 && b.minY - next.y <= Balance.TORP_KEEL_DEPTH) {
                        ProjectilePath.PathHit up = SableBridge.exactHit(sl, next, new Vec3(next.x, b.maxY + 0.5, next.z), java.util.List.of(s));
                        if (up != null && up.pos().y - next.y <= Balance.TORP_KEEL_DEPTH + 0.5) { detonate(sl, new Vec3(next.x, Math.max(next.y, up.pos().y - 0.5), next.z), s, "keel"); return; }
                    }
                }
            }
        }

        for (int i = 0; nh != null && i < 4; i++) {
            if (!armed) { dud(sl, nh.getLocation()); return; }
            if (k != TorpedoKind.T4 || !TorpedoNetBlock.rip(sl, nh.getBlockPos(), nh.getLocation())) { netBlast(sl, nh.getLocation()); return; }
            if (!netRipped) { netRipped = true; Wmd.LOGGER.info("Torpedo {} ripped through a net at {}", k.id, nh.getBlockPos().toShortString()); }
            nh = TorpedoNetBlock.clip(sl, pos, next, this);
        }
        if (shipBehindNet != null) { detonate(sl, shipBehindAt, shipBehindNet, "hit"); return; }

        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (armed) detonate(sl, hit.getLocation(), null, "ground"); else dud(sl, hit.getLocation());
            return;
        }
        if (tickCount % Balance.TORP_NOISE_TICKS == 0) NoiseNet.emit(sl, getUUID(), next, k.db(), NoiseNet.Kind.TORPEDO, owner, null);
        run += step;
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private Vec3 guide(ServerLevel sl, Vec3 pos, TorpedoKind k) {
        long now = sl.getGameTime();
        if (wireFreq >= 0) {
            if (run > Balance.TORP_WIRE_LEN) { wireFreq = -1; tell(sl, Component.translatable("message.wmd.torpedo.wire_cut", Component.translatable("item.wmd." + k.id))); }
            else {
                Vec3 w = FireControlNet.aimOf(sl, owner, wireFreq);
                if (w != null && w.distanceTo(pos) > Balance.TORP_ACTIVE_RANGE) return w;
            }
        }
        if (now >= nextSeek) {
            nextSeek = now + Balance.TORP_SEEK_TICKS;
            Vec3 best = null;
            double bestLv = -1e9;
            for (NoiseNet.Heard h : NoiseNet.heard(sl, pos, Balance.TORP_SEEK_RANGE, Balance.NOISE_FLOOR_DB, ownShip, getUUID())) {
                NoiseNet.Kind kd = h.src().kind();
                if (kd == NoiseNet.Kind.TORPEDO || kd == NoiseNet.Kind.LAUNCH || kd == NoiseNet.Kind.BLAST) continue;
                if (!inCone(pos, h.src().pos())) continue;
                if (h.level() > bestLv) { bestLv = h.level(); best = h.src().pos(); }
            }
            if (best == null && SableBridge.active()) {
                double bd = Double.MAX_VALUE;
                for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(pos, pos).inflate(Balance.TORP_ACTIVE_RANGE))) {
                    if (s.id().equals(ownShip) || !NavalLayer.inWater(sl, s)) continue;
                    Vec3 c = s.worldBounds().getCenter();
                    double d = c.distanceTo(pos);
                    if (d < bd && inCone(pos, c) && d <= Balance.TORP_ACTIVE_RANGE * Anechoic.pingFactor(sl, s.id())) { bd = d; best = c; }
                }
                if (now % 40 < Balance.TORP_SEEK_TICKS) {
                    NoiseNet.pulse(sl, pos, Balance.TORP_PING_DB, NoiseNet.Kind.PING, owner, 20);
                    sl.playSound(null, pos.x, pos.y, pos.z, WmdSounds.TP_SEEKER.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
                }
            }
            if (best != null) {
                aim = best;
                aimSeen = now;
                if (!announced) { announced = true; tell(sl, Component.translatable("message.wmd.torpedo.locked", Component.translatable("item.wmd." + k.id), Mth.floor(best.x), Mth.floor(best.y), Mth.floor(best.z))); }
            }
        }
        return aim != null && now - aimSeen <= Balance.TORP_LOST_TICKS ? aim : null;
    }

    private double t4Steer(ServerLevel sl, Vec3 pos, TorpedoKind k, double[] w) {
        long now = sl.getGameTime();
        Vec3 goal = null;
        double turn = Balance.T4_SPRINT_TURN, speed = k.speed();
        if (t4Phase == 0) {
            boolean near = t4Point != null && Math.hypot(t4Point.x - pos.x, t4Point.z - pos.z) <= Balance.T4_SEARCH_DIST;
            if (near || (t4Point == null && run >= k.range() * Balance.T4_SEARCH_AT)) {
                t4Phase = 1;
                t4SearchAt = now;
                NoiseNet.pulse(sl, pos, Balance.T4_PING_DB, NoiseNet.Kind.PING, owner, 20);
                sl.playSound(null, pos.x, pos.y, pos.z, WmdSounds.TP_SEEKER.get(), SoundSource.BLOCKS, 4.0F, 0.8F);
            } else goal = t4Point;
        }
        if (t4Phase == 1) {
            speed = Balance.T4_SEARCH_SPEED;
            if (SableBridge.active() && (now - t4SearchAt) % 5 == 0) {
                double bd = Double.MAX_VALUE;
                for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(pos, pos).inflate(Balance.T4_SEEK_RANGE))) {
                    if (s.id().equals(ownShip) || !NavalLayer.inWater(sl, s)) continue;
                    Vec3 c = s.worldBounds().getCenter();
                    double dx = c.x - pos.x, dz = c.z - pos.z, l = Math.hypot(dx, dz), d = c.distanceTo(pos);
                    if (l > 1.0 && (dx * heading.x + dz * heading.z) / l < Math.cos(Math.toRadians(Balance.T4_SEEK_CONE))) continue;
                    if (d < bd && d <= Balance.T4_SEEK_RANGE * Anechoic.pingFactor(sl, s.id())) { bd = d; t4Lock = s.id(); }
                }
                if (t4Lock != null) {
                    t4Phase = 2;
                    tell(sl, Component.translatable("message.wmd.torpedo.locked", Component.translatable("item.wmd." + k.id), Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z)));
                }
            }
            if (t4Phase == 1 && now - t4SearchAt > Balance.T4_SEARCH_TICKS) t4Phase = 3;
        }
        if (t4Phase == 2) {
            SableBridge.ShipInfo s = t4Lock == null ? null : SableBridge.shipInfo(sl, t4Lock);
            if (s == null) t4Phase = 3;
            else { goal = s.worldBounds().getCenter(); turn = Balance.T4_TURN_RATE; }
        }
        if (goal != null) {
            double cur = Math.atan2(heading.x, -heading.z), want0 = Math.atan2(goal.x - pos.x, -(goal.z - pos.z));
            double dA = Mth.wrapDegrees(Math.toDegrees(want0 - cur));
            if (Math.hypot(goal.x - pos.x, goal.z - pos.z) > 1.5) {
                double na = cur + Math.toRadians(Mth.clamp(dA, -turn, turn));
                heading = new Vec3(Math.sin(na), 0.0, -Math.cos(na));
            }
            w[0] = Mth.clamp(NavalLayer.depthBelowSurface(sl, goal), Balance.TORP_MIN_DEPTH, Balance.TORP_MAX_DEPTH);
        }
        return speed;
    }

    private boolean inCone(Vec3 pos, Vec3 p) {
        double dx = p.x - pos.x, dz = p.z - pos.z, l = Math.hypot(dx, dz);
        if (l < 1.0) return true;
        return (dx * heading.x + dz * heading.z) / l >= Math.cos(Math.toRadians(Balance.TORP_SEEK_CONE));
    }

    private boolean onStation;

    private void droneTick(ServerLevel sl, TorpedoKind k) {
        Vec3 pos = position();
        if (tickCount > Balance.UUV_LIFE || pos.y < sl.getMinBuildHeight()) {
            tell(sl, Component.translatable("message.wmd.uuv.gone", Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z)));
            discard();
            return;
        }
        boolean wet = sl.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (!wet) vel = UnderwaterMotion.coast(sl, pos, vel);
        else {
            if (!wasWet && depth <= 0.0) depth = Math.max(Balance.TORP_MIN_DEPTH, NavalLayer.depthBelowSurface(sl, pos));
            if (!wasWet && vel.horizontalDistanceSqr() > 1e-6) heading = new Vec3(vel.x, 0.0, vel.z).normalize();
            if (course != null && run >= Balance.GYRO_STRAIGHT) {
                double cur = Math.atan2(heading.x, -heading.z), want0 = Math.atan2(course.x, -course.z);
                double dA = Mth.wrapDegrees(Math.toDegrees(want0 - cur));
                double na = cur + Math.toRadians(Mth.clamp(dA, -Balance.GYRO_RATE, Balance.GYRO_RATE));
                heading = new Vec3(Math.sin(na), 0.0, -Math.cos(na));
                if (Math.abs(dA) < 0.5) course = null;
            }
            if (!onStation && run >= k.range()) station(sl, pos);
            double want = Mth.clamp(depth <= 0.0 ? Balance.TORP_MIN_DEPTH : depth, Balance.TORP_MIN_DEPTH, Balance.TORP_MAX_DEPTH);
            Vec3 target = UnderwaterMotion.run(sl, pos, heading, onStation ? 0.0 : k.speed(), want, Balance.TORP_MAX_VY);
            vel = vel.add(target.subtract(vel).scale(0.25));
        }
        wasWet = wet;
        Vec3 next = pos.add(vel);
        boolean blocked = false;
        if (SableBridge.active()) for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, pos, next)) if (!s.id().equals(ownShip)) { blocked = true; break; }
        if (!blocked && sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.BLOCK) blocked = true;
        if (blocked) { vel = Vec3.ZERO; next = pos; if (!onStation) station(sl, pos); }
        if (tickCount % Balance.TORP_NOISE_TICKS == 0 && !onStation) NoiseNet.emit(sl, getUUID(), next, k.db(), NoiseNet.Kind.ENGINE, owner, null);
        if (wet && wireFreq >= 0 && (sl.getGameTime() + getId()) % Balance.SONAR_SCAN_TICKS == 0) {
            SonarNet.beat(sl, owner, wireFreq, sensorId(), next, SonarNet.UUV, Balance.UUV_RANGE, null);
            java.util.List<NoiseNet.Heard> h = new java.util.ArrayList<>();
            for (NoiseNet.Heard x : NoiseNet.heard(sl, next, Balance.UUV_RANGE, Balance.NOISE_FLOOR_DB, null, getUUID())) {
                double lv = x.level() - SonarNet.layerLoss(sl, next, x.src().pos());
                if (lv >= Balance.NOISE_FLOOR_DB) h.add(new NoiseNet.Heard(x.src(), lv, x.dist(), x.bearing()));
            }
            SonarNet.report(sl, owner, wireFreq, sensorId(), next, h);
        }
        run += vel.length();
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private void station(ServerLevel sl, Vec3 pos) {
        onStation = true;
        NoiseNet.silence(sl, getUUID());
        tell(sl, Component.translatable("message.wmd.uuv.station", Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z), (int) Math.round(run)));
    }

    private long sensorId() { return getUUID().getMostSignificantBits() | Long.MIN_VALUE; }

    private long[] held;

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && kind().drone() && wireFreq >= 0) { SonarNet.forget(sl, owner, wireFreq, sensorId()); NoiseNet.silence(sl, getUUID()); }
        if (level() instanceof ServerLevel sl) held = RunnerChunks.release(sl, getUUID(), held);
        super.remove(reason);
    }

    private void dud(ServerLevel sl, Vec3 at) {
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 0.6F);
        sl.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.05);
        tell(sl, Component.translatable("message.wmd.torpedo.dud", Component.translatable("item.wmd." + kind().id)));
        discard();
    }

    private void detonate(ServerLevel sl, Vec3 at, SableBridge.ShipInfo ship, String why) {
        if (isRemoved()) return;
        TorpedoKind k = kind();
        discard();
        int hull = ShipStrike.blast(sl, at, Balance.TORP_REACH, k.power(), owner);

        UnderwaterBlast.go(sl, at, k == TorpedoKind.T4 ? UnderwaterBlast.T4 : UnderwaterBlast.TORPEDO, k.power(), owner, this);
        int d = start == null ? 0 : (int) Math.round(start.distanceTo(at));
        tell(sl, Component.translatable("message.wmd.torpedo." + why, Component.translatable("item.wmd." + k.id), Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), d));
        Wmd.LOGGER.info("Torpedo {} {} at {} {} {} after {} blocks{}, {} hull blast(s); ship box {}", k.id, why, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), d,
                ship != null ? " (ship " + ship.id() + ", " + ship.blockCount() + " blocks, submerged " + NavalLayer.submerged(sl, ship) + ")" : "", hull, ship != null ? ship.worldBounds() : "-");
    }

    private void netBlast(ServerLevel sl, Vec3 at) {
        if (isRemoved()) return;
        TorpedoKind k = kind();
        discard();
        int torn = TorpedoNetBlock.tear(sl, at);
        int hull = TorpedoNetBlock.shipNear(sl, at) ? ShipStrike.blast(sl, at, Balance.TORP_REACH, k.power(), owner) : 0;
        UnderwaterBlast.go(sl, at, k == TorpedoKind.T4 ? UnderwaterBlast.T4 : UnderwaterBlast.TORPEDO, k.power(), owner, this);
        int d = start == null ? 0 : (int) Math.round(start.distanceTo(at));
        tell(sl, Component.translatable("message.wmd.torpedo.net", Component.translatable("item.wmd." + k.id), Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), d));
        Wmd.LOGGER.info("Torpedo {} stopped by a net at {} {} {} after {} blocks, {} net pieces torn, {} hull blast(s)", k.id, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), d, torn, hull);
    }

    private void tell(ServerLevel sl, Component c) {
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p) p.displayClientMessage(c, false);
    }

    Object loop;
    private void runLoop() {
        if (drop() != DROP_NONE) return;
        TorpedoKind k = kind();
        net.minecraft.sounds.SoundEvent s = k == TorpedoKind.T4 ? WmdSounds.T4_RUN_LOOP.get() : k.drone() ? WmdSounds.UUV_RUN_LOOP.get() : WmdSounds.TP_RUN_LOOP.get();
        float v = k == TorpedoKind.T4 ? 6.0F : k.drone() ? 1.0F : 2.0F;
        loop = LoopSound.keep(loop, tickCount, s, v, 1.0F, this::position, () -> !isRemoved());
    }

    private void clientTick() {
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
            lerpSteps--;
        }
        runLoop();
        Vec3 p = position(), d = p.subtract(new Vec3(xo, yo, zo));
        if (d.lengthSqr() < 1e-4) return;
        trail(p, d.normalize());
    }

    private void trail(Vec3 p, Vec3 f) {
        Level l = level();
        Vec3 tail = p.subtract(f.scale(TAIL));
        if (!l.getFluidState(BlockPos.containing(tail)).is(FluidTags.WATER)) return;
        TorpedoKind kd = kind();
        if (kd == TorpedoKind.UUV) {
            Vec3 t = p.subtract(f.scale(1.2));
            if (tickCount % 2 == 0) l.addParticle(ParticleTypes.BUBBLE, t.x, t.y, t.z, -f.x * 0.05, 0.02, -f.z * 0.05);
            return;
        }
        if (kd == TorpedoKind.T4) {
            Vec3 up = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 a = f.cross(up).normalize(), b = f.cross(a).normalize();
            for (int i = 0; i < 8; i++) {
                double along = 1.6 - random.nextDouble() * 3.4, ang = random.nextDouble() * Math.PI * 2.0, r = 0.42;
                Vec3 o = p.add(f.scale(along)).add(a.scale(Math.cos(ang) * r)).add(b.scale(Math.sin(ang) * r));
                l.addParticle(ParticleTypes.BUBBLE, o.x, o.y, o.z, -f.x * 0.3, 0.01, -f.z * 0.3);
            }
            for (int i = 0; i < 6; i++) {
                Vec3 o = tail.subtract(f.scale(random.nextDouble() * 2.0));
                l.addParticle(ParticleTypes.BUBBLE, o.x, o.y, o.z, -f.x * 0.6 + (random.nextDouble() - 0.5) * 0.1, 0.02, -f.z * 0.6 + (random.nextDouble() - 0.5) * 0.1);
            }
            for (int i = 0; i < 2; i++) {
                Vec3 w = tail.subtract(f.scale(i * 2.0));
                l.addAlwaysVisibleParticle(WmdParticles.TORPEDO_WAKE.get(), true, w.x, w.y, w.z, -f.x * 0.05, 0.006, -f.z * 0.05);
            }
            int top = NavalLayer.waterTop(l, Mth.floor(tail.x), Mth.floor(tail.z));
            if (top != NavalLayer.NO_WATER && top + 1.0 - tail.y <= SURFACE_TRACK * 2) {
                double sy = top + 1.02;
                l.addAlwaysVisibleParticle(WmdParticles.TORPEDO_WAKE.get(), true, tail.x, sy, tail.z, 0, 0.003, 0);
                if (random.nextInt(2) == 0) l.addParticle(ParticleTypes.SPLASH, tail.x, sy, tail.z, 0, 0.08, 0);
            }
            return;
        }
        Vec3 up = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = f.cross(up).normalize(), b = f.cross(a).normalize();
        double spin = tickCount * 0.9;
        for (int k = 0; k < 2; k++) {
            double ang = (k == 0 ? spin : -spin) + k * Math.PI, r = 0.22 + 0.06 * k;
            for (int i = 0; i < 2; i++) {
                double t = ang + i * Math.PI;
                Vec3 o = tail.subtract(f.scale(0.15 * k)).add(a.scale(Math.cos(t) * r)).add(b.scale(Math.sin(t) * r));
                Vec3 v = f.scale(-0.08).add(a.scale(-Math.sin(t) * 0.04)).add(b.scale(Math.cos(t) * 0.04));
                l.addParticle(ParticleTypes.BUBBLE, o.x, o.y, o.z, v.x, v.y + 0.02, v.z);
            }
        }
        Vec3 w = tail.subtract(f.scale(0.4)).add((random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15);
        l.addAlwaysVisibleParticle(WmdParticles.TORPEDO_WAKE.get(), true, w.x, w.y, w.z, -f.x * 0.03, 0.004, -f.z * 0.03);
        int top = NavalLayer.waterTop(l, Mth.floor(tail.x), Mth.floor(tail.z));
        if (top != NavalLayer.NO_WATER && top + 1.0 - tail.y <= SURFACE_TRACK && tickCount % 2 == 0) {
            double sy = top + 1.02;
            l.addAlwaysVisibleParticle(WmdParticles.TORPEDO_WAKE.get(), true, tail.x + (random.nextDouble() - 0.5) * 0.6, sy, tail.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.002, 0);
            if (random.nextInt(3) == 0) l.addParticle(ParticleTypes.SPLASH, tail.x, sy, tail.z, 0, 0.05, 0);
            if (random.nextInt(2) == 0) l.addParticle(ParticleTypes.BUBBLE_POP, tail.x + (random.nextDouble() - 0.5) * 0.8, sy, tail.z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }
    }

    static final double TAIL = 1.75, SURFACE_TRACK = 6.0;

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lx = x; ly = y; lz = z;
        lerpSteps = Math.max(1, steps);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256.0 * 256.0; }
    @Override public boolean isPickable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(KIND, tag.getInt("kind")); }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("kind", entityData.get(KIND)); }
}

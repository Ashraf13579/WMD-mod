package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BombEntity extends Entity {

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DUD = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> FUSE = SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.INT);

    private UUID owner, ownShip;
    private Vec3 vel = Vec3.ZERO;

    private long[] held;
    private Set<BlockPos> ownCells = Set.of();
    private double aimX, aimZ;
    private boolean aimed, whistled;
    private int skips;
    private double sinkFromY = Double.NaN;
    private Vec3 startPos, lockSpot;
    private int tsarHits;

    static final Set<BombEntity> FALLING = ConcurrentHashMap.newKeySet();

    static void clearStatics() { FALLING.clear(); SENT.clear(); }
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Long> SENT = new HashMap<>();
    private static final UUID NOBODY = new UUID(0L, 0L);

    private int lerpSteps;
    private double lx, ly, lz;

    public BombEntity(EntityType<? extends BombEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(KIND, 0); builder.define(DUD, false); builder.define(FUSE, -1); }

    public int fuse() { return entityData.get(FUSE); }

    public BombType type() { return BombType.ofOrdinal(entityData.get(KIND)); }

    public boolean isDud() { return entityData.get(DUD); }

    public int age() { return tickCount; }

    static BombEntity release(ServerLevel level, BombType type, Vec3 at, Vec3 v, UUID owner, int[] target, Set<BlockPos> ownCells, UUID ownShip) {
        if (!Config.Server.bombAllowed(type)) return null;
        BombEntity e = new BombEntity(Wmd.BOMB.get(), level);
        e.entityData.set(KIND, type.ordinal());
        e.setPos(at.x, at.y, at.z);
        e.owner = owner;
        e.vel = v;
        e.ownCells = ownCells == null ? Set.of() : ownCells;
        e.ownShip = ownShip;
        e.startPos = at;
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        double h = Math.max(1.0, at.y - ground);
        switch (type) {
            case IRON -> {
                double r = Math.min(Balance.IRON_SCATTER_MAX, Balance.IRON_SCATTER * h) * Math.sqrt(level.random.nextDouble()), a = level.random.nextDouble() * Math.PI * 2.0;
                double t = Math.sqrt(2.0 * h / Balance.BOMB_G);
                e.vel = e.vel.add(Math.cos(a) * r / t, 0.0, Math.sin(a) * r / t);
            }
            case GLIDE -> {
                if (target != null && Math.hypot(target[0] + 0.5 - at.x, target[1] + 0.5 - at.z) <= Balance.GLIDE_RANGE) {
                    double r = Balance.GLIDE_SCATTER * Math.sqrt(level.random.nextDouble()), a = level.random.nextDouble() * Math.PI * 2.0;
                    e.aimX = target[0] + 0.5 + Math.cos(a) * r; e.aimZ = target[1] + 0.5 + Math.sin(a) * r; e.aimed = true;
                }
            }
            default -> {}
        }
        level.addFreshEntity(e);
        FALLING.add(e);
        Vec3 land = e.impactGuess(level);
        DroneWarnings.bomb(level, owner, at.x, at.z, land.x, land.z, (long) Math.ceil(Math.sqrt(2.0 * h / Balance.BOMB_G)));
        level.playSound(null, at.x, at.y, at.z, WmdSounds.BOMB_RELEASE.get(), SoundSource.BLOCKS, 2.0F, 0.95F + level.random.nextFloat() * 0.1F);
        return e;
    }

    static void dud(ServerLevel level, Vec3 at, UUID owner) {
        BombEntity e = new BombEntity(Wmd.BOMB.get(), level);
        e.entityData.set(KIND, BombType.CLUSTER.ordinal());
        e.entityData.set(DUD, true);
        e.setPos(at.x + (level.random.nextDouble() - 0.5) * 0.6, at.y + 0.05, at.z + (level.random.nextDouble() - 0.5) * 0.6);
        e.setYRot(level.random.nextFloat() * 360.0F);
        e.owner = owner;
        level.addFreshEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (!isRemoved()) held = RunnerChunks.follow(sl, getUUID(), position(), vel.lengthSqr() > 1.0E-6 ? vel : getDeltaMovement(), held);

        if (isDud()) { dudTick(sl); return; }
        if (fuse() >= 0) { fuseTick(sl); return; }
        if (startPos == null) { discard(); return; }
        if (tickCount > (type() == BombType.TSAR ? Balance.TSAR_LIFE : 1200) || getY() < sl.getMinBuildHeight()) { discard(); return; }
        long gt = sl.getGameTime();
        if (gt % Balance.BOMB_RADAR_TICKS == 0 && !Long.valueOf(gt).equals(SENT.get(sl.dimension()))) { SENT.put(sl.dimension(), gt); radarSync(sl); }
        Vec3 pos = position();
        BombType t = type();
        double ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
        double h = pos.y - ground;

        vel = new Vec3(vel.x * Balance.BOMB_DRAG, vel.y * Balance.BOMB_DRAG - Balance.BOMB_G, vel.z * Balance.BOMB_DRAG);
        switch (t) {
            case LASER -> steerLaser(sl, pos, h);
            case GLIDE -> { if (tickCount >= Balance.GLIDE_WINGS) glide(pos, h); }
            case CLUSTER -> {
                if (h <= Balance.CARPET_OPEN_UP && vel.y < 0.0) { explode(sl, new Vec3(pos.x, ground, pos.z)); return; }
            }
            case DAISY -> {
                if (h <= Balance.DAISY_PROBE + t.length / 32.0 && vel.y < 0.0) { explode(sl, new Vec3(pos.x, ground, pos.z)); return; }
            }
            case TSAR -> {
                if (tickCount == Balance.TSAR_CHUTE_DELAY) sl.playSound(null, pos.x, pos.y, pos.z, WmdSounds.TS_CHUTES.get(), SoundSource.BLOCKS, 10.0F, 1.0F);
                if (tickCount >= Balance.TSAR_CHUTE_DELAY) {
                    vel = new Vec3(vel.x * 0.9, vel.y + (-Balance.TSAR_CHUTE_SPEED - vel.y) * 0.08, vel.z * 0.9);
                    TsarEvent.track(sl, this, pos, ground, h);
                    if (h <= Balance.TSAR_BURST_H) { tsarBurst(sl, pos, ground, h); return; }
                }
            }
            case BOUNCING -> {
                if (!Double.isNaN(sinkFromY)) {
                    vel = new Vec3(0.0, -0.2, 0.0);
                    if (pos.y <= sinkFromY - Balance.BOUNCE_SINK || !sl.getBlockState(BlockPos.containing(pos.x, pos.y - 0.6, pos.z)).getFluidState().isSource() && !sl.getBlockState(BlockPos.containing(pos.x, pos.y - 0.6, pos.z)).isAir()) {
                        explode(sl, pos);
                        return;
                    }
                }
            }
            default -> {}
        }
        Vec3 next = pos.add(vel);

        if (SableBridge.active()) {
            for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, pos, next)) {
                if (s.id().equals(ownShip)) continue;
                explode(sl, pos);
                return;
            }
        }

        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, Double.isNaN(sinkFromY) ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK && !ownCells.contains(hit.getBlockPos())) {
            if (t == BombType.BOUNCING && Double.isNaN(sinkFromY) && bounce(sl, hit)) return;
            if (t == BombType.DELAYED) { arm(sl, hit.getLocation()); return; }
            explode(sl, hit.getLocation());
            return;
        }
        if (!whistled && h < 48.0 && vel.y < -0.8 && t != BombType.TSAR) {
            whistled = true;
            sl.playSound(null, pos.x, pos.y, pos.z, WmdSounds.SHELL_WHISTLE.get(), SoundSource.BLOCKS, 6.0F, t == BombType.IRON ? 0.8F : 0.65F);
        }
        if (!ForcefieldBlockEntity.LOADED.isEmpty()) {
            Forcefield.Crossing c = Forcefield.crossing(sl, pos, next);
            if (c != null && Forcefield.shellHit(sl, c, t.blast.tier, t.blast)) { discard(); return; }
        }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(vel);
    }

    private void steerLaser(ServerLevel sl, Vec3 pos, double h) {
        double tLand = landTicks(h, vel.y);
        double px = pos.x + vel.x * tLand, pz = pos.z + vel.z * tLand;
        LaserSpots.Spot spot = owner == null ? null : LaserSpots.nearest(sl, owner, px, pz, Balance.LGB_RANGE);
        lockSpot = spot == null ? null : spot.pos();
        if (spot == null) return;
        if (tickCount % 10 == 0) sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.0);
        double hs = Math.max(1.0, pos.y - spot.pos().y);
        double tl = Math.max(1.0, landTicks(hs, vel.y));
        double wantX = (spot.pos().x - pos.x) / tl, wantZ = (spot.pos().z - pos.z) / tl;
        double s = tl < 6.0 ? 10.0 : Balance.LGB_STEER;
        vel = new Vec3(vel.x + Mth.clamp(wantX - vel.x, -s, s), vel.y, vel.z + Mth.clamp(wantZ - vel.z, -s, s));
    }

    private void glide(Vec3 pos, double h) {
        Vec3 hv = new Vec3(vel.x, 0.0, vel.z);
        Vec3 dir;
        double sink;
        if (aimed) {
            Vec3 to = new Vec3(aimX - pos.x, 0.0, aimZ - pos.z);
            double d = to.length();
            dir = d < 1.0E-3 ? new Vec3(0, 0, 1) : to.scale(1.0 / d);
            double ticks = Math.max(1.0, d / Balance.GLIDE_SPEED);
            sink = Math.max(0.05, h / ticks);
            if (d < Balance.GLIDE_SPEED * 2.0) { vel = new Vec3(to.x * 0.5, -Math.max(1.0, h * 0.5), to.z * 0.5); return; }
        } else {
            dir = hv.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : hv.normalize();
            sink = Balance.GLIDE_SINK;
        }
        Vec3 want = dir.scale(Balance.GLIDE_SPEED).add(0.0, -sink, 0.0);
        vel = vel.add(want.subtract(vel).scale(0.15));
    }

    private boolean bounce(ServerLevel sl, BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        BlockPos hp = hit.getBlockPos();
        boolean water = sl.getFluidState(hp).is(net.minecraft.tags.FluidTags.WATER);
        double hs = Math.hypot(vel.x, vel.z);
        if (water) {
            if (skips < Balance.BOUNCE_SKIPS && hs > Balance.BOUNCE_MIN_SPEED && -vel.y < hs * Balance.BOUNCE_MAX_SLOPE + 0.3) {
                skips++;
                vel = new Vec3(vel.x * Balance.BOUNCE_KEEP, Math.abs(vel.y) * 0.45 + 0.18, vel.z * Balance.BOUNCE_KEEP);
                setPos(at.x, at.y + 0.1, at.z);
                sl.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2, at.z, 60, 1.2, 0.3, 1.2, 0.3);
                sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 6, 0.8, 0.2, 0.8, 0.02);
                sl.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 4.0F, 0.7F + 0.1F * skips);
                return true;
            }
            sinkFromY = at.y;
            setPos(at.x, at.y - 0.3, at.z);
            return true;
        }
        BlockPos wet = BlockPos.containing(getX(), getY() - 0.5, getZ());
        if (sl.getFluidState(wet).is(net.minecraft.tags.FluidTags.WATER) || sl.getFluidState(wet.below()).is(net.minecraft.tags.FluidTags.WATER)) {
            sinkFromY = getY();
            vel = Vec3.ZERO;
            return true;
        }
        return false;
    }

    private void arm(ServerLevel sl, Vec3 at) {
        FALLING.remove(this);
        setPos(at.x, at.y - 0.25, at.z);
        vel = Vec3.ZERO;
        setDeltaMovement(Vec3.ZERO);
        entityData.set(FUSE, Balance.DELAY_MIN + sl.random.nextInt(Balance.DELAY_MAX - Balance.DELAY_MIN + 1));
        sl.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 12, 0.4, 0.2, 0.4, 0.02);
        sl.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof net.minecraft.server.level.ServerPlayer p)
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.bomb.armed", Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), fuse() / 20), false);
    }

    private void fuseTick(ServerLevel sl) {
        int f = fuse() - 1;
        if (f <= 0) { startPos = null; explode(sl, position()); return; }
        entityData.set(FUSE, f);
        int every = Math.max(4, Math.min(40, f / 15));
        if (f % every == 0) sl.playSound(null, getX(), getY(), getZ(), WmdSounds.FUSE_BEEP.get(), SoundSource.BLOCKS, 1.5F, f < 100 ? 1.25F : 1.0F);
    }

    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (fuse() < 0 || !player.getItemInHand(hand).is(Wmd.WRENCH.get())) return net.minecraft.world.InteractionResult.PASS;
        if (level() instanceof ServerLevel sl && !isRemoved()) {
            spawnAtLocation(new net.minecraft.world.item.ItemStack(Wmd.BOMB_DELAYED.get()));
            sl.playSound(null, getX(), getY(), getZ(), WmdSounds.FUSE_DEFUSE.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.bomb.defused"), true);
            Wmd.LOGGER.info("Bomb: {} defused a delayed-fuse bomb at {}", player.getName().getString(), blockPosition().toShortString());
            discard();
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    private static double landTicks(double h, double vy) {
        double g = Balance.BOMB_G, d = -vy;
        return Math.max(1.0, (-d + Math.sqrt(d * d + 2.0 * g * Math.max(0.0, h))) / g);
    }

    private void explode(ServerLevel sl, Vec3 at) {
        WarheadType w = type().blast;
        FlightManager mgr = FlightManager.get(sl);
        UUID prev = ShipStrike.begin(owner);
        Vec3 dir = new Vec3(vel.x, 0.0, vel.z);
        try {
            WarheadFx.detonate(sl, mgr, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), w.tier, w, dir.x, dir.z);
        } finally {
            ShipStrike.end(prev);
        }
        Wmd.LOGGER.info("Bomb: {} ({}) went off at {} {} {}", type().id, w.id, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof net.minecraft.server.level.ServerPlayer p && startPos != null)
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.bomb.hit", net.minecraft.network.chat.Component.translatable("item.wmd." + type().id),
                    Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), (int) Math.round(Math.hypot(at.x - startPos.x, at.z - startPos.z))), false);
        discard();
    }

    public Vec3 velocity() { return vel; }

    public UUID owner() { return owner; }

    Vec3 impactGuess(ServerLevel sl) {
        if (lockSpot != null) return lockSpot;
        if (aimed) return new Vec3(aimX, getY(), aimZ);
        double ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(getX()), Mth.floor(getZ()));
        double t = landTicks(Math.max(0.0, getY() - ground), vel.y);
        return new Vec3(getX() + vel.x * t, ground, getZ() + vel.z * t);
    }

    void shotDown(ServerLevel sl, UUID by, String byName) {
        if (isRemoved() || isDud()) return;
        if (type() == BombType.TSAR) { if (++tsarHits >= Balance.TSAR_CIWS_HITS) tsarBreak(sl, by, byName); return; }
        Vec3 at = position();
        Wmd.LOGGER.info("Bomb: {} shot down by {}'s CIWS at {} {} {}", type().id, byName, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof net.minecraft.server.level.ServerPlayer p)
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.bomb.shot_down", net.minecraft.network.chat.Component.translatable("item.wmd." + type().id),
                    Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z)), false);
        if (by != null && !by.equals(owner) && sl.getServer().getPlayerList().getPlayer(by) instanceof net.minecraft.server.level.ServerPlayer p)
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.bomb.ciws_kill", net.minecraft.network.chat.Component.translatable("item.wmd." + type().id)), true);
        startPos = null;
        explode(sl, at);
    }

    private void tsarBurst(ServerLevel sl, Vec3 pos, double ground, double h) {
        FlightManager mgr = FlightManager.get(sl);
        UUID prev = ShipStrike.begin(owner);
        try {
            WarheadFx.airburst(sl, mgr, Mth.floor(pos.x), (int) ground, Mth.floor(pos.z), WarheadType.TSAR.tier, WarheadType.TSAR, false, Math.max(4.0, h));
        } finally {
            ShipStrike.end(prev);
        }
        TsarEvent.burst(sl, this, new Vec3(pos.x, ground, pos.z), Math.max(4.0, h));
        Wmd.LOGGER.info("Bomb: the TSAR went off {} blocks over {} {} {}", Math.round(h), Mth.floor(pos.x), (int) ground, Mth.floor(pos.z));
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof net.minecraft.server.level.ServerPlayer p)
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.tsar.burst", Mth.floor(pos.x), Mth.floor(pos.z)).withStyle(net.minecraft.ChatFormatting.RED), false);
        discard();
    }

    void tsarBreak(ServerLevel sl, UUID by, String byName) {
        if (isRemoved()) return;
        Vec3 p = position();
        discard();
        sl.explode(this, p.x, p.y, p.z, (float) Balance.TSAR_FIZZLE_POWER, Config.Server.blockDamage() && Config.Server.canBreak(sl, BlockPos.containing(p)) ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 2, 2.0, 2.0, 2.0, 0.0);
        TsarEvent.broken(sl, this);
        Wmd.LOGGER.info("Bomb: the TSAR was broken by {} at {} {} {}", byName, Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
        net.minecraft.network.chat.Component line = net.minecraft.network.chat.Component.translatable("message.wmd.tsar.broken", byName).withStyle(net.minecraft.ChatFormatting.GOLD);
        for (net.minecraft.server.level.ServerPlayer o : sl.players()) if (o.distanceToSqr(p) < Balance.TSAR_FLASH_R * Balance.TSAR_FLASH_R) o.sendSystemMessage(line);
    }

    private static void radarSync(ServerLevel sl) {
        List<BombEntity> here = new ArrayList<>();
        for (BombEntity b : FALLING) if (b.level() == sl && !b.isRemoved()) here.add(b);
        double r2 = Balance.BOMB_RADAR_SEND * Balance.BOMB_RADAR_SEND;
        for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
            List<BombView.Contact> l = new ArrayList<>();
            for (BombEntity b : here) {
                boolean own = p.getUUID().equals(b.owner);
                double dx = b.getX() - p.getX(), dz = b.getZ() - p.getZ();
                if (!own && dx * dx + dz * dz > r2) continue;
                Vec3 i = b.impactGuess(sl);
                l.add(new BombView.Contact(b.getId(), own ? b.owner : NOBODY, b.getX(), b.getY(), b.getZ(), b.type().ordinal(), i.x, i.z));
                if (l.size() >= BombsPayload.MAX) break;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new BombsPayload(l));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sr) held = RunnerChunks.release(sr, getUUID(), held);
        super.remove(reason);
        if (level() instanceof ServerLevel s8 && type() == BombType.TSAR) TsarEvent.gone(s8, this);
        if (!(level() instanceof ServerLevel sl) || !FALLING.remove(this)) return;
        for (BombEntity b : FALLING) if (b.level() == sl) return;
        for (net.minecraft.server.level.ServerPlayer p : sl.players()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new BombsPayload(List.of()));
    }

    private void dudTick(ServerLevel sl) {
        if (tickCount % 4 != 0) return;
        AABB box = getBoundingBox().inflate(0.35);
        if (!sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator()).isEmpty()) dudGoesOff(sl);
    }

    private void dudGoesOff(ServerLevel sl) {
        if (isRemoved()) return;
        Vec3 p = position();
        discard();
        sl.explode(this, p.x, p.y + 0.2, p.z, (float) Balance.DUD_POWER, Config.Server.blockDamage() && Config.Server.canBreak(sl, BlockPos.containing(p)) ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        sl.playSound(null, p.x, p.y, p.z, WmdSounds.HE_NEAR.get(), SoundSource.BLOCKS, 2.0F, 1.4F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDud() && level() instanceof ServerLevel sl && !isRemoved()) { dudGoesOff(sl); return true; }
        return false;
    }

    @Override public boolean isPickable() { return isDud() || fuse() >= 0; }

    private void clientTick() {
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
            lerpSteps--;
        }
        if (isDud()) {
            if (tickCount % 30 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.25, getZ(), 0, 0.01, 0);
            return;
        }
        if (fuse() >= 0) {
            int every = Math.max(4, Math.min(40, fuse() / 15));
            if (fuse() % every < 2) VeilBridge.glow(0x4C00_0000_0000_0000L ^ getId(), getX(), getY() + 0.5, getZ(), 1.0F, 0.1F, 0.05F, 5.0F, 1.8F, level().getGameTime());
            return;
        }
        Vec3 p = position(), d = p.subtract(new Vec3(xo, yo, zo));
        if (d.lengthSqr() < 0.25) return;

        Vec3 tail = p.subtract(d.normalize().scale(type().length / 32.0));
        if (tickCount % 2 == 0) level().addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, tail.x, tail.y, tail.z, 0, 0, 0);
        if (type() == BombType.LASER && tickCount % 10 < 2) {
            Vec3 nose = p.add(d.normalize().scale(type().length / 32.0));
            VeilBridge.glow(0x4B00_0000_0000_0000L ^ getId(), nose.x, nose.y, nose.z, 1.0F, 0.15F, 0.1F, 4.0F, 1.2F, level().getGameTime());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lx = x; ly = y; lz = z;
        lerpSteps = Math.max(1, steps);
    }

    @Override public boolean shouldBeSaved() { return (isDud() || fuse() >= 0) && !isRemoved(); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < (isDud() ? 96.0 * 96.0 : 512.0 * 512.0); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt("kind"));
        entityData.set(DUD, tag.getBoolean("dud"));
        entityData.set(FUSE, tag.contains("fuse") ? tag.getInt("fuse") : -1);
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("kind", entityData.get(KIND));
        tag.putBoolean("dud", isDud());
        if (fuse() >= 0) tag.putInt("fuse", fuse());
        if (owner != null) tag.putUUID("owner", owner);
    }
}

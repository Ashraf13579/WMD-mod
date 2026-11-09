package com.ashraf.wmd;

import java.util.UUID;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public class CapsuleEntity extends Entity {

    public static final int RISE = 0, BREACH = 1, DROP = 2;

    public static final double HEIGHT = 6.25, MOUTH = 5.5;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> T0 = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> TOP = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.VECTOR3);

    private MissileBuild build;
    private int tx, tz, hang;
    private double startY, vy;
    private UUID owner;
    private String ownerName = "?";
    private long[] held;

    private int seenPhase = -1;
    private boolean splashed;

    public CapsuleEntity(EntityType<? extends CapsuleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static CapsuleEntity launch(ServerLevel level, Vec3 hatch, MissileBuild build, int tx, int tz, UUID owner, String ownerName) {
        CapsuleEntity e = new CapsuleEntity(Wmd.CAPSULE_ENTITY.get(), level);
        e.setPos(hatch.x, hatch.y - HEIGHT + 0.4, hatch.z);
        e.startY = hatch.y;
        e.build = build;
        e.tx = tx; e.tz = tz;
        e.owner = owner;
        e.ownerName = ownerName;
        Vec3 h = new Vec3(tx + 0.5 - hatch.x, 0.0, tz + 0.5 - hatch.z);
        h = h.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : h.normalize();
        e.entityData.set(DIR, new Vector3f((float) h.x, 0.0F, (float) h.z));
        e.entityData.set(T0, (int) level.getGameTime());
        level.addFreshEntity(e);
        SeaSound.nf(level, WmdSounds.LC_COLD_LAUNCH.get(), WmdSounds.LC_COLD_LAUNCH_FAR.get(), 64.0, hatch, 5.0F, 0.95F);
        NoiseNet.pulse(level, hatch, Balance.TORP_DB + 15.0, NoiseNet.Kind.LAUNCH, owner, 40);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(PHASE, RISE);
        b.define(T0, 0);
        b.define(DIR, new Vector3f(0, 0, -1));
        b.define(TOP, new Vector3f());
    }

    public int phase() { return entityData.get(PHASE); }
    double age(float pt) { return level().getGameTime() - entityData.get(T0) + pt; }
    Vec3 dir() { Vector3f d = entityData.get(DIR); return new Vec3(d.x(), 0.0, d.z()); }

    Vec3 capFrom() { Vector3f t = entityData.get(TOP); return new Vec3(t.x(), t.y(), t.z()); }

    Vec3 capPos(double k) { Vec3 d = dir(); return capFrom().add(-d.x * 0.25 * k, 0.55 * k - 0.04 * k * k, -d.z * 0.25 * k); }

    float tilt(float pt) { return phase() == DROP ? (float) Math.min(80.0, age(pt) * 2.5) : 0.0F; }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl) held = RunnerChunks.release(sl, getUUID(), held);
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (tickCount > 20 * 60 || (build == null && phase() != DROP)) { discard(); return; }
        Vec3 p = position();
        int top = NavalLayer.waterTop(sl, Mth.floor(p.x), Mth.floor(p.z));
        double sea = top == NavalLayer.NO_WATER ? startY : Math.max(startY, top + 1.0);
        int ph = phase();
        if (ph == RISE) {
            if (p.y + HEIGHT * 0.3 > sea) { setPhase(sl, BREACH); hang = 0; vy = Balance.CAPSULE_RISE * 2.2; }
            else move(p.add(0.0, Balance.CAPSULE_RISE, 0.0));
        } else if (ph == BREACH) {
            hang++;
            if (p.y < sea + Balance.CAPSULE_POP) { move(p.add(0.0, vy, 0.0)); vy = Math.max(0.05, vy * 0.85); }
            else if (hang >= Balance.CAPSULE_HANG_TICKS) away(sl, p);
        } else {
            double k = age(0.0F);
            boolean wet = sl.getFluidState(net.minecraft.core.BlockPos.containing(p.add(0.0, 1.0, 0.0))).is(FluidTags.WATER);
            vy = wet ? -Balance.CAPSULE_SINK : vy - 0.06;
            Vec3 d = dir();
            move(p.add(-d.x * 0.04, vy, -d.z * 0.04));
            if (k > Balance.CAPSULE_SINK_TICKS || p.y < sl.getMinBuildHeight()) discard();
        }
        if (!isRemoved()) held = RunnerChunks.follow(sl, getUUID(), position(), getDeltaMovement(), held);
    }

    private void move(Vec3 n) {
        setDeltaMovement(n.subtract(position()));
        setPos(n.x, n.y, n.z);
    }

    private void setPhase(ServerLevel sl, int ph) {
        entityData.set(T0, (int) sl.getGameTime());
        entityData.set(PHASE, ph);
    }

    private void away(ServerLevel sl, Vec3 p) {
        Vec3 mouth = p.add(0.0, MOUTH, 0.0);
        entityData.set(TOP, new Vector3f((float) mouth.x, (float) (p.y + HEIGHT - 0.6), (float) mouth.z));
        Vec3 d = dir();
        CruiseRoute r = new CruiseRoute(mouth.x, mouth.z, d.x, d.z, new int[] { tx }, new int[] { tz }, (float) Balance.CRUISE_SPEED, null);
        r = r.withAlt(r.sampleAltitude(sl));
        int groundY = CruiseRoute.groundAt(sl, tx, tz);
        double miss = Config.missRadius(build.guidance()) * Balance.CRUISE_MISS_FACTOR;
        FlightManager mgr = FlightManager.get(sl);
        long id = mgr.launchCruise(sl, mouth, r, groundY, ownerName, owner, miss, build);
        ShipDefence.assignCruiseLock(sl, mgr, mgr.find(id), owner);
        PacketDistributor.sendToPlayersNear(sl, null, mouth.x, mouth.y, mouth.z, LaunchSoundPayload.range(LaunchSoundPayload.CRUISE), new LaunchSoundPayload(LaunchSoundPayload.CRUISE, mouth.x, mouth.y, mouth.z));
        sl.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 4.0F, 0.5F);
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer pl)
            pl.displayClientMessage(Component.translatable("message.wmd.lc.cruise_away", tx, tz, (int) Math.round(r.length()), r.ticks() / 20), false);
        Wmd.LOGGER.info("Cell cruise missile {} by {} from {} {} {} to {} {}, {} blocks", id, ownerName, Mth.floor(mouth.x), Mth.floor(mouth.y), Mth.floor(mouth.z), tx, tz, (int) r.length());
        build = null;
        vy = 0.1;
        setPhase(sl, DROP);
    }

    private void clientTick() {
        Level l = level();
        int ph = phase();
        if (ph != seenPhase) { onPhase(l, ph); seenPhase = ph; }
        Vec3 p = position();
        if (ph == RISE || (ph == DROP && l.getFluidState(net.minecraft.core.BlockPos.containing(p)).is(FluidTags.WATER))) {
            for (int i = 0; i < 3; i++) l.addParticle(ParticleTypes.BUBBLE, p.x + (random.nextDouble() - 0.5) * 0.8, p.y + random.nextDouble() * HEIGHT, p.z + (random.nextDouble() - 0.5) * 0.8, 0, 0.12, 0);
        }
        if (ph == BREACH && random.nextInt(2) == 0) l.addParticle(ParticleTypes.FALLING_WATER, p.x + (random.nextDouble() - 0.5) * 0.8, p.y + random.nextDouble() * 4.0, p.z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        if (ph == DROP && !splashed) {
            int top = NavalLayer.waterTop(l, Mth.floor(p.x), Mth.floor(p.z));
            if (top != NavalLayer.NO_WATER && p.y < top + 1.0 && age(0.0F) > 4) {
                splashed = true;
                for (int i = 0; i < 40; i++) l.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, p.x + (random.nextDouble() - 0.5) * 2.0, top + 1.0, p.z + (random.nextDouble() - 0.5) * 2.0,
                        (random.nextDouble() - 0.5) * 0.4, 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.4);
                l.playLocalSound(p.x, top + 1.0, p.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 3.0F, 0.7F, false);
            }
        }
    }

    private void onPhase(Level l, int ph) {
        Vec3 p = position();
        int top = NavalLayer.waterTop(l, Mth.floor(p.x), Mth.floor(p.z));
        if (ph == BREACH && top != NavalLayer.NO_WATER) {
            for (int i = 0; i < 60; i++)
                l.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, p.x + (random.nextDouble() - 0.5) * 1.8, top + 1.0, p.z + (random.nextDouble() - 0.5) * 1.8,
                        (random.nextDouble() - 0.5) * 0.25, 0.5 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.25);
            l.playLocalSound(p.x, top + 1.0, p.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 3.5F, 0.8F, false);
        } else if (ph == DROP) {
            Vec3 c = capFrom();
            VeilBridge.flash(c.x, c.y, c.z, 1.0F, 0.75F, 0.4F, 10.0F, 2.5F, 6);
            for (int i = 0; i < 16; i++)
                l.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, c.x, c.y, c.z, (random.nextDouble() - 0.5) * 0.35, 0.05 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.35);
        }
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 400.0 * 400.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

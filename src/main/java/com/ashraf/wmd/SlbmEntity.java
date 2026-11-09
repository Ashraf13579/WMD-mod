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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public class SlbmEntity extends Entity {

    public static final int RISE = 0, HANG = 1, CLIMB = 2;

    public static final double LENGTH = 6.0;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(SlbmEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> T0 = SynchedEntityData.defineId(SlbmEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> P0 = SynchedEntityData.defineId(SlbmEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> PAINT = SynchedEntityData.defineId(SlbmEntity.class, EntityDataSerializers.INT);

    private MissileBuild build;
    private int tx, ty, tz, hang;
    private UUID owner;
    private String ownerName = "?";
    private double startY;
    private long[] held;

    private int seenPhase = -1;
    private boolean popped;

    public SlbmEntity(EntityType<? extends SlbmEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static SlbmEntity launch(ServerLevel level, Vec3 hatch, MissileBuild build, int tx, int ty, int tz, UUID owner, String ownerName) {
        SlbmEntity e = new SlbmEntity(Wmd.SLBM_ENTITY.get(), level);
        e.setPos(hatch.x, hatch.y - LENGTH + 0.4, hatch.z);
        e.startY = hatch.y;
        e.build = build;
        e.tx = tx; e.ty = ty; e.tz = tz;
        e.owner = owner;
        e.ownerName = ownerName;
        e.entityData.set(T0, (int) level.getGameTime());
        e.entityData.set(PAINT, MissileParts.paintOf(build));
        level.addFreshEntity(e);
        SeaSound.nf(level, WmdSounds.LC_COLD_LAUNCH.get(), WmdSounds.LC_COLD_LAUNCH_FAR.get(), 64.0, hatch, 6.0F, 0.85F);
        NoiseNet.pulse(level, hatch, Balance.TORP_DB + 20.0, NoiseNet.Kind.LAUNCH, owner, 60);
        Wmd.LOGGER.info("SLBM cold launch by {} at {} {} {} -> {} {} ({})", ownerName, Mth.floor(hatch.x), Mth.floor(hatch.y), Mth.floor(hatch.z), tx, tz, build.warheadType());
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(PHASE, RISE);
        b.define(T0, 0);
        b.define(P0, new Vector3f());
        b.define(PAINT, 3);
    }

    public int phase() { return entityData.get(PHASE); }
    public int paint() { return entityData.get(PAINT); }

    double age(float pt) { return level().getGameTime() - entityData.get(T0) + pt; }

    static double climbed(double t) {
        double a = Flight.V_START / Balance.SLBM_CLIMB_TICKS;
        t = Mth.clamp(t, 0.0, Balance.SLBM_CLIMB_TICKS);
        return 0.5 * a * t * t;
    }

    Vec3 climbPos(float pt) {
        if (phase() != CLIMB) return null;
        Vector3f p = entityData.get(P0);
        return new Vec3(p.x(), p.y() + climbed(age(pt)), p.z());
    }

    float spike01(float pt) {
        int ph = phase();
        if (ph == RISE) return 0.0F;
        if (ph == CLIMB) return 1.0F;
        return (float) Mth.clamp((age(pt) - Balance.SLBM_POP_AT) / Balance.SLBM_SPIKE_TICKS, 0.0, 1.0);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl) held = RunnerChunks.release(sl, getUUID(), held);
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (build == null || tickCount > 20 * 60) { discard(); return; }
        Vec3 p = position();
        int ph = phase();
        if (ph == RISE) {
            int top = NavalLayer.waterTop(sl, Mth.floor(p.x), Mth.floor(p.z));
            double clear = Math.max(startY, top == NavalLayer.NO_WATER ? startY : top + 1.0);
            if (p.y > clear + 0.3) { setPhase(sl, HANG); hang = 0; }
            else move(p.add(0.0, Balance.SLBM_RISE, 0.0));
        } else if (ph == HANG) {
            hang++;
            move(p.add(0.0, Balance.SLBM_RISE * Math.max(0.0, 1.0 - hang / (double) Balance.SLBM_HANG_TICKS), 0.0));
            if (hang == Balance.SLBM_POP_AT) sl.playSound(null, p.x, p.y + LENGTH, p.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 3.0F, 0.6F);
            if (hang >= Balance.SLBM_HANG_TICKS) ignite(sl, position());
        } else {
            double t = age(0.0F);
            Vector3f a = entityData.get(P0);
            move(new Vec3(a.x(), a.y() + climbed(t), a.z()));
            if (t >= Balance.SLBM_CLIMB_TICKS) handOver(sl);
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

    private void ignite(ServerLevel sl, Vec3 p) {
        entityData.set(P0, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        setPhase(sl, CLIMB);
        sound(sl, LaunchSoundPayload.IGNITE + 3, p);
        sound(sl, LaunchSoundPayload.ASCENT + 3, p);
    }

    private static void sound(ServerLevel sl, int kind, Vec3 at) {
        PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, LaunchSoundPayload.range(kind), new LaunchSoundPayload(kind, at.x, at.y, at.z));
    }

    private void handOver(ServerLevel sl) {
        Vec3 mid = position().add(0.0, LENGTH * 0.5, 0.0);
        boolean up = Uplink.covers(sl.getServer(), build, owner) && !CcJammer.jammedAt(sl, owner, tx + 0.5, tz + 0.5);
        double miss = Config.missRadius(build.guidance());
        long id = FlightManager.get(sl).launch(sl, 3, mid, tx, ty, tz, ownerName, owner, up ? Uplink.missRadius(miss) : miss, build, false, 0, 0);
        if (up) ShipDefence.assignLock(sl, FlightManager.get(sl), FlightManager.get(sl).find(id), owner);
        sound(sl, LaunchSoundPayload.BOOM + 3, mid);
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer pl)
            pl.displayClientMessage(Component.translatable("message.wmd.slbm.away", tx, tz, String.format("%,d", (int) Math.round(Math.hypot(tx + 0.5 - mid.x, tz + 0.5 - mid.z)))), false);
        Wmd.LOGGER.info("SLBM flight {} by {} from {} {} {} to {} {}", id, ownerName, Mth.floor(mid.x), Mth.floor(mid.y), Mth.floor(mid.z), tx, tz);
        discard();
    }

    private void clientTick() {
        Level l = level();
        int ph = phase();
        if (ph != seenPhase) { onPhase(l, ph); seenPhase = ph; }
        Vec3 p = position();
        if (ph == RISE) {
            for (int i = 0; i < 4; i++) l.addParticle(ParticleTypes.BUBBLE, p.x + (random.nextDouble() - 0.5) * 0.8, p.y + random.nextDouble() * LENGTH, p.z + (random.nextDouble() - 0.5) * 0.8, 0, 0.12, 0);
        } else if (ph == HANG) {
            if (!popped && age(0.0F) >= Balance.SLBM_POP_AT) {
                popped = true;
                Vec3 n = p.add(0.0, LENGTH, 0.0);
                for (int i = 0; i < 12; i++)
                    l.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, n.x, n.y, n.z, (random.nextDouble() - 0.5) * 0.3, 0.1 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
                for (int i = 0; i < 4; i++)
                    l.addParticle(ParticleTypes.CRIT, n.x, n.y, n.z, (random.nextDouble() - 0.5) * 0.6, 0.3 + random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.6);
            }
            if (random.nextInt(3) == 0) l.addParticle(ParticleTypes.FALLING_WATER, p.x + (random.nextDouble() - 0.5) * 0.8, p.y + random.nextDouble() * 3.0, p.z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        } else {
            double t1 = age(0.0F), t0 = Math.max(0.0, t1 - 1.0);
            Vector3f a = entityData.get(P0);
            int n = 5;
            for (int i = 0; i < n; i++) {
                double y = a.y() + climbed(t0 + (t1 - t0) * (i + random.nextDouble()) / n);
                WmdSmokeParticle.nextSize = 1.6F;
                try { l.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, a.x() + (random.nextDouble() - 0.5) * 0.4, y - 0.4, a.z() + (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.03, -0.05, (random.nextDouble() - 0.5) * 0.03); }
                finally { WmdSmokeParticle.nextSize = 1.0F; }
            }
            double y = a.y() + climbed(t1);
            l.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, a.x(), y - 0.3, a.z(), 0, -0.4, 0);
            if (tickCount % 3 == 0) VeilBridge.flash(a.x(), y - 0.5, a.z(), 1.0F, 0.7F, 0.35F, 8.0F, 2.0F, 5);
        }
    }

    private void onPhase(Level l, int ph) {
        Vec3 p = position();
        int top = NavalLayer.waterTop(l, Mth.floor(p.x), Mth.floor(p.z));
        if (ph == HANG && top != NavalLayer.NO_WATER) {
            for (int i = 0; i < 80; i++)
                l.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, p.x + (random.nextDouble() - 0.5) * 2.0, top + 1.0, p.z + (random.nextDouble() - 0.5) * 2.0,
                        (random.nextDouble() - 0.5) * 0.3, 0.6 + random.nextDouble() * 0.7, (random.nextDouble() - 0.5) * 0.3);
            l.playLocalSound(p.x, top + 1.0, p.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 4.0F, 0.6F, false);
        } else if (ph == CLIMB) {
            Vector3f a = entityData.get(P0);
            VeilBridge.flash(a.x(), a.y(), a.z(), 1.0F, 0.8F, 0.5F, 18.0F, 3.5F, 10);
            double sy = top == NavalLayer.NO_WATER ? a.y() - 1.0 : top + 1.2;
            WmdSmokeParticle.nextSize = 2.0F;
            try {
                for (int i = 0; i < 32; i++) {
                    double ang = i * Math.PI * 2.0 / 32;
                    l.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, a.x() + Math.cos(ang) * 1.5, sy, a.z() + Math.sin(ang) * 1.5, Math.cos(ang) * 0.3, 0.02, Math.sin(ang) * 0.3);
                }
            } finally { WmdSmokeParticle.nextSize = 1.0F; }
        }
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 512.0 * 512.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

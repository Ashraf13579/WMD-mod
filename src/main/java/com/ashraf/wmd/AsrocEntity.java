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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class AsrocEntity extends Entity {

    public static final int RISE = 0, HANG = 1, BOOST = 2, CHUTE = 3;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(AsrocEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> T0 = SynchedEntityData.defineId(AsrocEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TICKS = SynchedEntityData.defineId(AsrocEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> P0 = SynchedEntityData.defineId(AsrocEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> P1 = SynchedEntityData.defineId(AsrocEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> P2 = SynchedEntityData.defineId(AsrocEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> P3 = SynchedEntityData.defineId(AsrocEntity.class, EntityDataSerializers.VECTOR3);

    private UUID owner;
    private Vec3 to;
    private double runDepth;
    private int hang;

    private long[] held;

    private int seenPhase = -1;
    private boolean boosterSplashed;

    public AsrocEntity(EntityType<? extends AsrocEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static AsrocEntity launch(ServerLevel level, Vec3 at, Vec3 target, double targetDepth, UUID owner) {
        AsrocEntity e = new AsrocEntity(Wmd.ASROC_ENTITY.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.to = target;
        e.runDepth = targetDepth;
        e.owner = owner;
        e.entityData.set(T0, (int) level.getGameTime());
        level.addFreshEntity(e);
        SeaSound.nf(level, WmdSounds.LC_COLD_LAUNCH.get(), WmdSounds.LC_COLD_LAUNCH_FAR.get(), 64.0, at, 5.0F, 1.0F);
        NoiseNet.pulse(level, at, Balance.TORP_DB + 15.0, NoiseNet.Kind.LAUNCH, owner, 40);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(PHASE, RISE);
        b.define(T0, 0);
        b.define(TICKS, 1);
        b.define(P0, new Vector3f());
        b.define(P1, new Vector3f());
        b.define(P2, new Vector3f());
        b.define(P3, new Vector3f());
    }

    public int phase() { return entityData.get(PHASE); }

    private static Vec3 v(Vector3f f) { return new Vec3(f.x(), f.y(), f.z()); }

    private Vec3 curve(double u) {
        Vec3 a = v(entityData.get(P0)), b = v(entityData.get(P1)), c = v(entityData.get(P2)), d = v(entityData.get(P3));
        double m = 1.0 - u;
        return a.scale(m * m * m).add(b.scale(3 * m * m * u)).add(c.scale(3 * m * u * u)).add(d.scale(u * u * u));
    }

    private Vec3 curveDir(double u) {
        Vec3 a = v(entityData.get(P0)), b = v(entityData.get(P1)), c = v(entityData.get(P2)), d = v(entityData.get(P3));
        double m = 1.0 - u;
        Vec3 t = b.subtract(a).scale(3 * m * m).add(c.subtract(b).scale(6 * m * u)).add(d.subtract(c).scale(3 * u * u));
        return t.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : t.normalize();
    }

    private Vec3 heading() {
        Vec3 h = v(entityData.get(P3)).subtract(v(entityData.get(P0)));
        h = new Vec3(h.x, 0.0, h.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : h.normalize();
    }

    private double age(float pt) { return level().getGameTime() - entityData.get(T0) + pt; }

    private double boostU(float pt) { return Mth.clamp(age(pt) / Math.max(1, entityData.get(TICKS)), 0.0, 1.0); }

    private Vec3 chutePos(double k) {
        return v(entityData.get(P3)).add(heading().scale(Balance.ASROC_CHUTE_DRIFT * k)).add(0.0, -Balance.ASROC_CHUTE_SPEED * k, 0.0);
    }

    Vec3 boosterPos(double k) {
        return v(entityData.get(P3)).add(heading().scale(0.5 * k)).add(0.0, 0.15 * k - 0.5 * 0.05 * k * k, 0.0);
    }

    Vec3[] pose(float pt) {
        int ph = phase();
        if (ph == BOOST) { double u = boostU(pt); return new Vec3[] { curve(u), curveDir(u) }; }
        if (ph == CHUTE) { Vec3 h = heading(); return new Vec3[] { chutePos(age(pt)), new Vec3(h.x * 0.35, -1.0, h.z * 0.35).normalize() }; }
        return new Vec3[] { null, new Vec3(0, 1, 0) };
    }

    double chuteAge(float pt) { return age(pt); }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl) held = RunnerChunks.release(sl, getUUID(), held);
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) { clientTick(); return; }
        if (to == null || tickCount > 20 * 120) { discard(); return; }
        Vec3 p = position();
        int ph = phase();
        if (ph == RISE) {
            int top = NavalLayer.waterTop(sl, Mth.floor(p.x), Mth.floor(p.z));
            boolean wet = sl.getFluidState(BlockPos.containing(p)).is(FluidTags.WATER);
            if (!wet || top == NavalLayer.NO_WATER || p.y > top + 1.0) { setPhase(sl, HANG); hang = 0; }
            else move(p.add(0.0, Balance.ASROC_RISE, 0.0));
        } else if (ph == HANG) {
            hang++;
            double vy = Balance.ASROC_RISE * Math.max(0.0, 1.0 - hang / (double) Balance.ASROC_HANG_TICKS);
            move(p.add(0.0, vy, 0.0));
            if (hang >= Balance.ASROC_HANG_TICKS) ignite(sl, position());
        } else if (ph == BOOST) {
            double u = boostU(0.0F);
            move(curve(u));
            if (u >= 1.0) separate(sl);
        } else {
            Vec3 n = chutePos(age(0.0F));
            move(n);
            int top = NavalLayer.waterTop(sl, Mth.floor(n.x), Mth.floor(n.z));
            boolean solid = !sl.getBlockState(BlockPos.containing(n)).getCollisionShape(sl, BlockPos.containing(n)).isEmpty();
            if (solid || (top != NavalLayer.NO_WATER && n.y <= top + 1.0) || n.y < sl.getMinBuildHeight()) splash(sl, n, top);
        }
        held = RunnerChunks.follow(sl, getUUID(), position(), getDeltaMovement(), held);
    }

    private void move(Vec3 n) {
        setDeltaMovement(n.subtract(position()));
        setPos(n.x, n.y, n.z);
    }

    private void setPhase(ServerLevel sl, int ph) {
        entityData.set(T0, (int) sl.getGameTime());
        entityData.set(PHASE, ph);
    }

    private static Vector3f f(Vec3 v) { return new Vector3f((float) v.x, (float) v.y, (float) v.z); }

    private void ignite(ServerLevel sl, Vec3 p) {
        int top = NavalLayer.waterTop(sl, Mth.floor(to.x), Mth.floor(to.z));
        double sea = top == NavalLayer.NO_WATER ? to.y : top + 1.0;
        Vec3 h = new Vec3(to.x - p.x, 0.0, to.z - p.z);
        double dist = h.length();
        Vec3 dir = dist < 1e-3 ? new Vec3(0, 0, -1) : h.scale(1.0 / dist);
        double apex = Math.max(30.0, dist * 0.25), alt = Math.max(Balance.ASROC_SEP_ALT, apex * 0.5);
        double drift = alt / Balance.ASROC_CHUTE_SPEED * Balance.ASROC_CHUTE_DRIFT;
        Vec3 land = landing(sl, dir, Math.min(Balance.ASROC_STANDOFF, Math.max(0.0, dist - 20.0)));
        Vec3 sep = new Vec3(land.x - dir.x * drift, sea + alt, land.z - dir.z * drift);
        Vec3 span = sep.subtract(p);
        Vec3 p1 = p.add(0.0, apex * 0.8, 0.0);
        Vec3 p2 = p.add(span.x * 0.55, Math.max(span.y, 0.0) + apex * 1.15, span.z * 0.55);
        double len = p.distanceTo(p1) + p1.distanceTo(p2) + p2.distanceTo(sep);
        entityData.set(P0, f(p)); entityData.set(P1, f(p1)); entityData.set(P2, f(p2)); entityData.set(P3, f(sep));
        entityData.set(TICKS, Math.max(30, (int) Math.round(len * 0.8 / Balance.ASROC_SPEED)));
        setPhase(sl, BOOST);
        SeaSound.nf(sl, WmdSounds.ASROC_IGNITE.get(), WmdSounds.ASROC_IGNITE_FAR.get(), 80.0, p, 8.0F, 1.0F);
    }

    private Vec3 landing(ServerLevel sl, Vec3 dir, double standoff) {
        for (double s = standoff; s > 0.0; s -= 4.0) {
            double x = to.x - dir.x * s, z = to.z - dir.z * s;
            int top = NavalLayer.waterTop(sl, Mth.floor(x), Mth.floor(z));
            if (top != NavalLayer.NO_WATER && sl.getFluidState(new BlockPos(Mth.floor(x), top - 2, Mth.floor(z))).is(FluidTags.WATER)) return new Vec3(x, to.y, z);
        }
        return to;
    }

    private void separate(ServerLevel sl) {
        Vec3 p = position();
        setPhase(sl, CHUTE);
        sl.playSound(null, p.x, p.y, p.z, WmdSounds.ASROC_DROP.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        tell(sl, Component.translatable("message.wmd.asroc.separated", Mth.floor(p.x), Mth.floor(p.z)));
    }

    private void splash(ServerLevel sl, Vec3 at, int top) {
        discard();
        Vec3 aim = to == null ? Vec3.ZERO : new Vec3(to.x - at.x, 0.0, to.z - at.z);
        Vec3 dir = aim.lengthSqr() > 4.0 ? aim.normalize() : heading();
        Vec3 entry = new Vec3(at.x, (top == NavalLayer.NO_WATER ? at.y : top + 1.0) - 1.5, at.z);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, 3.0F, 0.8F);
        sl.sendParticles(ParticleTypes.SPLASH, at.x, entry.y + 1.5, at.z, 60, 0.6, 0.2, 0.6, 0.3);
        sl.sendParticles(ParticleTypes.BUBBLE, at.x, entry.y, at.z, 30, 0.5, 0.5, 0.5, 0.1);
        if (!sl.getFluidState(BlockPos.containing(entry)).is(FluidTags.WATER)) {
            tell(sl, Component.translatable("message.wmd.asroc.lost", Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z)));
            return;
        }
        TorpedoEntity.launch(sl, TorpedoKind.T2, entry, dir.add(0.0, -0.3, 0.0), 0.3, Math.max(Balance.TORP_MIN_DEPTH, runDepth), owner, null);
        tell(sl, Component.translatable("message.wmd.asroc.splash", Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z)));
    }

    private void tell(ServerLevel sl, Component c) {
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p) p.displayClientMessage(c, false);
    }

    private void clientTick() {
        Level l = level();
        int ph = phase();
        if (ph != seenPhase) { onPhase(l, ph); seenPhase = ph; }
        if (ph == RISE) {
            Vec3 p = position();
            for (int i = 0; i < 3; i++) l.addParticle(ParticleTypes.BUBBLE, p.x + (random.nextDouble() - 0.5) * 0.6, p.y - 1.5 - random.nextDouble() * 2.0, p.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.1, 0);
        } else if (ph == BOOST) {

            double u1 = boostU(0.0F), u0 = Math.max(0.0, boostU(-1.0F));
            int n = 4;
            for (int i = 0; i < n; i++) {
                double u = u0 + (u1 - u0) * (i + random.nextDouble()) / n;
                Vec3 c = curve(u), d = curveDir(u), noz = c.subtract(d.scale(2.7));
                WmdSmokeParticle.nextSize = 1.3F;
                try { l.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, noz.x, noz.y, noz.z, -d.x * 0.05 + (random.nextDouble() - 0.5) * 0.02, -d.y * 0.05, -d.z * 0.05 + (random.nextDouble() - 0.5) * 0.02); }
                finally { WmdSmokeParticle.nextSize = 1.0F; }
            }
            Vec3 c = curve(u1), d = curveDir(u1), noz = c.subtract(d.scale(2.7));
            l.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, noz.x, noz.y, noz.z, -d.x * 0.3, -d.y * 0.3, -d.z * 0.3);
            if (tickCount % 4 == 0) VeilBridge.flash(noz.x, noz.y, noz.z, 1.0F, 0.7F, 0.35F, 6.0F, 1.6F, 5);
        } else if (ph == CHUTE && !boosterSplashed) {
            Vec3 b = boosterPos(age(0.0F));
            int top = NavalLayer.waterTop(l, Mth.floor(b.x), Mth.floor(b.z));
            if (age(0.0F) < 25) l.addParticle(ParticleTypes.SMOKE, b.x, b.y, b.z, 0, 0.02, 0);
            if ((top != NavalLayer.NO_WATER && b.y <= top + 1.0) || age(0.0F) > 200) {
                boosterSplashed = true;
                if (top != NavalLayer.NO_WATER) {
                    for (int i = 0; i < 25; i++) l.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, b.x, top + 1.0, b.z, (random.nextDouble() - 0.5) * 0.4, 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.4);
                    l.playLocalSound(b.x, top + 1.0, b.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 2.5F, 0.7F, false);
                }
            }
        }
    }

    private void onPhase(Level l, int ph) {
        Vec3 p = position();
        if (ph == HANG) {
            int top = NavalLayer.waterTop(l, Mth.floor(p.x), Mth.floor(p.z));
            double sy = top == NavalLayer.NO_WATER ? p.y : top + 1.0;
            for (int i = 0; i < 50; i++)
                l.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, p.x + (random.nextDouble() - 0.5) * 1.6, sy, p.z + (random.nextDouble() - 0.5) * 1.6,
                        (random.nextDouble() - 0.5) * 0.25, 0.5 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.25);
        } else if (ph == BOOST) {
            Vec3 a = v(entityData.get(P0));
            VeilBridge.flash(a.x, a.y, a.z, 1.0F, 0.8F, 0.5F, 14.0F, 3.0F, 8);
            int top = NavalLayer.waterTop(l, Mth.floor(a.x), Mth.floor(a.z));
            double sy = top == NavalLayer.NO_WATER ? a.y - 3.0 : top + 1.2;
            WmdSmokeParticle.nextSize = 1.6F;
            try {
                for (int i = 0; i < 24; i++) {
                    double ang = i * Math.PI * 2.0 / 24;
                    l.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, a.x + Math.cos(ang) * 1.5, sy, a.z + Math.sin(ang) * 1.5, Math.cos(ang) * 0.25, 0.02, Math.sin(ang) * 0.25);
                }
            } finally { WmdSmokeParticle.nextSize = 1.0F; }
        }
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 400.0 * 400.0; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}

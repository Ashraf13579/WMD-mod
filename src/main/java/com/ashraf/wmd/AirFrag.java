package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AirFrag {

    private AirFrag() {}

    static void burst(ServerLevel level, Vec3 at, Vec3 heading, int kind, UUID owner, UUID ship, UUID entity, int[] plot, boolean refined) {
        if (level == null || at == null) return;
        double rf = AirMissiles.refinedFactor(refined), r = Balance.AAM_FRAG_RADIUS[kind] * rf;
        AirMissiles.fx(level, at, ParticleTypes.EXPLOSION, 3, 1.2, 0.0);
        AirMissiles.fx(level, at, WmdParticles.LAUNCH_SMOKE.get(), 24, 1.8, 0.04);
        level.playSound(null, at.x, at.y, at.z, WmdSounds.AAM_BURST.get(), SoundSource.HOSTILE, Balance.AAM_BURST_VOLUME, 1.0F);

        ServerPlayer shooter = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r))) {
            double d = AirMissiles.distToBox(e.getBoundingBox(), at);
            if (d > r) continue;
            float dmg = (float) (Balance.AAM_FRAG_DAMAGE[kind] * (1.0 - 0.7 * d / r));
            e.hurt(level.damageSources().explosion(null, shooter), dmg);
        }

        Vec3 hull = at;
        if (ship != null) {
            int[] b = plot != null ? plot : SableBridge.plotBox(level, ship);
            Vec3 q = b == null ? null : SableBridge.toShip(level, ship, at);
            if (q != null) {
                Vec3 c = new Vec3(Math.max(b[0], Math.min(b[3] + 1.0, q.x)), Math.max(b[1], Math.min(b[4] + 1.0, q.y)), Math.max(b[2], Math.min(b[5] + 1.0, q.z)));
                hull = SableBridge.toWorld(level, c);
            }
        }

        ShipStrike.blast(level, hull, r, (float) (Balance.AAM_FRAG_POWER[kind] * rf), owner);

        if (ship == null || ShipStrike.spare(level, ship, owner)) return;

        int off = AirStations.shootOff(level, ship, hull, r);
        if (off > 0) Wmd.LOGGER.info("Air frag ({}): {} store(s) shot off", AirMissiles.NAMES[kind], off);
        Vec3 toHull = hull.subtract(at);
        Vec3 axis = toHull.lengthSqr() > 0.25 ? toHull.normalize() : heading.lengthSqr() > 1.0E-9 ? heading.normalize() : new Vec3(0, -1, 0);
        double reach = toHull.length() + Balance.AAM_FRAG_DEPTH;
        int broken = 0;
        for (int i = 0; i < Balance.AAM_FRAG_RAYS; i++) {
            Vec3 dir = cone(level, axis, Math.toRadians(Balance.AAM_FRAG_CONE));
            broken += ShipStrike.chew(level, ship, at, at.add(dir.scale(reach)), Balance.AAM_FRAG_BLOCKS, (float) Balance.CIWS_SHIP_MAX_HARDNESS, owner);
        }
        Wmd.LOGGER.info("Air frag ({}) at {}: {} hull blocks from fragments", AirMissiles.NAMES[kind], BlockPos.containing(at).toShortString(), broken);
    }

    static void ground(ServerLevel level, BlockPos p, Vec3 heading, UUID owner, boolean refined) {
        if (level == null || p == null) return;
        UUID prev = ShipStrike.begin(owner);
        try {
            double hl = Math.hypot(heading.x, heading.z);
            WarheadFx.detonate(level, FlightManager.get(level), p.getX(), p.getY(), p.getZ(), 1, WarheadType.FRAG, hl > 1.0E-6 ? heading.x / hl : 0.0, hl > 1.0E-6 ? heading.z / hl : 1.0, refined);
        } finally {
            ShipStrike.end(prev);
        }
    }

    static void puff(ServerLevel level, Vec3 at) {
        AirMissiles.fx(level, at, ParticleTypes.POOF, 12, 0.6, 0.05);
        AirMissiles.fx(level, at, WmdParticles.TRAIL_SMOKE.get(), 16, 1.0, 0.02);
        level.playSound(null, at.x, at.y, at.z, WmdSounds.AAM_FIZZLE.get(), SoundSource.HOSTILE, Balance.SND_RELEASE, 1.0F);
    }

    private static Vec3 cone(ServerLevel level, Vec3 axis, double half) {
        Vec3 up = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = axis.cross(up).normalize(), w = axis.cross(u).normalize();
        double a = level.random.nextDouble() * Math.PI * 2.0, t = Math.sqrt(level.random.nextDouble()) * half;
        return axis.scale(Math.cos(t)).add(u.scale(Math.sin(t) * Math.cos(a))).add(w.scale(Math.sin(t) * Math.sin(a))).normalize();
    }
}

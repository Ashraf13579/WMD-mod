package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class AirGuns {

    private AirGuns() {}

    public record Jet(UUID id, String name, Vec3 pos, Vec3 vel, int blocks, double dist) {
        public double speedBps() { return vel.length() * 20.0; }

        public Vec3 lead(double roundSpeed) { return pos.add(vel.scale(dist / Math.max(1.0, roundSpeed))); }
    }

    static Jet groundTarget(ServerLevel sl, UUID owner, int frequency, Vec3 gun, double range, long now) {
        if (!AirGate.on() || owner == null || gun == null) return null;
        Jet best = null;
        for (UUID id : TrackNet.aircraftTracks(sl, owner, frequency, now)) {
            AirPicture.Aircraft a = AirPicture.get(sl, id);
            if (a == null) continue;
            Vec3 p = a.pos();
            double d = p.distanceTo(gun);
            if (d > range) continue;
            double el = Math.toDegrees(Math.atan2(p.y - gun.y, Math.hypot(p.x - gun.x, p.z - gun.z)));
            if (el < 2.0 || el > 85.0) continue;
            if (best == null || d < best.dist()) best = new Jet(a.id(), a.name(), p, a.vel(), a.blocks(), d);
        }
        return best;
    }

    static Jet boresightTarget(ServerLevel sl, UUID ownShip, Vec3 muzzle, Vec3 dir, double range, double coneDeg) {
        if (!AirGate.on() || sl == null || muzzle == null || dir == null) return null;
        double tan = Math.tan(Math.toRadians(coneDeg));
        Jet best = null;
        for (AirPicture.Aircraft a : AirPicture.all(sl)) {
            if (a.id().equals(ownShip)) continue;
            Vec3 c = a.pos(), v = c.subtract(muzzle);
            double along = v.dot(dir);
            if (along <= 0.0 || along > range) continue;
            double off = v.subtract(dir.scale(along)).length();
            double half = a.bounds() == null ? 1.0 : 0.5 * Math.max(a.bounds().getXsize(), Math.max(a.bounds().getYsize(), a.bounds().getZsize()));
            if (off > along * tan + half) continue;
            if (best == null || along < best.dist()) best = new Jet(a.id(), a.name(), c, a.vel(), a.blocks(), along);
        }
        return best;
    }

    static double roundHit(double dist, double range, int blocks, double speedBps) {
        double q = Math.pow(Math.max(0.0, 1.0 - dist / Math.max(1.0, range)), 1.5);
        double p = Balance.CIWS_HIT_FAR + (Balance.CIWS_HIT_CLOSE - Balance.CIWS_HIT_FAR) * q;
        double size = Mth.clamp(AirPicture.sizeFactor(blocks), Balance.AIRGUN_SIZE_MIN, Balance.AIRGUN_SIZE_MAX);
        double speed = Mth.clamp(Balance.AIRGUN_SPEED_REF / Math.max(1.0, speedBps), Balance.AIRGUN_SPEED_MIN, 1.0);
        return Mth.clamp(p * size * speed, 0.0, 1.0);
    }

    static int flakFrags(ServerLevel sl, UUID id, Vec3 at, UUID owner) {
        if (!AirGate.on() || sl == null || id == null || at == null) return 0;
        AirPicture.Aircraft a = AirPicture.get(sl, id);
        if (a == null) return 0;
        Vec3 c = a.pos();
        double chance = roundHit(c.distanceTo(at), Balance.FLAK_JET_REACH, a.blocks(), 0.0);
        return burst(sl, id, at, c, Balance.FLAK_JET_FRAGS, chance, Balance.FLAK_JET_MAX_BLOCKS, owner);
    }

    static int burst(ServerLevel sl, UUID id, Vec3 gun, Vec3 centre, int rounds, double p, int maxBlocks, UUID owner) {
        if (!AirGate.on() || sl == null || id == null || gun == null || centre == null || rounds <= 0) return 0;
        int hits = 0;
        for (int i = 0; i < rounds; i++) if (sl.random.nextDouble() < p) hits++;
        hits = Math.min(hits, maxBlocks);
        if (hits == 0 || ShipStrike.spare(sl, id, owner)) return 0;
        Vec3 dir = centre.subtract(gun);
        if (dir.lengthSqr() < 1.0E-6) return 0;
        dir = dir.normalize();
        ShipStrike.chew(sl, id, gun, centre.add(dir.scale(Balance.AIRGUN_CHEW_PAST)), hits, (float) Balance.CIWS_SHIP_MAX_HARDNESS, owner);
        return hits;
    }

    static int chewGround(ServerLevel sl, Vec3 point, Vec3 dir, int rounds, int max) {
        TankDamage.hitAt(sl, point, rounds);
        int broken = 0;
        net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(point.add(dir.normalize().scale(0.1)));
        for (int i = 0; i < rounds && broken < max; i++) {
            if (sl.random.nextDouble() >= Balance.GUN_GROUND_CHEW) continue;
            if (!sl.isLoaded(p) || SableBridge.isOnShip(sl, p)) break;
            net.minecraft.world.level.block.state.BlockState st = sl.getBlockState(p);
            if (st.isAir()) { p = net.minecraft.core.BlockPos.containing(Vec3.atCenterOf(p).add(dir.normalize())); continue; }
            float hard = st.getDestroySpeed(sl, p);
            if (hard < 0 || hard > Balance.CIWS_SHIP_MAX_HARDNESS || !BlockRules.blastRemovable(sl, p, st)) break;
            if (sl.destroyBlock(p, false)) broken++;
        }
        return broken;
    }
}

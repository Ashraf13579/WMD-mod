package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class WardenOverdrive {

    private WardenOverdrive() {}

    static final class Drone {
        final int id;
        Vec3 pos, vel;
        int age;
        boolean dead;
        Drone(int id, Vec3 pos, Vec3 vel) { this.id = id; this.pos = pos; this.vel = vel; }
    }

    static void blowPlates(WardenEntity w, ServerLevel sl) {
        for (int i = 0; i < 8; i++) blowPlate(w, sl, i);
    }

    static void blowPlate(WardenEntity w, ServerLevel sl, int i) {
        BlockState plate = Wmd.ARMOUR_PLATE.get().defaultBlockState();
        Vec3 side = w.local(1.0, 0.0, 0.0).subtract(w.position());
        {
            double sx = i < 4 ? -1.0 : 1.0, lz = new double[] {2.8, 0.9, -0.9, -2.3}[i % 4];
            Vec3 at = w.local(sx * 4.8, 10.4, lz);
            Vec3 out = side.scale(sx).add(0.0, 0.7 + sl.random.nextDouble() * 0.5, (sl.random.nextDouble() - 0.5) * 0.6).normalize()
                    .scale(Balance.WARDEN_PLATE_KICK * (0.8 + sl.random.nextDouble() * 0.5));
            BlockPos b = BlockPos.containing(at);
            Debris.throwPiece(sl, List.of(b, b.above()), plate, out, new Vec3(sl.random.nextDouble() - 0.5, 0.0, sl.random.nextDouble() - 0.5));
            sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.6, 0.6, 0.6, 0.05);
            sl.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.08);
            WardenFight.sound(sl, at.x, at.y, at.z, WmdSounds.BLAST_WHUMP.get(), Balance.SND_RELEASE, 0.7F + 0.05F * i);
        }
        if (i == 0) fx(sl, w, WardenFxPayload.FLASH, w.getX(), w.getY() + 10.0, w.getZ(), 30);
    }

    static void tick(WardenEntity w, ServerLevel sl, WardenState d, Player t) {
        long now = sl.getGameTime();
        drones(w, sl, now);
        if (w.napalmLeft > 0) napalm(w, sl);
        eye(w, sl, t);
        if (t == null) return;
        if (--w.droneWait <= 0) {
            w.droneWait = Balance.WARDEN_DRONE_PERIOD;
            Vec3 bay = w.local(-1.9, 12.2, -1.6);
            for (int i = 0; i < Balance.WARDEN_DRONES; i++) {
                Vec3 v = new Vec3((sl.random.nextDouble() - 0.5) * 0.3, 0.35, (sl.random.nextDouble() - 0.5) * 0.3);
                w.drones.add(new Drone(w.nextDrone++, bay.add((sl.random.nextDouble() - 0.5) * 2.0, 0.0, (sl.random.nextDouble() - 0.5) * 2.0), v));
            }
            WardenFight.sound(sl, bay.x, bay.y, bay.z, WmdSounds.SW_LAUNCH.get(), Balance.SND_RELEASE, 0.9F);
        }
        if (--w.empWait <= 0) {
            w.empWait = Balance.WARDEN_EMP_PERIOD;
            WarheadFx.empField(sl, w.getX(), w.getY() + 8.0, w.getZ(), Balance.WARDEN_EMP_R, Balance.WARDEN_EMP_TICKS);
            WardenFxPayload emp = new WardenFxPayload(WardenFxPayload.EMP, (float) w.getX(), (float) (w.getY() + 8.0), (float) w.getZ(), (float) Balance.WARDEN_EMP_R, 0.0F, 0.0F, 40);
            for (net.minecraft.server.level.ServerPlayer p : sl.players()) if (p.distanceToSqr(w) < 128.0 * 128.0) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, emp);
            w.empAt = w.position();
            w.empUntil = now + Balance.WARDEN_EMP_TICKS;
        }
        double dist = Math.sqrt(t.distanceToSqr(w.getX(), t.getY(), w.getZ()));
        if (--w.slamWait <= 0 && w.slamT < 0 && dist >= Balance.WARDEN_SLAM_MIN && dist <= Balance.WARDEN_SLAM_MAX) {
            w.slamWait = Balance.WARDEN_SLAM_PERIOD;
            w.slamT = 0;
            w.slamFrom = w.position();
            double lx = Math.max(-WardenEntity.LEASH, Math.min(WardenEntity.LEASH, t.getX() - d.ox - 0.5)), lz = Math.max(-WardenEntity.LEASH, Math.min(WardenEntity.LEASH, t.getZ() - d.oz - 0.5));
            w.slamTo = new Vec3(d.ox + 0.5 + lx, w.getY(), d.oz + 0.5 + lz);
        }
        if (--w.napalmWait <= 0 && w.napalmLeft <= 0 && dist >= 6.0 && dist <= 40.0) {
            w.napalmWait = Balance.WARDEN_NAPALM_PERIOD;
            w.napalmLeft = Balance.WARDEN_NAPALM_LEN;
            w.napalmFrom = w.position();
            Vec3 dir = new Vec3(t.getX() - w.getX(), 0.0, t.getZ() - w.getZ());
            w.napalmDir = dir.lengthSqr() < 1.0E-6 ? w.forward() : dir.normalize();
        }
    }

    private static void drones(WardenEntity w, ServerLevel sl, long now) {
        if (w.drones.isEmpty()) return;
        for (Iterator<Drone> it = w.drones.iterator(); it.hasNext(); ) {
            Drone dr = it.next();
            if (dr.dead) { it.remove(); continue; }
            dr.age++;
            if (dr.age > Balance.WARDEN_DRONE_RISE) {
                Player p = nearest(sl, dr.pos, 128.0);
                if (p != null) {
                    Vec3 want = p.getEyePosition().subtract(0.0, 0.6, 0.0).subtract(dr.pos).normalize().scale(Balance.WARDEN_DRONE_SPEED);
                    dr.vel = dr.vel.lerp(want, Balance.WARDEN_DRONE_STEER);
                    if (p.distanceToSqr(dr.pos) < Balance.WARDEN_DRONE_HIT * Balance.WARDEN_DRONE_HIT) { boom(w, sl, dr, true); it.remove(); continue; }
                }
            }
            Vec3 next = dr.pos.add(dr.vel);
            BlockPos bp = BlockPos.containing(next);
            if (!sl.isLoaded(bp) || dr.age > Balance.WARDEN_DRONE_LIFE || !sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) { boom(w, sl, dr, true); it.remove(); continue; }
            boolean own = now < w.empUntil && dr.pos.distanceToSqr(w.empAt) <= Balance.WARDEN_EMP_R * Balance.WARDEN_EMP_R;
            if (dr.age % 5 == 0 && !own && EmpField.jammed(sl, bp)) { boom(w, sl, dr, false); it.remove(); continue; }
            dr.pos = next;
        }
        if (now % 2 == 0) {
            float[] xyz = new float[w.drones.size() * 3];
            int[] ids = new int[w.drones.size()];
            for (int i = 0; i < w.drones.size(); i++) {
                Drone dr = w.drones.get(i);
                ids[i] = dr.id; xyz[i * 3] = (float) dr.pos.x; xyz[i * 3 + 1] = (float) dr.pos.y; xyz[i * 3 + 2] = (float) dr.pos.z;
            }
            WardenDronesPayload pl = new WardenDronesPayload(w.getId(), ids, xyz);
            for (ServerPlayer p : sl.players()) if (p.distanceToSqr(w) < 160.0 * 160.0) PacketDistributor.sendToPlayer(p, pl);
        }
    }

    private static Player nearest(ServerLevel sl, Vec3 at, double r) {
        Player best = null;
        double bd = r * r;
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double q = p.distanceToSqr(at);
            if (q < bd) { bd = q; best = p; }
        }
        return best;
    }

    private static void boom(WardenEntity w, ServerLevel sl, Drone dr, boolean armed) {
        dr.dead = true;
        if (armed) {
            w.ownFire = true;
            try { WarheadFx.hurt(sl, dr.pos, Balance.WARDEN_DRONE_R, 3.0, 0.0, Balance.WARDEN_DRONE_DMG, 0.6, 2); } finally { w.ownFire = false; }
            sl.sendParticles(ParticleTypes.EXPLOSION, dr.pos.x, dr.pos.y, dr.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            WardenFight.sound(sl, dr.pos.x, dr.pos.y, dr.pos.z, WmdSounds.SW_BLAST.get(), Balance.SND_RELEASE, 1.0F);
        } else {
            sl.sendParticles(ParticleTypes.SMOKE, dr.pos.x, dr.pos.y, dr.pos.z, 6, 0.2, 0.2, 0.2, 0.02);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, dr.pos.x, dr.pos.y, dr.pos.z, 6, 0.2, 0.2, 0.2, 0.05);
        }
    }

    static Vec3 eyeAt(WardenEntity w) { return w.local(0.0, 10.6, 6.4); }

    static void eye(WardenEntity w, ServerLevel sl, Player t) {
        if (w.eyeT < 0) {
            if (t == null || w.railT >= 0 || --w.eyeWait > 0) return;
            if (!WardenAttacks.sees(sl, w, eyeAt(w), t.getEyePosition())) { w.eyeWait = 20; return; }
            w.eyeT = 0;
            Vec3 e = eyeAt(w);
            WardenFight.sound(sl, e.x, e.y, e.z, WmdSounds.FUSION_CHARGE.get(), Balance.SND_RELEASE, 0.9F);
        }
        w.eyeT++;
        w.setEye(w.eyeT);
        int lockAt = Balance.WARDEN_EYE_CHARGE - Balance.WARDEN_EYE_LOCK;
        if (w.eyeT <= lockAt && t != null) w.eyeAim = t.getEyePosition();
        Vec3 from = eyeAt(w);
        if (w.eyeT == lockAt) WardenFight.sound(sl, from.x, from.y, from.z, WmdSounds.SIGHT_LOCK.get(), Balance.SND_ALARM, 1.3F);
        if (w.eyeT < Balance.WARDEN_EYE_CHARGE) return;
        Vec3 aim = w.eyeAim == null ? from.add(w.forward().scale(40.0)) : w.eyeAim;
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) dir = w.forward();
        dir = dir.normalize();
        Vec3 end = from.add(dir.scale(Balance.WARDEN_EYE_RANGE));
        var hit = SableBridge.clipWorld(sl, new net.minecraft.world.level.ClipContext(from, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, w), null);
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = hit.getLocation();
        double w2 = Balance.WARDEN_EYE_W * Balance.WARDEN_EYE_W;
        Vec3 ab = end.subtract(from);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(Balance.WARDEN_EYE_W), e -> e != w)) {
            Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            double u = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(ab) / Math.max(1.0E-6, ab.lengthSqr())));
            if (from.add(ab.scale(u)).distanceToSqr(c) <= w2) { e.hurt(w.damageSources().mobAttack(w), Balance.WARDEN_EYE_DMG); e.igniteForSeconds(3.0F); }
        }
        sl.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.1);
        WardenFight.sound(sl, from.x, from.y, from.z, WmdSounds.FUSION_DISCHARGE.get(), Balance.SND_RELEASE, 1.0F);
        WardenFxPayload pl = new WardenFxPayload(WardenFxPayload.EYE, (float) from.x, (float) from.y, (float) from.z, (float) end.x, (float) end.y, (float) end.z, 8);
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(from) < 200.0 * 200.0) PacketDistributor.sendToPlayer(p, pl);
        w.eyeT = -1;
        w.setEye(0);
        w.eyeWait = (int) (Balance.WARDEN_EYE_PERIOD * (w.stage() >= 3 ? Balance.WARDEN_FAST_MUL : 1.0F));
    }

    static void blastAt(ServerLevel sl, Vec3 pos, double r) {
        for (WardenEntity w : sl.getEntitiesOfClass(WardenEntity.class, new AABB(pos, pos).inflate(160.0), e -> !e.drones.isEmpty())) {
            if (w.ownFire) continue;
            for (Drone dr : w.drones) if (!dr.dead && dr.pos.distanceToSqr(pos) <= (r + 1.0) * (r + 1.0)) boom(w, sl, dr, false);
        }
    }

    static void slamStep(WardenEntity w, ServerLevel sl) {
        int c = w.slamCrouch > 0 ? w.slamCrouch : Balance.WARDEN_SLAM_CROUCH, air = Balance.WARDEN_SLAM_AIR;
        w.slamT++;
        if (w.slamT <= c) {
            w.setPos(w.slamFrom.x, w.slamFrom.y - Math.sin(Math.PI * w.slamT / c) * 0.8, w.slamFrom.z);
            if (w.slamT == c) {
                WardenFight.sound(sl, w.getX(), w.getY(), w.getZ(), WmdSounds.BLAST_WHUMP.get(), Balance.SND_RELEASE, 0.6F);
                sl.sendParticles(ParticleTypes.CLOUD, w.getX(), w.getY() + 0.5, w.getZ(), 30, 4.0, 0.2, 4.0, 0.05);
            }
            return;
        }
        double u = Math.min(1.0, (w.slamT - c) / (double) air);
        Vec3 p = w.slamFrom.lerp(w.slamTo, u).add(0.0, 4.0 * Balance.WARDEN_SLAM_H * u * (1.0 - u), 0.0);
        w.setPos(p.x, p.y, p.z);
        if (u < 1.0) return;
        land(w, sl);
        w.slamT = -1;
        w.slamCrouch = 0;
    }

    static void hop(WardenEntity w, ServerLevel sl) {
        w.slamT = 0;
        w.slamCrouch = Balance.WARDEN_HUG_CROUCH;
        w.slamFrom = w.position();
        w.slamTo = w.position();
        sl.sendParticles(ParticleTypes.CLOUD, w.getX(), w.getY() + 0.6, w.getZ(), 60, 5.0, 0.3, 5.0, 0.12);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, w.getX(), w.getY() + 2.0, w.getZ(), 30, 4.0, 1.0, 4.0, 0.04);
        WardenFight.sound(sl, w.getX(), w.getY() + 2.0, w.getZ(), WmdSounds.HD_START.get(), Balance.SND_MECH, 0.6F);
    }

    private static void land(WardenEntity w, ServerLevel sl) {
        Vec3 at = w.position();
        w.ownFire = true;
        try { WarheadFx.hurt(sl, at.add(0.0, 0.5, 0.0), Balance.WARDEN_SLAM_R, 2.5, 0.0, 1.0, 0.0, 0); } finally { w.ownFire = false; }
        FlightManager fm = FlightManager.get(sl);
        fm.craters.add(new Crater((int) Math.floor(at.x), (int) Math.floor(at.y) - 1, (int) Math.floor(at.z), Balance.WARDEN_SLAM_R * 0.7, 0.6, 0.4, 0.0, 0));
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(Balance.WARDEN_SLAM_PUSH_R), e -> e != w)) {
            Vec3 away = e.position().subtract(at);
            double dd = away.length();
            if (dd > Balance.WARDEN_SLAM_PUSH_R || dd < 1.0E-3) continue;
            double k = Balance.WARDEN_SLAM_PUSH * (1.0 - dd / Balance.WARDEN_SLAM_PUSH_R);
            e.push(away.x / dd * k, 0.5 + k * 0.6, away.z / dd * k);
            e.hurtMarked = true;
        }
        WardenFight.sound(sl, at.x, at.y, at.z, WmdSounds.BLAST_WHUMP.get(), Balance.SND_RELEASE, 0.4F);
        fx(sl, w, WardenFxPayload.SLAM, at.x, at.y, at.z, 20);
    }

    private static void napalm(WardenEntity w, ServerLevel sl) {
        int done = Balance.WARDEN_NAPALM_LEN - w.napalmLeft;
        w.napalmLeft--;
        double u = 4.0 + done;
        Vec3 dir = w.napalmDir, perp = new Vec3(-dir.z, 0.0, dir.x);
        BlockState fire = Wmd.NAPALM_FIRE.get().defaultBlockState();
        int y = (int) Math.floor(w.napalmFrom.y);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int half = Balance.WARDEN_NAPALM_W / 2;
        for (int v = -half; v <= half; v++) {
            double x = w.napalmFrom.x + dir.x * u + perp.x * v, z = w.napalmFrom.z + dir.z * u + perp.z * v;
            p.set(Math.floor(x), y, Math.floor(z));
            if (!sl.isLoaded(p)) continue;
            BlockState s = sl.getBlockState(p);
            if ((s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty() && NapalmFireBlock.canSit(sl, p) && Config.Server.canBreak(sl, p))
                BlastWrites.set(sl, p, fire, Block.UPDATE_ALL);
        }
        Vec3 head = w.napalmFrom.add(dir.scale(u)).add(0.0, 0.5, 0.0);
        Vec3 nozzle = w.local(0.0, 8.0, 6.5);
        for (int i = 0; i < 6; i++) {
            Vec3 q = nozzle.lerp(head, i / 6.0);
            sl.sendParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 3, 0.3, 0.3, 0.3, 0.02);
        }
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, head.x, head.y + 1.0, head.z, 4, 1.0, 0.5, 1.0, 0.03);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(head, head).inflate(Balance.WARDEN_NAPALM_W, 3.0, Balance.WARDEN_NAPALM_W), e -> e != w))
            e.igniteForSeconds(8.0F);
        if (done % 8 == 0) WardenFight.sound(sl, head.x, head.y, head.z, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, Balance.SND_HEAVY, 0.6F);
    }

    static void fx(ServerLevel sl, WardenEntity w, int kind, double x, double y, double z, int dur) {
        WardenFxPayload pl = new WardenFxPayload(kind, (float) x, (float) y, (float) z, (float) x, (float) y, (float) z, dur);
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(x, y, z) < 160.0 * 160.0) PacketDistributor.sendToPlayer(p, pl);
    }
}

package com.ashraf.wmd;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class WardenAttacks {

    private WardenAttacks() {}

    record Shell(Vec3 to, long land) {}

    static void tick(WardenEntity w, ServerLevel sl, WardenState d, Player t) {
        long now = sl.getGameTime();
        shells(w, sl, now);
        if (t == null) { w.ciwsBurst = 0; w.rocketsLeft = 0; w.ciwsAcc = 0.0F; w.ciwsSpin = 0; w.podT = 0; w.setPod(0); w.setCiwsHot(false); return; }
        ciws(w, sl, d, t, now);
        rockets(w, sl, t, now);
        mortar(w, sl, t, now);
    }

    private static float fast(WardenEntity w) { return w.stage() >= 2 ? Balance.WARDEN_FAST_MUL : 1.0F; }

    private static void ciws(WardenEntity w, ServerLevel sl, WardenState d, Player t, long now) {
        Vec3 muzzle = w.local(0.0, 9.2, 5.4);
        Vec3 aim = t.getEyePosition().subtract(0.0, 0.5, 0.0);
        double dist = muzzle.distanceTo(aim);
        if (w.ciwsBurst <= 0) {
            w.setCiwsHot(false);
            if (--w.ciwsWait > 0) return;
            if (dist > Balance.WARDEN_CIWS_RANGE || !sees(sl, w, muzzle, aim)) { w.ciwsWait = 10; return; }
            w.ciwsBurst = Balance.WARDEN_CIWS_BURST;
            w.ciwsSpin = Balance.WARDEN_CIWS_SPINUP;
            w.ciwsWait = (int) (Balance.WARDEN_CIWS_PERIOD * fast(w));
        }
        w.setCiwsHot(true);
        if (w.ciwsSpin > 0) { w.ciwsSpin--; return; }
        w.ciwsBurst--;
        boolean clear = dist <= Balance.WARDEN_CIWS_RANGE && sees(sl, w, muzzle, aim);
        float hit = 0.0F;
        if (clear) {
            double q = Math.pow(Math.max(0.0, 1.0 - dist / Balance.WARDEN_CIWS_RANGE), 1.5);
            double p = Balance.WARDEN_CIWS_HIT_FAR + (Balance.WARDEN_CIWS_HIT_CLOSE - Balance.WARDEN_CIWS_HIT_FAR) * q;
            for (int i = 0; i < Balance.WARDEN_CIWS_ROUNDS; i++) if (sl.random.nextDouble() < p) hit += Balance.WARDEN_CIWS_DMG;
        }
        w.ciwsAcc += hit;
        if (now % 2 == 0) {
            Vec3 to = clear ? aim : muzzle.add(w.forward().scale(40.0));
            PacketDistributor.sendToPlayersNear(sl, null, muzzle.x, muzzle.y, muzzle.z, 200.0,
                    new CiwsFirePayload(new BlockPos(d.ox, d.oy, d.oz), muzzle.x, muzzle.y, muzzle.z, to.x, to.y, to.z, hit > 0.0F));
        }
        if (now % Balance.WARDEN_CIWS_DEAL == 0 && w.ciwsAcc > 0.0F) {
            t.hurt(w.damageSources().mobAttack(w), w.ciwsAcc);
            w.ciwsAcc = 0.0F;
        }
    }

    static boolean underRoof(ServerLevel sl, WardenEntity w, Player t) {
        WardenState d = WardenState.get(sl.getServer());
        double rx = t.getX() - (d.ox + 0.5), rz = t.getZ() - (d.oz + 0.5), ry = t.getY() - d.oy;
        boolean hall = Math.abs(rx) <= WardenBunker.HALL + 1 && Math.abs(rz) <= WardenBunker.HALL + 1 && ry >= WardenBunker.F && ry <= WardenBunker.F + WardenBunker.HALL_H + 1;
        return hall || sees(sl, w, w.local(0.0, 13.2, -2.8), t.getEyePosition());
    }

    static boolean sees(ServerLevel sl, WardenEntity w, Vec3 from, Vec3 to) {
        return sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w)).getType() == HitResult.Type.MISS;
    }

    static int podFire(int k) { return Balance.WARDEN_ROCKET_LOCK + 2 + 2 * k; }

    static boolean podRight(int k) { return (Balance.WARDEN_ROCKETS - k) % 2 == 0; }

    private static void rockets(WardenEntity w, ServerLevel sl, Player t, long now) {
        if (w.podT > 0) w.setPod(++w.podT);
        if (w.rocketsLeft > 0) {
            int k = Balance.WARDEN_ROCKETS - w.rocketsLeft;
            if (w.podT < podFire(k)) {
                if (w.podT <= Balance.WARDEN_ROCKET_LOCK + 1) w.rocketAim = t.position().add(0.0, 0.5, 0.0);
                return;
            }
            if (k == 0) WardenFight.sound(sl, w.getX(), w.getY() + 13.0, w.getZ(), WmdSounds.RP_RIPPLE.get(), Balance.SND_RELEASE, 1.0F);
            Vec3 from = w.local(podRight(k) ? 2.6 : -2.6, 13.0, 0.6);
            double s = Balance.WARDEN_ROCKET_SCATTER;
            Vec3 tgt = w.rocketAim.add((sl.random.nextDouble() - 0.5) * 2.0 * s, 0.0, (sl.random.nextDouble() - 0.5) * 2.0 * s);
            Vec3 dv = tgt.subtract(from);
            Vec3 dir = dv.add(0.0, dv.length() * 0.04, 0.0).normalize();
            AirRockets.fire(sl, WardenFight.WARDEN_OWNER, from, dir, Vec3.ZERO, null, k == 0, w.rocketsLeft == 1);
            w.rocketsLeft--;
            return;
        }
        if (w.podT > podFire(Balance.WARDEN_ROCKETS - 1) + 14) { w.podT = 0; w.setPod(0); }
        if (--w.rocketWait > 0) return;
        double dist = t.distanceTo(w);
        if (dist < Balance.WARDEN_ROCKET_MIN || dist > Balance.WARDEN_ROCKET_RANGE) { w.rocketWait = 20; return; }
        if (!sees(sl, w, w.local(0.0, 13.0, 0.6), t.getEyePosition())) { w.rocketWait = 20; return; }
        w.rocketsLeft = Balance.WARDEN_ROCKETS;
        w.rocketAim = t.position().add(0.0, 0.5, 0.0);
        w.rocketWait = (int) (Balance.WARDEN_ROCKET_PERIOD * fast(w));
        w.podT = 1;
        w.setPod(1);
        WardenFight.sound(sl, w.getX(), w.getY() + 13.0, w.getZ(), WmdSounds.SIGHT_LOCK.get(), Balance.SND_ALARM, 1.0F);
    }

    private static void mortar(WardenEntity w, ServerLevel sl, Player t, long now) {
        if (--w.mortarWait > 0) return;
        double dx = t.getX() - w.getX(), dz = t.getZ() - w.getZ();
        if (Math.sqrt(dx * dx + dz * dz) < Balance.WARDEN_MORTAR_MIN) { w.mortarWait = 20; return; }
        if (!underRoof(sl, w, t)) { w.mortarWait = 20; return; }
        w.mortarWait = (int) (Balance.WARDEN_MORTAR_PERIOD * fast(w));
        Vec3 from = w.local(0.0, 13.2, -2.8);
        WardenFight.sound(sl, from.x, from.y, from.z, WmdSounds.MORTAR_FIRE.get(), Balance.SND_RELEASE, 0.9F);
        w.shotMortar();
        double s = Balance.WARDEN_MORTAR_SPREAD;
        for (int i = 0; i < Balance.WARDEN_MORTARS; i++) {
            int dur = Balance.WARDEN_MORTAR_FLIGHT + 6 * i;
            Vec3 c = i == 0 ? t.position().add(t.getDeltaMovement().multiply(dur, 0.0, dur))
                    : t.position().add((sl.random.nextDouble() - 0.5) * 2.0 * s, 0.0, (sl.random.nextDouble() - 0.5) * 2.0 * s);
            Vec3 land = floor(sl, c);
            if (land == null) continue;
            w.shells.add(new Shell(land, now + dur));
            WardenFxPayload ring = new WardenFxPayload(WardenFxPayload.RING, (float) land.x, (float) land.y, (float) land.z, (float) land.x, (float) land.y, (float) land.z, dur);
            WardenFxPayload shell = new WardenFxPayload(WardenFxPayload.SHELL, (float) from.x, (float) from.y, (float) from.z, (float) land.x, (float) land.y, (float) land.z, dur);
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(land) > 160.0 * 160.0) continue;
                PacketDistributor.sendToPlayer(p, ring);
                PacketDistributor.sendToPlayer(p, shell);
            }
        }
    }

    private static Vec3 floor(ServerLevel sl, Vec3 c) {
        BlockPos.MutableBlockPos p = BlockPos.containing(c.x, c.y + 2.0, c.z).mutable();
        for (int i = 0; i < 48; i++) {
            if (!sl.isLoaded(p)) return null;
            if (!sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()) return new Vec3(c.x, p.getY() + 1.0, c.z);
            p.move(0, -1, 0);
        }
        return null;
    }

    private static void shells(WardenEntity w, ServerLevel sl, long now) {
        for (Iterator<Shell> it = w.shells.iterator(); it.hasNext(); ) {
            Shell s = it.next();
            if (now < s.land()) continue;
            it.remove();
            w.ownFire = true;
            try {
                WarheadFx.hurt(sl, s.to(), Balance.MORTAR_HE_RADIUS, 3.5, 0.0, 0.5, 0.6, 3);
            } finally {
                w.ownFire = false;
            }
            ArtShell fx = new ArtShell(0L, null, "", Artillery.MORTAR, Artillery.HE, s.to().x, s.to().y, s.to().z, s.to().x, s.to().y, s.to().z, now, 1, 0L, 0, false, true);
            ArtilleryManager.sendImpact(sl, fx, s.to().x, s.to().y, s.to().z);
        }
    }
}

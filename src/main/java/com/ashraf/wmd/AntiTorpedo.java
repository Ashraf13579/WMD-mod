package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AntiTorpedo {

    private AntiTorpedo() {}

    private static final class State {
        Vec3 vel, heading;
        double run, depth;
        UUID target;
        long nextSeek;
        State(Vec3 dir, double speed0) {
            Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : dir.normalize();
            vel = d.scale(speed0);
            Vec3 h = new Vec3(d.x, 0, d.z);
            heading = h.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : h.normalize();
            depth = -1.0;
        }
    }

    private static final Map<TorpedoEntity, State> STATE = Statics.server(new WeakHashMap<>());

    private static final Map<UUID, Long> CLAIMED = Statics.server(new HashMap<>());

    static void track(TorpedoEntity att, TorpedoEntity threat, Vec3 dir, double speed0) {
        State s = new State(dir, speed0);
        if (threat != null) s.target = threat.getUUID();
        STATE.put(att, s);
    }

    static boolean hostile(TorpedoEntity t, UUID owner) {
        if (t == null || !t.isAlive()) return false;
        TorpedoKind k = t.kind();
        if (k == TorpedoKind.ATT || k.drone()) return false;
        return t.owner() == null || !t.owner().equals(owner);
    }

    static boolean autoFire(ServerLevel sl, TorpedoLauncherBlockEntity l) {
        if (!l.hasAtt()) return false;
        UUID owner = l.getOwner();
        List<SonarNet.Sensor> ears = SonarNet.sensors(sl, owner, l.frequency());
        if (ears.isEmpty()) return false;
        Vec3 muzzle = l.worldMuzzle()[0];
        UUID ship = SableBridge.shipIdAt(sl, l.getBlockPos());
        SableBridge.ShipInfo si = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 guard = si != null ? si.worldCenter() : muzzle;
        double cpaMax = Balance.ATT_CPA + (si != null ? 0.5 * Math.max(si.worldBounds().getXsize(), si.worldBounds().getZsize()) : 0.0);
        long now = sl.getGameTime();
        CLAIMED.values().removeIf(t -> t < now);
        TorpedoEntity best = null;
        double bd = Double.MAX_VALUE;
        for (TorpedoEntity t : sl.getEntitiesOfClass(TorpedoEntity.class, new AABB(muzzle, muzzle).inflate(Balance.ATT_DETECT))) {
            if (!hostile(t, owner) || CLAIMED.containsKey(t.getUUID())) continue;
            Vec3 v = t.getDeltaMovement();
            if (v.lengthSqr() < 1e-4) continue;
            Vec3 r = guard.subtract(t.position());
            double ts = r.dot(v) / v.lengthSqr();
            if (ts <= 0.0) continue;
            if (t.position().add(v.scale(ts)).distanceTo(guard) > cpaMax) continue;
            if (!heard(sl, ears, ship, t)) continue;
            double d = t.position().distanceTo(muzzle);
            if (d < bd) { bd = d; best = t; }
        }
        if (best == null) return false;
        if (l.fireAtt(best) != TorpedoLauncherBlockEntity.S_FIRED) return false;
        CLAIMED.put(best.getUUID(), now + Balance.ATT_CLAIM_TICKS);
        Wmd.LOGGER.info("Anti-torpedo fired from {} at {} {} blocks out", l.getBlockPos().toShortString(), best.kind().id, (int) bd);
        return true;
    }

    private static boolean heard(ServerLevel sl, List<SonarNet.Sensor> ears, UUID launcherShip, TorpedoEntity t) {
        for (SonarNet.Sensor s : ears) {
            if (s.ear() == null || !CommsLink.linked(sl, launcherShip, s.ship())) continue;
            double d = s.ear().distanceTo(t.position());
            if (d <= s.range() && NoiseNet.received(t.kind().db(), d) >= Balance.NOISE_FLOOR_DB) return true;
        }
        return false;
    }

    static void tick(TorpedoEntity e, ServerLevel sl) {
        State s = STATE.computeIfAbsent(e, k -> new State(e.getDeltaMovement().lengthSqr() > 1e-6 ? e.getDeltaMovement() : new Vec3(0, 0, -1), 0.6));
        TorpedoKind k = TorpedoKind.ATT;
        Vec3 pos = e.position();
        if (s.run >= k.range()) { burst(sl, e, pos, "end"); return; }
        long now = sl.getGameTime();
        boolean wet = sl.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
        if (!wet) {
            s.vel = UnderwaterMotion.coast(sl, pos, s.vel);
        } else {
            if (s.depth <= 0.0) s.depth = Math.max(Balance.TORP_MIN_DEPTH, NavalLayer.depthBelowSurface(sl, pos));
            TorpedoEntity tgt = s.target == null ? null : sl.getEntity(s.target) instanceof TorpedoEntity t && hostile(t, e.owner()) ? t : null;
            if (tgt == null && now >= s.nextSeek) {
                s.nextSeek = now + 5;
                tgt = seek(sl, e, pos, s);
                s.target = tgt == null ? null : tgt.getUUID();
            }
            double want = s.depth;
            if (tgt != null && s.run >= Balance.ATT_ARM * 0.5) {
                double lead = Mth.clamp(tgt.position().distanceTo(pos) / Math.max(0.2, k.speed()), 0.0, 20.0);
                Vec3 aim = tgt.position().add(tgt.getDeltaMovement().scale(lead));
                double cur = Math.atan2(s.heading.x, -s.heading.z), wantA = Math.atan2(aim.x - pos.x, -(aim.z - pos.z));
                double dA = Mth.wrapDegrees(Math.toDegrees(wantA - cur));
                double na = cur + Math.toRadians(Mth.clamp(dA, -Balance.ATT_TURN, Balance.ATT_TURN));
                s.heading = new Vec3(Math.sin(na), 0.0, -Math.cos(na));
                want = Math.max(Balance.TORP_MIN_DEPTH, NavalLayer.depthBelowSurface(sl, aim));
            }
            Vec3 target = UnderwaterMotion.run(sl, pos, s.heading, k.speed(), want, 0.4);
            s.vel = s.vel.add(target.subtract(s.vel).scale(0.5));
        }
        Vec3 next = pos.add(s.vel);
        boolean armed = s.run >= Balance.ATT_ARM;
        if (armed) {
            for (TorpedoEntity t : sl.getEntitiesOfClass(TorpedoEntity.class, new AABB(next, next).inflate(Balance.ATT_FUSE))) {
                if (t != e && hostile(t, e.owner()) && t.position().distanceTo(next) <= Balance.ATT_FUSE) { burst(sl, e, next, "kill"); return; }
            }
        }
        BlockHitResult hit = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        if (hit.getType() == HitResult.Type.BLOCK) { burst(sl, e, hit.getLocation(), "ground"); return; }
        if (e.tickCount % Balance.TORP_NOISE_TICKS == 0) NoiseNet.emit(sl, e.getUUID(), next, k.db(), NoiseNet.Kind.TORPEDO, e.owner(), null);
        s.run += s.vel.length();
        e.setPos(next.x, next.y, next.z);
        e.setDeltaMovement(s.vel);
    }

    private static TorpedoEntity seek(ServerLevel sl, TorpedoEntity e, Vec3 pos, State s) {
        TorpedoEntity best = null;
        double bd = Double.MAX_VALUE, cos = Math.cos(Math.toRadians(70.0));
        for (TorpedoEntity t : sl.getEntitiesOfClass(TorpedoEntity.class, new AABB(pos, pos).inflate(Balance.ATT_SEEK))) {
            if (t == e || !hostile(t, e.owner())) continue;
            Vec3 d = t.position().subtract(pos);
            double l = d.length();
            if (l > Balance.ATT_SEEK || l < 1e-3) continue;
            Vec3 h = new Vec3(d.x, 0, d.z);
            if (h.lengthSqr() > 1e-4 && h.normalize().dot(s.heading) < cos) continue;
            if (l < bd) { bd = l; best = t; }
        }
        return best;
    }

    static void burst(ServerLevel sl, TorpedoEntity e, Vec3 at, String why) {
        UUID owner = e.owner();
        STATE.remove(e);
        e.discard();
        int kills = 0;
        for (TorpedoEntity t : sl.getEntitiesOfClass(TorpedoEntity.class, new AABB(at, at).inflate(Balance.ATT_FUSE + 1.0))) {
            if (!hostile(t, owner) || t.position().distanceTo(at) > Balance.ATT_FUSE + 1.0) continue;
            tell(sl, t.owner(), Component.translatable("message.wmd.att.lost", Component.translatable("item.wmd." + t.kind().id)));
            sl.sendParticles(ParticleTypes.BUBBLE, t.getX(), t.getY(), t.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            t.discard();
            kills++;
        }
        UnderwaterBlast.go(sl, at, UnderwaterBlast.DEPTH_CHARGE, Balance.ATT_BLAST, owner, e);
        tell(sl, owner, Component.translatable(kills > 0 ? "message.wmd.att.kill" : "message.wmd.att." + why, kills));
        Wmd.LOGGER.info("Anti-torpedo {} at {} {} {}: {} torpedo(es) destroyed", why, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), kills);
    }

    private static void tell(ServerLevel sl, UUID who, Component c) {
        if (who != null && sl.getServer().getPlayerList().getPlayer(who) instanceof ServerPlayer p) p.displayClientMessage(c, false);
    }
}

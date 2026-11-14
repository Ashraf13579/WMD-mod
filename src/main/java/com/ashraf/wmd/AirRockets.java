package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AirRockets {

    private AirRockets() {}

    static final class Salvo {
        final UUID owner;
        int fired, ended, onCraft;
        boolean closed;
        Salvo(UUID owner) { this.owner = owner; }
    }

    public static final class Shot {
        final long id;
        final UUID owner, ownShip;
        final ResourceKey<Level> dim;
        final boolean lead;
        final Salvo salvo;
        final double maxSpeed;
        Vec3 pos, vel;
        int age;
        double flown;
        boolean ended;

        Shot(long id, UUID owner, UUID ownShip, ResourceKey<Level> dim, boolean lead, Salvo salvo, Vec3 pos, Vec3 vel, double maxSpeed) {
            this.id = id; this.owner = owner; this.ownShip = ownShip; this.dim = dim; this.lead = lead; this.salvo = salvo; this.pos = pos; this.vel = vel;
            this.maxSpeed = maxSpeed;
        }

        public Vec3 pos() { return pos; }
        boolean burning() { return age < Balance.RPOD_BURN; }
    }

    private static final List<Shot> SHOTS = Statics.server(new ArrayList<>());

    private static final java.util.Map<UUID, Salvo> OPEN = Statics.server(new java.util.HashMap<>());
    private static boolean warned;

    public static List<Shot> inFlight(ServerLevel level) {
        List<Shot> out = new ArrayList<>();
        for (Shot s : SHOTS) if (s.dim == level.dimension()) out.add(s);
        return out;
    }

    static String fire(ServerLevel sl, UUID owner, Vec3 from, Vec3 dir, Vec3 carrierVel, UUID ownShip, boolean lead, boolean last) {
        if (!AirGate.on()) return "the 1.2 content is off";
        int mine = 0, here = 0;
        for (Shot s : SHOTS) if (s.dim == sl.dimension()) { here++; if (owner != null && owner.equals(s.owner)) mine++; }
        if (here >= Balance.RPOD_MAX_PER_DIM || mine >= Balance.RPOD_MAX_PER_OWNER) return "too many rockets in the air";
        Vec3 d = spread(dir, Balance.RPOD_SPREAD, sl.random.nextDouble(), sl.random.nextDouble());
        Vec3 v = carrierVel.add(d.scale(Balance.RPOD_KICK));
        Salvo salvo = OPEN.computeIfAbsent(owner == null ? new UUID(0L, 0L) : owner, Salvo::new);
        salvo.fired++;
        if (last) { salvo.closed = true; OPEN.remove(salvo.owner); }
        SHOTS.add(new Shot(FlightManager.get(sl).nextShotId(), owner, ownShip, sl.dimension(), lead, salvo, from, v, v.length() + Balance.RPOD_SPEED));
        return null;
    }

    static void cut(UUID owner) {
        Salvo v = OPEN.remove(owner == null ? new UUID(0L, 0L) : owner);
        if (v != null) v.closed = true;
    }

    static Vec3 spread(Vec3 dir, double deg, double u, double w) {
        Vec3 a = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p = dir.cross(a).normalize(), q = dir.cross(p).normalize();
        double r = Math.toRadians(deg) * Math.sqrt(u), t = w * Math.PI * 2.0;
        return dir.add(p.scale(Math.tan(r) * Math.cos(t))).add(q.scale(Math.tan(r) * Math.sin(t))).normalize();
    }

    static Vec3 nextVel(Vec3 v, int age, double maxSpeed) {
        if (age < Balance.RPOD_BURN) {
            double sp = Math.min(maxSpeed, v.length() + Balance.RPOD_SPEED / Math.max(1, Balance.RPOD_BOOST));
            Vec3 n = v.lengthSqr() < 1.0E-9 ? v : v.normalize().scale(sp);
            return n.add(0.0, -Balance.RPOD_G_BURN, 0.0);
        }
        return v.scale(1.0 - Balance.RPOD_DRAG).add(0.0, -Balance.ART_GRAVITY, 0.0);
    }

    static Vec3[] airPoint(Vec3 from, Vec3 dir, Vec3 carrierVel, Vec3 tgt, Vec3 tgtVel) {
        Vec3 v = carrierVel.add(dir.scale(Balance.RPOD_KICK)), p = from;
        double max = v.length() + Balance.RPOD_SPEED, flown = 0.0, best = Double.MAX_VALUE;
        Vec3 bp = null, bt = null;
        for (int age = 0; age < Balance.RPOD_LIFE; age++) {
            v = nextVel(v, age, max);
            p = p.add(v);
            flown += v.length();
            Vec3 t = tgt.add(tgtVel.scale(age + 1));
            double d = p.distanceToSqr(t);
            if (d < best) { best = d; bp = p; bt = t; }
            else if (bp != null) break;
            if (flown > Balance.RPOD_RANGE_MAX * 1.3) return null;
        }
        return bp == null ? null : new Vec3[] { bp, bt };
    }

    static Vec3 impactPoint(ServerLevel sl, Vec3 from, Vec3 dir, Vec3 carrierVel, UUID ownShip) {
        Vec3 v = carrierVel.add(dir.scale(Balance.RPOD_KICK)), p = from;
        double max = v.length() + Balance.RPOD_SPEED, flown = 0.0;
        double ground = Double.NaN;
        for (int age = 0; age < Balance.RPOD_LIFE; age++) {
            v = nextVel(v, age, max);
            Vec3 to = p.add(v);
            BlockPos bp = BlockPos.containing(to);
            if (sl.getChunkSource().getChunkNow(bp.getX() >> 4, bp.getZ() >> 4) == null) {

                if (Double.isNaN(ground)) return null;
                if (to.y <= ground) return new Vec3(to.x, ground, to.z);
                flown += v.length();
                p = to;
                if (flown > Balance.RPOD_RANGE_MAX * 1.3) return p;
                continue;
            }
            ground = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
            BlockHitResult hit = sl.clip(new ClipContext(p, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK) {
                UUID hs = SableBridge.shipIdAt(sl, hit.getBlockPos());
                if (!(hs != null && hs.equals(ownShip) && flown < Balance.RPOD_ARM)) return hit.getLocation();
            }
            flown += v.length();
            p = to;
            if (flown > Balance.RPOD_RANGE_MAX * 1.3) return p;
        }
        return p;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!AirGate.on()) { if (!SHOTS.isEmpty()) SHOTS.clear(); return; }
        if (SHOTS.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        try {
            for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
                Shot s = it.next();
                if (s.dim != sl.dimension()) continue;
                step(sl, s);
                if (s.ended) { it.remove(); ended(sl, s); }
            }
            broadcast(sl);
        } catch (Throwable t) {
            if (!warned) { warned = true; Wmd.LOGGER.error("WMD air rockets failed (rockets in the air dropped)", t); }
            SHOTS.removeIf(s -> s.dim == sl.dimension());
        }
    }

    static boolean reach(ServerLevel sl, Vec3 to) {
        BlockPos bp = BlockPos.containing(to);
        int base = sl.getChunkSource().getGenerator().getBaseHeight(bp.getX(), bp.getZ(), net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                sl, sl.getChunkSource().randomState());
        if (to.y > base + Balance.RPOD_AIR_CLEAR) return true;
        long now = sl.getGameTime();
        if (now != loadTick) { loadTick = now; loadsLeft = Balance.RPOD_CHUNK_LOADS; }
        if (loadsLeft <= 0) return false;
        loadsLeft--;
        sl.getChunk(bp.getX() >> 4, bp.getZ() >> 4);
        return sl.hasChunkAt(bp);
    }

    private static long loadTick = Long.MIN_VALUE;
    private static int loadsLeft;

    private static void step(ServerLevel sl, Shot s) {
        if (s.age >= Balance.RPOD_LIFE) { puff(sl, s); return; }
        s.vel = nextVel(s.vel, s.age, s.maxSpeed);
        s.age++;
        Vec3 from = s.pos, to = from.add(s.vel);
        BlockPos bp = BlockPos.containing(to);
        if (!sl.hasChunkAt(bp) && !reach(sl, to)) { s.ended = true; return; }
        BlockHitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        double hitDist = Double.MAX_VALUE;
        UUID hitShip = null;
        if (hit.getType() == HitResult.Type.BLOCK) {
            hitShip = SableBridge.shipIdAt(sl, hit.getBlockPos());
            hitDist = hit.getLocation().distanceTo(from);
        }

        Entity hitEntity = null;
        if (s.flown >= Balance.RPOD_ARM) {
            AABB box = new AABB(from, to).inflate(0.6);
            for (Entity e : sl.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
                var o = e.getBoundingBox().inflate(0.3).clip(from, to);
                if (o.isPresent()) { double dd = o.get().distanceTo(from); if (dd < hitDist) { hitDist = dd; hitEntity = e; hitShip = null; } }
            }
        }
        if (hitDist < Double.MAX_VALUE) {
            Vec3 dir = s.vel.lengthSqr() < 1.0E-9 ? Vec3.ZERO : s.vel.normalize();
            Vec3 at = from.add(dir.scale(Math.max(0.0, hitDist - 0.2)));
            s.flown += hitDist;
            s.pos = at;
            if (hitEntity == null && hitShip != null && hitShip.equals(s.ownShip) && s.flown < Balance.RPOD_ARM) { puff(sl, s); return; }
            if (hitShip != null && !hitShip.equals(s.ownShip)) s.salvo.onCraft++;
            Mlrs.airBurst(sl, at, s.owner);
            s.ended = true;
            return;
        }
        s.flown += s.vel.length();
        s.pos = to;
    }

    private static void puff(ServerLevel sl, Shot s) {
        s.ended = true;
        double r2 = Balance.AAM_FX_RANGE * Balance.AAM_FX_RANGE;
        AamBurstPayload p = new AamBurstPayload(s.id, s.pos.x, s.pos.y, s.pos.z, false);
        for (ServerPlayer sp : sl.players()) if (sp.distanceToSqr(s.pos) <= r2) PacketDistributor.sendToPlayer(sp, p);
    }

    private static void ended(ServerLevel sl, Shot s) {
        Salvo v = s.salvo;
        v.ended++;
        if (!v.closed || v.ended < v.fired || v.owner == null || (v.owner.getMostSignificantBits() == 0L && v.owner.getLeastSignificantBits() == 0L)) return;
        if (v.fired < 2 && v.onCraft == 0) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(v.owner);
        if (p != null) p.displayClientMessage(Component.translatable("message.wmd.rocket_pod.result", v.fired, v.onCraft), true);
        CcBattleLog.add(sl.getServer(), v.owner, CcBattleLog.HIT, "log.wmd.rocket_salvo", Integer.toString(v.fired), Integer.toString(v.onCraft),
                Integer.toString(Mth.floor(s.pos.x)), Integer.toString(Mth.floor(s.pos.z)));
    }

    private static void broadcast(ServerLevel sl) {
        List<Shot> here = new ArrayList<>();
        for (Shot s : SHOTS) if (s.dim == sl.dimension() && !s.ended) here.add(s);
        if (here.isEmpty()) return;
        double r2 = Balance.AAM_FX_RANGE * Balance.AAM_FX_RANGE;
        for (ServerPlayer sp : sl.players()) {
            List<AirRocketPayload.Entry> l = new ArrayList<>();
            for (Shot s : here) {
                if (sp.distanceToSqr(s.pos) > r2 || l.size() >= AirRocketPayload.MAX) continue;
                l.add(new AirRocketPayload.Entry(s.id, s.pos.x, s.pos.y, s.pos.z, (float) s.vel.x, (float) s.vel.y, (float) s.vel.z, s.burning(), s.lead, s.age <= 1));
            }
            if (!l.isEmpty()) PacketDistributor.sendToPlayer(sp, new AirRocketPayload(l));
        }
    }
}

package com.ashraf.wmd;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Forcefield {

    private Forcefield() {}

    public static final int FX_HIT = 0, FX_CRACK = 1, FX_SHATTER = 2, FX_TOUCH = 3;

    private static int inside;

    public static void inside(Runnable r) {
        inside++;
        try { r.run(); } finally { inside--; }
    }

    static boolean pierces(WarheadType t, int tier) {
        return t == WarheadType.RAILGUN || t == WarheadType.ANTIMATTER || t == WarheadType.ORBITAL_ROD || t == WarheadType.ASTEROID && tier > 1;
    }

    static int drain(double r) {
        long v = (long) Balance.FF_HIT_FE + (long) (Balance.FF_DRAIN_PER_R2 * r * r);
        return (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, v));
    }

    public static boolean absorbs(ServerLevel sl, double x, double y, double z, int tier, WarheadType t) {
        if (inside > 0 || ForcefieldBlockEntity.LOADED.isEmpty()) return false;
        if (t == WarheadType.INTERCEPTOR || t == WarheadType.ASAT || t == WarheadType.CUBESAT || t == WarheadType.DECOY) return false;
        boolean pierce = pierces(t, tier);
        double r = Missiles.craterRadius(Math.max(1, tier)) * Math.max(0.3, t.crater);
        int drain = drain(r);
        for (ForcefieldBlockEntity be : new java.util.ArrayList<>(ForcefieldBlockEntity.LOADED)) {
            if (be.getLevel() != sl || be.isRemoved() || !be.protecting()) continue;
            Vec3 c = be.centre();
            Vec3 d = new Vec3(x - c.x, y - c.y, z - c.z);
            if (d.length() > be.radius() + 1.0 || d.y < -6.0) continue;

            double hd = Math.hypot(d.x, d.z), sy = Math.sqrt(Math.max(0.0, be.radius() * be.radius() - hd * hd));
            Vec3 at = new Vec3(x, c.y + sy, z), dir = at.subtract(c).lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : at.subtract(c).normalize();
            boolean took = be.absorb(sl, dir, drain, pierce);
            if (took) PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 8000, new ImpactPayload(at.x, at.y, at.z, tier, t.ordinal(), 0, 0.0F, ImpactPayload.SHELL));
            Wmd.LOGGER.info("[forcefield] {} hit by {} tier {} at {} {} {}: drain {} FE, {}", be.getBlockPos().toShortString(), t.id, tier,
                    Math.round(x), Math.round(y), Math.round(z), drain, !took ? "the dome BREAKS, the blast goes through"
                            : be.state() == ForcefieldBlockEntity.CRACK ? "OVERLOAD: it takes this hit and COLLAPSES" : "ABSORBED (" + be.pct() + " % left)");
            if (took) return true;
        }
        return false;
    }

    public record Crossing(ForcefieldBlockEntity be, Vec3 at) {}

    public static Crossing crossing(ServerLevel sl, Vec3 a, Vec3 b) {
        if (ForcefieldBlockEntity.LOADED.isEmpty()) return null;
        for (ForcefieldBlockEntity be : ForcefieldBlockEntity.LOADED) {
            if (be.getLevel() != sl || be.isRemoved() || !be.protecting()) continue;
            Vec3 c = be.centre();
            double r = be.radius();
            if (a.distanceToSqr(c) <= r * r || b.distanceToSqr(c) > r * r || b.y < c.y - 6.0) continue;
            Vec3 d = b.subtract(a), f = a.subtract(c);
            double qa = d.lengthSqr(), qb = 2.0 * f.dot(d), qc = f.lengthSqr() - r * r, disc = qb * qb - 4.0 * qa * qc;
            double t = qa < 1.0E-9 || disc < 0.0 ? 1.0 : Math.max(0.0, Math.min(1.0, (-qb - Math.sqrt(disc)) / (2.0 * qa)));
            return new Crossing(be, a.add(d.scale(t)));
        }
        return null;
    }

    public static boolean shellHit(ServerLevel sl, Crossing c, int tier, WarheadType t) {
        if (inside > 0 || t == WarheadType.INTERCEPTOR || t == WarheadType.ASAT || t == WarheadType.CUBESAT || t == WarheadType.DECOY) return false;
        boolean pierce = pierces(t, tier);
        double r = Missiles.craterRadius(Math.max(1, tier)) * Math.max(0.3, t.crater);
        int drain = drain(r);
        Vec3 dir = c.at().subtract(c.be().centre()).normalize();
        boolean took = c.be().absorb(sl, dir, drain, pierce);
        Wmd.LOGGER.info("[forcefield] {} : {} tier {} hit the SHELL at {} {} {}: drain {} FE, {}", c.be().getBlockPos().toShortString(), t.id, tier,
                Math.round(c.at().x), Math.round(c.at().y), Math.round(c.at().z), drain, !took ? "the dome BREAKS, it flies on"
                        : c.be().state() == ForcefieldBlockEntity.CRACK ? "OVERLOAD: it takes this hit and COLLAPSES" : "burst on the shell (" + c.be().pct() + " % left)");
        if (took) PacketDistributor.sendToPlayersNear(sl, null, c.at().x, c.at().y, c.at().z, 8000, new ImpactPayload(c.at().x, c.at().y, c.at().z, tier, t.ordinal(), 0, 0.0F, ImpactPayload.SHELL));
        return took;
    }

    public static boolean verticalHit(ServerLevel sl, double x, double z, double h0, double h1, int tier, WarheadType t) {
        if (ForcefieldBlockEntity.LOADED.isEmpty()) return false;
        for (ForcefieldBlockEntity be : ForcefieldBlockEntity.LOADED) {
            if (be.getLevel() != sl || be.isRemoved() || !be.protecting()) continue;
            Vec3 c = be.centre();
            double r = be.radius(), d = Math.hypot(x - c.x, z - c.z);
            if (d >= r) continue;
            double sy = Math.sqrt(r * r - d * d);
            if (h0 > sy && h1 <= sy) return shellHit(sl, new Crossing(be, new Vec3(x, c.y + sy, z)), tier, t);
        }
        return false;
    }

    public static boolean mayCrossDrone(ForcefieldBlockEntity be, java.util.UUID who) {
        return who != null && who.equals(be.getOwner());
    }

    public static boolean mayCross(ForcefieldBlockEntity be, java.util.UUID who) { return mayCrossDrone(be, who); }

    public static boolean mayWalkThrough(ForcefieldBlockEntity be, net.minecraft.world.entity.player.Player p) {
        if (p.isSpectator() || p.isCreative() || be.getOwner() == null) return true;
        java.util.UUID id = p.getUUID();
        return id.equals(be.getOwner()) || be.security().allows(id);
    }

    static ForcefieldBlockEntity domeOver(ServerLevel sl, Vec3 at) {
        for (ForcefieldBlockEntity be : ForcefieldBlockEntity.LOADED)
            if (be.getLevel() == sl && !be.isRemoved() && be.protecting() && be.wallInside(at)) return be;
        return null;
    }

    static boolean wallBlocksTeleport(net.minecraft.world.entity.player.Player p, Vec3 to) {
        if (!(p.level() instanceof ServerLevel sl) || ForcefieldBlockEntity.LOADED.isEmpty()) return false;
        for (ForcefieldBlockEntity be : ForcefieldBlockEntity.LOADED)
            if (be.getLevel() == sl && !be.isRemoved() && be.protecting() && !mayWalkThrough(be, p) && be.wallInside(p.position()) != be.wallInside(to)) return true;
        return false;
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class PearlGuard {
        private PearlGuard() {}

        @SubscribeEvent
        static void pearl(net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderPearl e) {
            if (wallBlocksTeleport(e.getPlayer(), e.getTarget())) e.setCanceled(true);
        }

        @SubscribeEvent
        static void chorus(net.neoforged.neoforge.event.entity.EntityTeleportEvent.ChorusFruit e) {
            if (e.getEntityLiving() instanceof net.minecraft.world.entity.player.Player p && wallBlocksTeleport(p, e.getTarget())) e.setCanceled(true);
        }
    }

    public static boolean shelters(ServerLevel sl, Vec3 at, Vec3 from) {
        for (ForcefieldBlockEntity be : ForcefieldBlockEntity.LOADED) {
            if (be.getLevel() != sl || be.isRemoved() || !be.protecting()) continue;
            Vec3 c = be.centre();
            double r = be.radius();
            if (at.distanceToSqr(c) < r * r && from.distanceToSqr(c) >= r * r) return true;
        }
        return false;
    }

    static void send(ServerLevel sl, ForcefieldBlockEntity be, int kind, Vec3 dir, float power) {
        Vec3 c = be.centre();
        Fx p = new Fx(be.getBlockPos(), kind, (float) c.x, (float) c.y, (float) c.z, be.radius(), (float) dir.x, (float) dir.y, (float) dir.z, power);
        if (kind == FX_SHATTER) PacketDistributor.sendToPlayersInDimension(sl, p);
        else PacketDistributor.sendToPlayersNear(sl, null, c.x, c.y, c.z, Balance.FF_FX_RANGE + be.radius(), p);
    }

    public record Fx(BlockPos gen, int kind, float cx, float cy, float cz, int radius, float dx, float dy, float dz, float power) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "forcefield_fx"));
        public static final StreamCodec<ByteBuf, Fx> CODEC = StreamCodec.of((b, p) -> {
            BlockPos.STREAM_CODEC.encode(b, p.gen()); ByteBufCodecs.VAR_INT.encode(b, p.kind());
            b.writeFloat(p.cx()); b.writeFloat(p.cy()); b.writeFloat(p.cz()); ByteBufCodecs.VAR_INT.encode(b, p.radius());
            b.writeFloat(p.dx()); b.writeFloat(p.dy()); b.writeFloat(p.dz()); b.writeFloat(p.power());
        }, b -> new Fx(BlockPos.STREAM_CODEC.decode(b), ByteBufCodecs.VAR_INT.decode(b), b.readFloat(), b.readFloat(), b.readFloat(), ByteBufCodecs.VAR_INT.decode(b),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.concurrent.ConcurrentLinkedQueue<Fx> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Fx.TYPE, Fx.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}

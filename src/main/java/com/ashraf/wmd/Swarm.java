package com.ashraf.wmd;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Swarm {

    private Swarm() {}

    public record Launch(int id, double cx, double cy, double cz, int facing, double tx, double ty, double tz, long seed, long t0, int[] cells, UUID owner)
            implements CustomPacketPayload {
        public static final Type<Launch> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "swarm_launch"));
        public static final StreamCodec<ByteBuf, Launch> CODEC = StreamCodec.of((b, p) -> {
            ByteBufCodecs.VAR_INT.encode(b, p.id()); b.writeDouble(p.cx()); b.writeDouble(p.cy()); b.writeDouble(p.cz()); ByteBufCodecs.VAR_INT.encode(b, p.facing());
            b.writeDouble(p.tx()); b.writeDouble(p.ty()); b.writeDouble(p.tz()); b.writeLong(p.seed()); b.writeLong(p.t0());
            ByteBufCodecs.VAR_INT.encode(b, p.cells().length); for (int c : p.cells()) ByteBufCodecs.VAR_INT.encode(b, c);
            b.writeLong(p.owner().getMostSignificantBits()); b.writeLong(p.owner().getLeastSignificantBits());
        }, b -> {
            int id = ByteBufCodecs.VAR_INT.decode(b);
            double cx = b.readDouble(), cy = b.readDouble(), cz = b.readDouble();
            int f = ByteBufCodecs.VAR_INT.decode(b);
            double tx = b.readDouble(), ty = b.readDouble(), tz = b.readDouble();
            long seed = b.readLong(), t0 = b.readLong();
            int n = ByteBufCodecs.VAR_INT.decode(b);
            int[] cells = new int[n];
            for (int i = 0; i < n; i++) cells[i] = ByteBufCodecs.VAR_INT.decode(b);
            return new Launch(id, cx, cy, cz, f, tx, ty, tz, seed, t0, cells, new UUID(b.readLong(), b.readLong()));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Kill(int id, int k, float x, float y, float z, boolean boom) implements CustomPacketPayload {
        public static final Type<Kill> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "swarm_kill"));
        public static final StreamCodec<ByteBuf, Kill> CODEC = StreamCodec.of((b, p) -> {
            ByteBufCodecs.VAR_INT.encode(b, p.id()); ByteBufCodecs.VAR_INT.encode(b, p.k()); b.writeFloat(p.x()); b.writeFloat(p.y()); b.writeFloat(p.z()); b.writeBoolean(p.boom());
        }, b -> new Kill(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static final int HATCH_TICKS = 6;

    static double[] cellLocal(int cell) { int i = cell / 7, j = cell % 7; return new double[] { (-27.0 + 9.0 * i) / 16.0, (-27.0 + 9.0 * j) / 16.0 }; }

    static double[] turn(int facing, double lx, double lz) {
        return switch (facing) { case 5 -> new double[] { -lz, lx }; case 3 -> new double[] { -lx, -lz }; case 4 -> new double[] { lz, -lx }; default -> new double[] { lx, lz }; };
    }

    private static double h(long seed, int k, int salt) {
        long v = seed * 0x9E3779B97F4A7C15L + k * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        v ^= v >>> 31; v *= 0xBF58476D1CE4E5B9L; v ^= v >>> 29;
        return (v & 0xFFFFFF) / (double) 0x1000000;
    }

    static Vec3 spot(Launch s, int k) {
        double a = h(s.seed(), k, 1) * Math.PI * 2.0, r = Balance.SW_SPREAD * Math.sqrt(h(s.seed(), k, 2));
        return new Vec3(s.tx() + Math.cos(a) * r, s.ty(), s.tz() + Math.sin(a) * r);
    }

    static Vec3 start(Launch s, int k) {
        double[] l = cellLocal(s.cells()[k]), w = turn(s.facing(), l[0], l[1]);
        return new Vec3(s.cx() + w[0], s.cy() + 0.4, s.cz() + w[1]);
    }

    static long launchTick(Launch s, int k) { return s.t0() + (long) k * Balance.SW_RIPPLE + HATCH_TICKS; }

    static Vec3 path(Launch s, int k, double t, int[] out) {
        double u = t - launchTick(s, k);
        if (u < 0) return null;
        Vec3 a = start(s, k), sp = spot(s, k);
        double riseH = 14.0;
        if (u <= Balance.SW_RISE) { if (out != null) out[0] = 0; return a.add(0.0, riseH * u / Balance.SW_RISE, 0.0); }
        u -= Balance.SW_RISE;
        Vec3 top = a.add(0.0, riseH, 0.0);
        double H = Math.max(top.y, sp.y + Balance.SW_CRUISE);
        Vec3 over = new Vec3(sp.x, H, sp.z);
        double lc = top.distanceTo(over), tc = Math.max(1.0, lc / Balance.SW_SPEED);
        if (u <= tc) {
            double f = u / tc;
            if (out != null) out[0] = 1;
            return top.lerp(over, f).add(0.0, Math.sin(f * Math.PI) * Math.min(18.0, lc * 0.08), 0.0);
        }
        u -= tc;
        double ld = over.distanceTo(sp), td = Math.max(1.0, ld / (Balance.SW_SPEED * Balance.SW_DIVE));
        if (u <= td) { if (out != null) out[0] = 2; return over.lerp(sp, u / td); }
        if (out != null) out[0] = 3;
        return sp;
    }

    static final class Live {
        final Launch s;
        final ServerLevel level;
        final boolean[] gone;
        final boolean[] dove;
        int alive;

        final java.util.List<long[]> held = new ArrayList<>();
        Live(Launch s, ServerLevel level) { this.s = s; this.level = level; gone = new boolean[s.cells().length]; dove = new boolean[gone.length]; alive = gone.length; }

        final java.util.Set<UUID> told = new java.util.HashSet<>();
    }

    static boolean sees(Live l, ServerPlayer p) {
        if (p.getUUID().equals(l.s.owner())) return true;
        double ax = l.s.cx(), az = l.s.cz(), bx = l.s.tx() - ax, bz = l.s.tz() - az, px = p.getX() - ax, pz = p.getZ() - az;
        double len2 = bx * bx + bz * bz, t = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, (px * bx + pz * bz) / len2));
        double dx = px - t * bx, dz = pz - t * bz;
        return dx * dx + dz * dz <= Balance.SW_VIEW * Balance.SW_VIEW;
    }

    static void tell(Live l) {
        for (ServerPlayer p : l.level.players()) {
            if (l.told.contains(p.getUUID()) || !sees(l, p)) continue;
            l.told.add(p.getUUID());
            Launch s = l.s;
            PacketDistributor.sendToPlayer(p, p.getUUID().equals(s.owner()) ? s
                    : new Launch(s.id(), s.cx(), s.cy(), s.cz(), s.facing(), s.tx(), s.ty(), s.tz(), s.seed(), s.t0(), s.cells(), new UUID(0L, 0L)));
            for (int k = 0; k < l.gone.length; k++) if (l.gone[k]) { Vec3 sp = spot(s, k); PacketDistributor.sendToPlayer(p, new Kill(s.id(), k, (float) sp.x, (float) sp.y, (float) sp.z, true)); }
        }
    }

    private static long ticketKey(Launch s) { return 0x5357_0000_0000_0000L ^ s.id(); }

    private static void hold(Live l, boolean on) {
        if (on) {
            int r = Balance.SW_SPREAD + 8, x0 = ((int) Math.floor(l.s.tx()) - r) >> 4, x1 = ((int) Math.floor(l.s.tx()) + r) >> 4,
                    z0 = ((int) Math.floor(l.s.tz()) - r) >> 4, z1 = ((int) Math.floor(l.s.tz()) + r) >> 4;
            for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) { AsyncChunks.ticket(l.level, ticketKey(l.s), cx, cz, true); l.held.add(new long[] { cx, cz }); }
        } else {
            for (long[] c : l.held) AsyncChunks.ticket(l.level, ticketKey(l.s), (int) c[0], (int) c[1], false);
            l.held.clear();
        }
    }

    static final List<Live> LIVE = new ArrayList<>();
    private static int nextId = 1;

    static Launch launch(ServerLevel sl, Vec3 deck, int facing, Vec3 target, int[] cells, UUID owner) {
        long now = sl.getGameTime();
        Launch s = new Launch(nextId++, deck.x, deck.y, deck.z, facing, target.x, target.y, target.z, sl.random.nextLong(), now, cells, owner);
        Live live = new Live(s, sl);
        hold(live, true);
        LIVE.add(live);
        tell(live);

        try { DroneWarnings.bomb(sl, owner, deck.x, deck.z, target.x, target.z, Balance.SW_RISE + (long) (Math.hypot(target.x - deck.x, target.z - deck.z) / Balance.SW_SPEED) + 40L); }
        catch (Throwable t) { Wmd.LOGGER.warn("[swarm] warning board failed", t); }
        Wmd.LOGGER.info("[swarm] salvo #{} of {} drones from {} {} {} to {} {}", s.id(), cells.length, Math.round(deck.x), Math.round(deck.y), Math.round(deck.z), Math.round(target.x), Math.round(target.z));
        return s;
    }

    private static void kill(Live l, int k, Vec3 p, boolean boom) {
        if (l.gone[k]) return;
        l.gone[k] = true;
        l.alive--;
        Kill kp = new Kill(l.s.id(), k, (float) p.x, (float) p.y, (float) p.z, boom);
        for (ServerPlayer pl : l.level.players()) if (l.told.contains(pl.getUUID())) PacketDistributor.sendToPlayer(pl, kp);
        if (!boom) l.level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0.02);
    }

    public record Target(int id, int k, Vec3 pos) {}

    static Target nearestHostile(ServerLevel sl, UUID owner, java.util.function.Predicate<UUID> friend, Vec3 from, double range, long now) {
        return nearestHostile(sl, owner, friend, from, range, now, k -> false);
    }

    static long key(int id, int k) { return ((long) id << 8) | k; }

    static Target nearestHostile(ServerLevel sl, UUID owner, java.util.function.Predicate<UUID> friend, Vec3 from, double range, long now, java.util.function.LongPredicate skip) {
        Target best = null;
        double bd = range * range;
        for (Live l : LIVE) {
            if (l.level != sl || l.s.owner().equals(owner) || friend.test(l.s.owner())) continue;
            for (int k = 0; k < l.gone.length; k++) {
                if (l.gone[k] || skip.test(key(l.s.id(), k))) continue;
                Vec3 p = path(l.s, k, now, null);
                if (p == null) continue;
                double d = p.distanceToSqr(from);
                if (d < bd) { bd = d; best = new Target(l.s.id(), k, p); }
            }
        }
        return best;
    }

    static Vec3 posOf(ServerLevel sl, int id, int k, long now) {
        for (Live l : LIVE) if (l.level == sl && l.s.id() == id && k >= 0 && k < l.gone.length && !l.gone[k]) return path(l.s, k, now, null);
        return null;
    }

    static boolean shoot(ServerLevel sl, int id, int k, long now) {
        for (Live l : LIVE) {
            if (l.level != sl || l.s.id() != id || k < 0 || k >= l.gone.length || l.gone[k]) continue;
            Vec3 p = path(l.s, k, now, null);
            if (p == null) return false;
            kill(l, k, p, false);
            return true;
        }
        return false;
    }

    private static void resend(net.minecraft.server.level.ServerPlayer p) {
        if (LIVE.isEmpty()) return;
        ServerLevel lv = p.serverLevel();
        for (Live l : LIVE) {
            l.told.remove(p.getUUID());
            if (l.level == lv) tell(l);
        }
    }

    @SubscribeEvent
    static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) resend(p);
    }

    @SubscribeEvent
    static void onDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) resend(p);
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { LIVE.clear(); }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || LIVE.isEmpty()) return;
        long now = sl.getGameTime();
        int[] ph = new int[1];
        for (Iterator<Live> it = LIVE.iterator(); it.hasNext(); ) {
            Live l = it.next();
            if (l.level != sl) continue;
            if (now % 20 == 0) tell(l);
            Launch s = l.s;
            double sx = 0, sy = 0, sz = 0;
            int n = 0;
            for (int k = 0; k < l.gone.length; k++) {
                if (l.gone[k]) continue;
                if (now == launchTick(s, k) - HATCH_TICKS) { Vec3 a = start(s, k); sl.playSound(null, a.x, a.y, a.z, WmdSounds.SW_HATCH.get(), SoundSource.BLOCKS, 1.0F, 0.9F + sl.random.nextFloat() * 0.2F); }
                Vec3 p = path(s, k, now, ph);
                if (p == null) continue;
                if (now == launchTick(s, k)) sl.playSound(null, p.x, p.y, p.z, WmdSounds.SW_LAUNCH.get(), SoundSource.BLOCKS, 1.6F, 0.9F + sl.random.nextFloat() * 0.2F);
                Vec3 was = path(s, k, now - 1, null);
                if (was != null && !ForcefieldBlockEntity.LOADED.isEmpty()) {
                    Forcefield.Crossing c = Forcefield.crossing(sl, was, p);
                    if (c != null && !Forcefield.mayCross(c.be(), s.owner()) && Forcefield.shellHit(sl, c, 1, WarheadType.HE)) { kill(l, k, c.at(), false); continue; }
                }
                if (now % 5 == 0 && sl.isLoaded(BlockPos.containing(p)) && EmpField.jammed(sl, BlockPos.containing(p))) { kill(l, k, p, false); continue; }
                if (ph[0] == 2 && !l.dove[k]) { l.dove[k] = true; sl.playSound(null, p.x, p.y, p.z, WmdSounds.SW_DIVE.get(), SoundSource.BLOCKS, 2.5F, 0.9F + sl.random.nextFloat() * 0.2F); }
                if (ph[0] == 3) {
                    BlockPos bp = BlockPos.containing(p);
                    double gy = sl.isLoaded(bp) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ()) : p.y;
                    Vec3 at = new Vec3(p.x, gy + 0.2, p.z);
                    boolean blocks = Config.Server.blockDamage() && Config.Server.canBreak(sl, BlockPos.containing(at));
                    if (sl.isLoaded(bp)) sl.explode(null, at.x, at.y, at.z, Balance.SW_BLAST, blocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
                    sl.playSound(null, at.x, at.y, at.z, WmdSounds.SW_BLAST.get(), SoundSource.BLOCKS, 3.0F, 0.9F + sl.random.nextFloat() * 0.2F);
                    kill(l, k, at, true);
                    continue;
                }
                sx += p.x; sy += p.y; sz += p.z; n++;
            }
            if (n > 0 && now % 60 == 0) sl.playSound(null, sx / n, sy / n, sz / n, WmdSounds.SW_DRONE_LOOP.get(), SoundSource.BLOCKS, Math.min(8.0F, 2.0F + n * 0.15F), 1.0F);
            if (l.alive <= 0) {
                Wmd.LOGGER.info("[swarm] salvo #{} is over", s.id());
                hold(l, false);
                it.remove();
            }
        }
    }

    public static final java.util.concurrent.ConcurrentLinkedQueue<CustomPacketPayload> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Launch.TYPE, Launch.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
            r.playToClient(Kill.TYPE, Kill.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}

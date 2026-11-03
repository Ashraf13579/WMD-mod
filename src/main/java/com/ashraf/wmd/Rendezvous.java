package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Rendezvous {

    private Rendezvous() {}

    private static final Set<Long> SIGHTED = new HashSet<>(), CLOSE = new HashSet<>(), TOUCHING = new HashSet<>();
    private static final Map<Long, int[]> LOADED = new HashMap<>();

    private static final class SentTags { final Map<Long, Integer> hash = new HashMap<>(); long fullAt = Long.MIN_VALUE; }
    private static final Map<UUID, SentTags> SENT_TAGS = new HashMap<>();
    private static final long FULL_EVERY = 200L;

    static void clearStatics() { SIGHTED.clear(); CLOSE.clear(); TOUCHING.clear(); LOADED.clear(); SENT_TAGS.clear(); Cross.LAST.clear(); }

    private static long pair(int a, int b) { return Math.min(a, b) * 100_000L + Math.max(a, b); }

    private static long now(MinecraftServer server) {
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        return orbit != null ? orbit.getGameTime() : 0L;
    }

    static double[] pos(MinecraftServer server, int slot, long now) {
        long[] r = Station.Data.get(server).reboost.get(slot);
        if (r == null) { Orbit.Ground g = Orbit.ground(server, slot); return new double[] { g.x(), g.z() }; }
        double f = Math.max(0.0, Math.min(1.0, (now - r[4]) / (double) Math.max(1L, r[5] - r[4])));
        return new double[] { r[0] + (r[2] - r[0]) * f, r[1] + (r[3] - r[1]) * f };
    }

    static double[] vel(MinecraftServer server, int slot, long now) {
        long[] r = Station.Data.get(server).reboost.get(slot);
        if (r == null || now >= r[5]) return new double[] { 0.0, 0.0 };
        double t = Math.max(1L, r[5] - r[4]);
        return new double[] { (r[2] - r[0]) / t, (r[3] - r[1]) / t };
    }

    static double dist(MinecraftServer server, int a, int b) {
        if (a < 0 || b < 0 || a == b || !Orbit.ground(server, a).dim().equals(Orbit.ground(server, b).dim())) return Double.MAX_VALUE;
        long now = now(server);
        double[] p = pos(server, a, now), q = pos(server, b, now);
        return Math.hypot(q[0] - p[0], q[1] - p[1]);
    }

    static boolean close(MinecraftServer server, int a, int b) { return dist(server, a, b) <= Balance.RDV_CLOSE; }

    static List<Integer> closeSlots(MinecraftServer server, int slot) {
        List<Integer> out = new ArrayList<>();
        for (int s : live(server, Station.Data.get(server))) if (s != slot && close(server, slot, s)) out.add(s);
        return out;
    }

    static boolean anyClose(MinecraftServer server, int slot) {
        for (int s : live(server, Station.Data.get(server))) if (s != slot && close(server, slot, s)) return true;
        return false;
    }

    static boolean sees(MinecraftServer server, int viewer, int target) {
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(viewer);
        return SensorArray.working(server, viewer) || (o != null && Recon.covered(server, o)) || close(server, viewer, target);
    }

    static Vec3 inFrame(MinecraftServer server, int viewer, int target, Vec3 p) {
        if (dist(server, viewer, target) > Balance.RDV_SIGHT) return p;
        long now = now(server);
        double[] a = pos(server, viewer, now), b = pos(server, target, now);
        BlockPos cv = Orbit.slotCentre(viewer), ct = Orbit.slotCentre(target);
        return new Vec3(p.x - ct.getX() + cv.getX() + (b[0] - a[0]), p.y, p.z - ct.getZ() + cv.getZ() + (b[1] - a[1]));
    }

    static List<Integer> viewersOf(int slot) {
        List<Integer> out = new ArrayList<>();
        for (long k : SIGHTED) {
            int s1 = (int) (k / 100_000L), s2 = (int) (k % 100_000L);
            if (s1 == slot) out.add(s2); else if (s2 == slot) out.add(s1);
        }
        return out;
    }

    static Vec3 fromFrame(MinecraftServer server, int viewer, int target, Vec3 p) {
        long now = now(server);
        double[] a = pos(server, viewer, now), b = pos(server, target, now);
        BlockPos cv = Orbit.slotCentre(viewer), ct = Orbit.slotCentre(target);
        return new Vec3(p.x - cv.getX() + ct.getX() - (b[0] - a[0]), p.y, p.z - cv.getZ() + ct.getZ() - (b[1] - a[1]));
    }

    static double radius(MinecraftServer server, int slot) {
        BlockPos c = Orbit.slotCentre(slot);
        double r = 8.0;
        for (Station.Module m : Station.modules(server, slot)) {
            if (!m.built()) continue;
            BlockPos mid = Station.middle(m);
            double reach = Station.tube(m.type()) ? Station.len(m.type()) / 2.0 + 1.0 : Station.LASER.equals(m.type()) ? 11.0 : Station.boom(m.type()) ? 6.0 : 7.0;
            r = Math.max(r, Math.hypot(mid.getX() - c.getX(), mid.getZ() - c.getZ()) + reach);
        }
        return r;
    }

    static int size(MinecraftServer server, int slot) {
        int s = 0;
        for (Station.Module m : Station.modules(server, slot)) if (m.built()) s += Station.maxHealth(m.type());
        return s;
    }

    private static List<Integer> live(MinecraftServer server, Station.Data d) {
        List<Integer> out = new ArrayList<>();
        for (int s : d.slots.keySet()) { Station.Module c = Station.core(server, s); if (c != null && c.built()) out.add(s); }
        return out;
    }

    static void tick(ServerLevel orbit, Station.Data d) {
        MinecraftServer server = orbit.getServer();
        long now = orbit.getGameTime();
        List<Integer> live = live(server, d);
        Set<Long> livePairs = new HashSet<>();
        for (int i = 0; i < live.size(); i++) for (int j = i + 1; j < live.size(); j++) livePairs.add(pair(live.get(i), live.get(j)));
        for (long k : new ArrayList<>(CLOSE)) if (!livePairs.contains(k)) { CLOSE.remove(k); preload(orbit, k, 0, 0, false); }
        for (long k : new ArrayList<>(LOADED.keySet())) if (!livePairs.contains(k)) preload(orbit, k, 0, 0, false);
        SIGHTED.retainAll(livePairs);
        TOUCHING.retainAll(livePairs);
        Map<Integer, List<Integer>> inSight = new HashMap<>();
        Set<UUID> gotPacket = new HashSet<>();
        for (int i = 0; i < live.size(); i++)
            for (int j = i + 1; j < live.size(); j++) {
                int a = live.get(i), b = live.get(j);
                long k = pair(a, b);
                double dd = dist(server, a, b);
                if (dd == Double.MAX_VALUE) { SIGHTED.remove(k); if (CLOSE.remove(k)) preload(orbit, k, a, b, false); TOUCHING.remove(k); continue; }
                double touch = radius(server, a) + radius(server, b);
                boolean burning = d.reboost.containsKey(a) || d.reboost.containsKey(b);
                if ((burning ? sweep(server, a, b, now) : dd) < touch) {
                    if (TOUCHING.add(k) && burning) collide(orbit, d, a, b, now);
                } else if (dd > touch + 10.0) TOUCHING.remove(k);
                if (dd <= Balance.RDV_SIGHT && dd >= touch - 1.0) {
                    inSight.computeIfAbsent(a, x -> new ArrayList<>()).add(b);
                    inSight.computeIfAbsent(b, x -> new ArrayList<>()).add(a);
                    if (SIGHTED.add(k)) { alert(orbit, a, b, "contact", dd); alert(orbit, b, a, "contact", dd); }
                } else if (dd > Balance.RDV_SIGHT + 50 || dd < touch - 1.0) SIGHTED.remove(k);
                if (dd <= Balance.RDV_CLOSE) { if (CLOSE.add(k)) { alert(orbit, a, b, "close", dd); alert(orbit, b, a, "close", dd); preload(orbit, k, a, b, true); } }
                else if (dd > Balance.RDV_CLOSE + 20 && CLOSE.remove(k)) preload(orbit, k, a, b, false);
            }
        for (Map.Entry<Integer, List<Integer>> e : inSight.entrySet()) {
            int a = e.getKey();
            double[] pa = pos(server, a, now), va = vel(server, a, now);
            List<Ghost> gs = new ArrayList<>();
            for (int b : e.getValue()) {
                double[] pb = pos(server, b, now), vb = vel(server, b, now);
                List<GMod> mods = new ArrayList<>();
                for (Station.Module m : Station.modules(server, b)) if (m.built()) mods.add(new GMod(m.id(), Station.controller(m).asLong(), tag(orbit, m)));
                double dd = Math.hypot(pb[0] - pa[0], pb[1] - pa[1]);
                gs.add(new Ghost(b, Boarding.ownerName(server, b), (float) (pb[0] - pa[0]), (float) (pb[1] - pa[1]), (float) (vb[0] - va[0]), (float) (vb[1] - va[1]),
                        (int) Math.round(dd), dd <= Balance.RDV_CLOSE, mods));
            }
            for (ServerPlayer p : orbit.players()) if (frameOf(p.getX()) == a) { PacketDistributor.sendToPlayer(p, new Ghosts(a, now, trimmed(p.getUUID(), gs, now))); gotPacket.add(p.getUUID()); }
        }
        SENT_TAGS.keySet().retainAll(gotPacket);
    }

    private static List<Ghost> trimmed(UUID player, List<Ghost> gs, long now) {

        if (gs != null) return gs;
        SentTags st = SENT_TAGS.computeIfAbsent(player, u -> new SentTags());
        boolean full = now - st.fullAt >= FULL_EVERY || now < st.fullAt;
        if (full) { st.fullAt = now; st.hash.clear(); }
        List<Ghost> out = new ArrayList<>();
        for (Ghost g : gs) {
            List<GMod> mods = new ArrayList<>();
            for (GMod m : g.mods()) {
                long key = ((long) g.slot() << 32) | (m.id() & 0xFFFFFFFFL);
                int h = m.tag().hashCode();
                Integer old = st.hash.put(key, h);
                mods.add(old != null && old == h ? new GMod(m.id(), m.pos(), null) : m);
            }
            out.add(new Ghost(g.slot(), g.owner(), g.dx(), g.dz(), g.vx(), g.vz(), g.dist(), g.close(), mods));
        }
        return out;
    }

    private static Ghosts merged(Ghosts old, Ghosts neu) {
        for (Ghost g : neu.list()) {
            for (int i = 0; i < g.mods().size(); i++) {
                GMod m = g.mods().get(i);
                if (m.tag() != null) continue;
                CompoundTag prev = null;
                if (old != null && old.frame() == neu.frame()) for (Ghost og : old.list()) if (og.slot() == g.slot()) for (GMod om : og.mods()) if (om.id() == m.id()) prev = om.tag();
                g.mods().set(i, new GMod(m.id(), m.pos(), prev != null ? prev : new CompoundTag()));
            }
        }
        return neu;
    }

    private static void preload(ServerLevel orbit, long k, int a, int b, boolean add) {
        MinecraftServer server = orbit.getServer();
        int[] q = add ? new int[] { a, (int) Math.ceil(radius(server, a) / 16.0) + 1, b, (int) Math.ceil(radius(server, b) / 16.0) + 1 } : LOADED.remove(k);
        if (q == null) return;
        if (add) { int[] old = LOADED.put(k, q); if (old != null) return; }
        for (int i = 0; i < 4; i += 2) {
            BlockPos c = Orbit.slotCentre(q[i]);
            int r = q[i + 1], cx = c.getX() >> 4, cz = c.getZ() >> 4;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) AsyncChunks.ticket(orbit, -1_000_000L - k, cx + x, cz + z, add);
        }
    }

    private static double sweep(MinecraftServer server, int a, int b, long now) {
        double[] a0 = pos(server, a, now - 20), b0 = pos(server, b, now - 20), a1 = pos(server, a, now), b1 = pos(server, b, now);
        double x0 = b0[0] - a0[0], z0 = b0[1] - a0[1], x1 = b1[0] - a1[0], z1 = b1[1] - a1[1], ex = x1 - x0, ez = z1 - z0;
        double t = Math.max(0.0, Math.min(1.0, -(x0 * ex + z0 * ez) / Math.max(1e-9, ex * ex + ez * ez)));
        return Math.hypot(x0 + ex * t, z0 + ez * t);
    }

    static int frameOf(double x) { return (int) Math.round(x / Balance.ORBIT_SLOT_SPACING); }

    private static CompoundTag tag(ServerLevel orbit, Station.Module m) {
        BlockPos c = Station.controller(m);
        if (orbit.isLoaded(c) && orbit.getBlockEntity(c) instanceof StationModuleBlockEntity be) return be.getUpdateTag(orbit.registryAccess());
        CompoundTag t = new CompoundTag();
        t.putString("type", m.type());
        t.putString("facing", m.facing().getSerializedName());
        t.putInt("id", m.id());
        t.putBoolean("wrecked", Station.wrecked(orbit.getServer(), m));
        return t;
    }

    private static void alert(ServerLevel orbit, int to, int other, String what, double dd) {
        boolean close = "close".equals(what);
        StationLife.tell(orbit, to, Component.translatable("message.wmd.rdv." + what, Boarding.ownerName(orbit.getServer(), other), other, (int) Math.round(dd))
                .withStyle(close ? ChatFormatting.RED : ChatFormatting.GOLD));
        Station.Module core = Station.core(orbit.getServer(), to);
        if (core != null) orbit.playSound(null, Station.controller(core), (close ? WmdSounds.RZ_CLOSE : WmdSounds.RZ_CONTACT).get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        UUID owner = Orbit.OrbitSlots.get(orbit.getServer()).ownerOf(to);
        if (owner != null) CcBattleLog.add(orbit.getServer(), owner, CcBattleLog.ALARM, "log.wmd.rdv." + what, Boarding.ownerName(orbit.getServer(), other), Integer.toString((int) Math.round(dd)));
        Wmd.LOGGER.info("[rendezvous] slot {} {}: slot {} at {} blocks", to, what, other, Math.round(dd));
    }

    private static void collide(ServerLevel orbit, Station.Data d, int a, int b, long now) {
        MinecraftServer server = orbit.getServer();
        double ra = radius(server, a), rb = radius(server, b), touch = ra + rb;
        boolean movingA = d.reboost.containsKey(a), movingB = d.reboost.containsKey(b);
        double[] pa = stop(orbit, d, a, now), pb = stop(orbit, d, b, now);
        double dx = pa[0] - pb[0], dz = pa[1] - pb[1], len = Math.hypot(dx, dz);
        if (len < 1e-3) { dx = 1.0; dz = 0.0; len = 1.0; }
        if (len < touch) {
            if (movingA || !movingB) { pa = new double[] { pb[0] + dx / len * touch, pb[1] + dz / len * touch }; place(server, a, pa); }
            else { pb = new double[] { pa[0] - dx / len * touch, pa[1] - dz / len * touch }; place(server, b, pb); }
        }
        int sa = size(server, a), sb = size(server, b);
        if (Math.abs(sa - sb) <= Balance.RDV_EQUAL * Math.max(sa, sb)) {
            for (int s : new int[] { a, b })
                for (Station.Module m : Station.modules(server, s))
                    if (m.built()) StationLife.damage(orbit, s, m, (int) Math.round(Station.maxHealth(m.type()) * Balance.RDV_EQUAL_LOSS));
        } else {
            int small = sa < sb ? a : b, big = small == a ? b : a;
            for (Station.Module m : Station.modules(server, small))
                if (m.built()) StationLife.damage(orbit, small, m, Math.max(1, Station.health(server, m)));
            double ux = (big == a ? -dx : dx) / len, uz = (big == a ? -dz : dz) / len;
            BlockPos c = Orbit.slotCentre(big);
            List<Station.Module> side = new ArrayList<>();
            int weight = 0;
            for (Station.Module m : Station.modules(server, big)) {
                if (!m.built()) continue;
                BlockPos mid = Station.middle(m);
                if ((mid.getX() - c.getX()) * ux + (mid.getZ() - c.getZ()) * uz < -3.0) continue;
                side.add(m);
                weight += Station.maxHealth(m.type());
            }
            int loss = (int) Math.round(Balance.RDV_RAM_SHARE * Math.min(sa, sb));
            for (Station.Module m : side) StationLife.damage(orbit, big, m, Math.max(1, (int) Math.round((double) loss * Station.maxHealth(m.type()) / Math.max(1, weight))));
        }
        for (int s : new int[] { a, b }) {
            int other = s == a ? b : a;
            double ox = (s == a ? -dx : dx) / len, oz = (s == a ? -dz : dz) / len;
            BlockPos c = Orbit.slotCentre(s);
            double r = s == a ? ra : rb;
            for (int k = -2; k <= 2; k++) {
                Vec3 at = new Vec3(c.getX() + 0.5 + ox * r * 0.8 - oz * k * 6.0, c.getY() + 4.0 + k, c.getZ() + 0.5 + oz * r * 0.8 + ox * k * 6.0);
                VacuumBurst.send(orbit, at, 3.0 + Math.abs(k));
            }
            Station.Module core = Station.core(server, s);
            if (core != null) orbit.playSound(null, Station.controller(core), WmdSounds.RZ_COLLISION.get(), SoundSource.BLOCKS, 6.0F, 1.0F);
            StationLife.pa(orbit, s, WmdSounds.PA_BREACH.get());
            StationLife.tell(orbit, s, Component.translatable("message.wmd.rdv.collision", Boarding.ownerName(server, other), other).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            UUID owner = Orbit.OrbitSlots.get(server).ownerOf(s);
            if (owner != null) CcBattleLog.add(server, owner, CcBattleLog.ALARM, "log.wmd.rdv.collision", Boarding.ownerName(server, other));
        }
        Wmd.LOGGER.info("[rendezvous] COLLISION slot {} (size {}) x slot {} (size {})", a, sa, b, sb);
    }

    private static double[] stop(ServerLevel orbit, Station.Data d, int slot, long now) {
        MinecraftServer server = orbit.getServer();
        double[] p = pos(server, slot, now);
        long[] r = d.reboost.remove(slot);
        if (r == null) return p;
        d.setDirty();
        place(server, slot, p);
        StationPayloads.Drift dr = new StationPayloads.Drift((int) r[0], (int) r[1], (int) Math.round(p[0]), (int) Math.round(p[1]), r[4], now);
        for (ServerPlayer o : orbit.players()) if (Orbit.slotAt(o.getX(), o.getZ()) == slot) PacketDistributor.sendToPlayer(o, dr);
        return p;
    }

    private static void place(MinecraftServer server, int slot, double[] p) {
        Orbit.Ground g = Orbit.ground(server, slot);
        Orbit.setGround(server, slot, g.dim(), (int) Math.round(p[0]), (int) Math.round(p[1]));
    }

    static Component approach(ServerPlayer p, int slot, boolean ram) {
        MinecraftServer server = p.server;
        int target = Station.Data.get(server).boardPick.getOrDefault(slot, -1);
        if (target < 0 || Station.core(server, target) == null) return Component.translatable("message.wmd.rdv.no_pick");
        double dd = dist(server, slot, target);
        if (dd == Double.MAX_VALUE) return Component.translatable("message.wmd.rdv.other_planet");
        long now = now(server);
        double[] me = pos(server, slot, now), it = pos(server, target, now);
        double[] go = it;
        if (!ram) {
            double stand = Math.max(Balance.RDV_MATCH, radius(server, slot) + radius(server, target) + Balance.RDV_MATCH_GAP);
            double dx = me[0] - it[0], dz = me[1] - it[1], len = Math.max(1e-3, Math.hypot(dx, dz));
            go = new double[] { it[0] + dx / len * stand, it[1] + dz / len * stand };
        }
        Component why = Reboost.start(p, slot, (int) Math.round(go[0]), (int) Math.round(go[1]));
        if (why == null) Wmd.LOGGER.info("[rendezvous] slot {} {} slot {} ({} blocks)", slot, ram ? "RAMS" : "matches", target, Math.round(dd));
        return why;
    }

    public record GMod(int id, long pos, CompoundTag tag) {}

    public record Ghost(int slot, String owner, float dx, float dz, float vx, float vz, int dist, boolean close, List<GMod> mods) {}

    public record Ghosts(int frame, long at, List<Ghost> list) implements CustomPacketPayload {
        public static final Type<Ghosts> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rdv_ghosts"));
        public static final StreamCodec<FriendlyByteBuf, Ghosts> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.frame()); b.writeLong(p.at());
            b.writeVarInt(p.list().size());
            for (Ghost g : p.list()) {
                b.writeVarInt(g.slot()); b.writeUtf(g.owner()); b.writeFloat(g.dx()); b.writeFloat(g.dz()); b.writeFloat(g.vx()); b.writeFloat(g.vz());
                b.writeVarInt(g.dist()); b.writeBoolean(g.close());
                b.writeVarInt(g.mods().size());
                for (GMod m : g.mods()) { b.writeVarInt(m.id()); b.writeLong(m.pos()); b.writeNbt(m.tag()); }
            }
        }, b -> {
            int frame = b.readVarInt();
            long at = b.readLong();
            List<Ghost> list = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) {
                int slot = b.readVarInt();
                String owner = b.readUtf();
                float dx = b.readFloat(), dz = b.readFloat(), vx = b.readFloat(), vz = b.readFloat();
                int dist = b.readVarInt();
                boolean close = b.readBoolean();
                List<GMod> mods = new ArrayList<>();
                for (int k = b.readVarInt(); k > 0; k--) mods.add(new GMod(b.readVarInt(), b.readLong(), b.readNbt()));
                list.add(new Ghost(slot, owner, dx, dz, vx, vz, dist, close, mods));
            }
            return new Ghosts(frame, at, list);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Cross {
        private Cross() {}

        private static final Map<UUID, Long> LAST = new HashMap<>();

        @SubscribeEvent
        static void onTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
            if (!(event.getLevel() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit) || CLOSE.isEmpty() || orbit.getGameTime() % 5 != 0) return;
            MinecraftServer server = orbit.getServer();
            long now = orbit.getGameTime();
            for (ServerPlayer p : new ArrayList<>(orbit.players())) {
                if (p.isSpectator() || p.isPassenger() || now - LAST.getOrDefault(p.getUUID(), -100L) < 40) continue;
                int a = frameOf(p.getX());
                BlockPos ca = Orbit.slotCentre(a);
                if (Math.hypot(p.getX() - ca.getX(), p.getZ() - ca.getZ()) < radius(server, a) + 4.0) continue;
                for (long k : CLOSE) {
                    int s1 = (int) (k / 100_000L), s2 = (int) (k % 100_000L), b = s1 == a ? s2 : s2 == a ? s1 : -1;
                    if (b < 0) continue;
                    Vec3 q = fromFrame(server, a, b, p.position());
                    BlockPos cb = Orbit.slotCentre(b);
                    if (Math.hypot(q.x - cb.getX(), q.z - cb.getZ()) > radius(server, b) + 4.0) continue;

                    double ox = q.x - cb.getX(), oz = q.z - cb.getZ(), ol = Math.hypot(ox, oz);
                    if (ol < 1.0E-3) { ox = 1.0; oz = 0.0; ol = 1.0; }
                    ox /= ol; oz /= ol;
                    double edge = radius(server, b) + 3.5;
                    Vec3 dst = null;
                    for (int step = 0; step <= 12 && dst == null; step++) {
                        double r = Math.max(ol, edge) + step;
                        Vec3 c = new Vec3(cb.getX() + ox * r, q.y, cb.getZ() + oz * r);
                        if (orbit.noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(c))) dst = c;
                    }
                    if (dst == null) continue;
                    q = dst;
                    LAST.put(p.getUUID(), now);
                    Vec3 v = p.getDeltaMovement();
                    p.teleportTo(orbit, q.x, q.y, q.z, p.getYRot(), p.getXRot());
                    p.setDeltaMovement(v);
                    p.hurtMarked = true;
                    p.displayClientMessage(Component.translatable("message.wmd.rdv.crossed", Boarding.ownerName(server, b)).withStyle(ChatFormatting.AQUA), true);
                    Wmd.LOGGER.info("[rendezvous] {} crossed from slot {} to slot {}", p.getGameProfile().getName(), a, b);
                    break;
                }
            }
        }
    }

    public static volatile Ghosts latest;
    public static volatile long latestAt;

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Ghosts.TYPE, Ghosts.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { latest = merged(latest, p); latestAt = System.currentTimeMillis(); }));
        }
    }
}

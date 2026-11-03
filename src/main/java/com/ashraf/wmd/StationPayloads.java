package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class StationPayloads {

    private StationPayloads() {}

    public record Mod(String type, int port, int air, boolean breach, int state) {}

    public record Seen(String owner, int slot, int modules, int health, int gx, int gz, int dist) {}

    public record Threat(String kind, String from, int secs) {}

    public record LTarget(int kind, long id, String label, int health) {}

    public record Info(int slot, boolean owner, long energy, long max, int make, int use, int orbitT, List<Mod> mods, List<String> crew, int nextPort,
                       int groundX, int groundZ, int rbX, int rbZ, int rbPct, List<int[]> pods, int cells,
                       int sensor, List<Seen> seen, List<Threat> threats,
                       int laser, int laserPct, boolean auto, List<LTarget> targets, int asatPick, String asatLabel,
                       int boardPick, String boardLabel) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.slot()); b.writeBoolean(p.owner()); b.writeLong(p.energy()); b.writeLong(p.max()); b.writeVarInt(p.make()); b.writeVarInt(p.use());
            b.writeVarInt(p.orbitT());
            b.writeVarInt(p.mods().size());
            for (Mod m : p.mods()) { b.writeUtf(m.type()); b.writeVarInt(m.port() + 1); b.writeVarInt(m.air()); b.writeBoolean(m.breach()); b.writeVarInt(m.state()); }
            b.writeVarInt(p.crew().size());
            for (String c : p.crew()) b.writeUtf(c);
            b.writeVarInt(p.nextPort() + 1);
            b.writeInt(p.groundX()); b.writeInt(p.groundZ()); b.writeInt(p.rbX()); b.writeInt(p.rbZ()); b.writeVarInt(p.rbPct() + 1);
            b.writeVarInt(p.pods().size());
            for (int[] q : p.pods()) { b.writeVarInt(q[0]); b.writeVarInt(q[1]); }
            b.writeVarInt(p.cells());
            b.writeVarInt(p.sensor());
            b.writeVarInt(p.seen().size());
            for (Seen s : p.seen()) { b.writeUtf(s.owner()); b.writeVarInt(s.slot()); b.writeVarInt(s.modules()); b.writeVarInt(s.health()); b.writeInt(s.gx()); b.writeInt(s.gz()); b.writeVarInt(s.dist() + 1); }
            b.writeVarInt(p.threats().size());
            for (Threat t : p.threats()) { b.writeUtf(t.kind()); b.writeUtf(t.from()); b.writeVarInt(t.secs()); }
            b.writeVarInt(p.laser() + 2); b.writeVarInt(p.laserPct()); b.writeBoolean(p.auto());
            b.writeVarInt(p.targets().size());
            for (LTarget t : p.targets()) { b.writeVarInt(t.kind()); b.writeLong(t.id()); b.writeUtf(t.label()); b.writeVarInt(t.health()); }
            b.writeVarInt(p.asatPick() + 1); b.writeUtf(p.asatLabel());
            b.writeVarInt(p.boardPick() + 1); b.writeUtf(p.boardLabel());
        }, b -> {
            int slot = b.readVarInt(); boolean owner = b.readBoolean(); long e = b.readLong(), max = b.readLong(); int make = b.readVarInt(), use = b.readVarInt(), ot = b.readVarInt();
            List<Mod> mods = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) mods.add(new Mod(b.readUtf(), b.readVarInt() - 1, b.readVarInt(), b.readBoolean(), b.readVarInt()));
            List<String> crew = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) crew.add(b.readUtf());
            int next = b.readVarInt() - 1, gx = b.readInt(), gz = b.readInt(), rx = b.readInt(), rz = b.readInt(), pct = b.readVarInt() - 1;
            List<int[]> pods = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) pods.add(new int[] { b.readVarInt(), b.readVarInt() });
            int cells = b.readVarInt(), sensor = b.readVarInt();
            List<Seen> seen = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) seen.add(new Seen(b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readInt(), b.readInt(), b.readVarInt() - 1));
            List<Threat> threats = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) threats.add(new Threat(b.readUtf(), b.readUtf(), b.readVarInt()));
            int laser = b.readVarInt() - 2, lpct = b.readVarInt();
            boolean auto = b.readBoolean();
            List<LTarget> targets = new ArrayList<>();
            for (int i = b.readVarInt(); i > 0; i--) targets.add(new LTarget(b.readVarInt(), b.readLong(), b.readUtf(), b.readVarInt()));
            int pick = b.readVarInt() - 1;
            String alab = b.readUtf();
            int bpick = b.readVarInt() - 1;
            return new Info(slot, owner, e, max, make, use, ot, mods, crew, next, gx, gz, rx, rz, pct, pods, cells, sensor, seen, threats,
                    laser, lpct, auto, targets, pick, alab, bpick, b.readUtf());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ask(int slot) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((b, p) -> b.writeVarInt(p.slot() + 1), b -> new Ask(b.readVarInt() - 1));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Pick(int slot, int port) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_pick"));
        public static final StreamCodec<FriendlyByteBuf, Pick> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot() + 1); b.writeVarInt(p.port() + 1); },
                b -> new Pick(b.readVarInt() - 1, b.readVarInt() - 1));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Laser(int slot, int cmd, long arg) implements CustomPacketPayload {
        public static final Type<Laser> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_laser"));
        public static final StreamCodec<FriendlyByteBuf, Laser> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot() + 1); b.writeVarInt(p.cmd()); b.writeLong(p.arg()); },
                b -> new Laser(b.readVarInt() - 1, b.readVarInt(), b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenPod(int slot, int port) implements CustomPacketPayload {
        public static final Type<OpenPod> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_open_pod"));
        public static final StreamCodec<FriendlyByteBuf, OpenPod> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot() + 1); b.writeVarInt(p.port()); },
                b -> new OpenPod(b.readVarInt() - 1, b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Burn(int slot, int x, int z) implements CustomPacketPayload {
        public static final Type<Burn> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_reboost"));
        public static final StreamCodec<FriendlyByteBuf, Burn> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot() + 1); b.writeInt(p.x()); b.writeInt(p.z()); },
                b -> new Burn(b.readVarInt() - 1, b.readInt(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Drift(int fromX, int fromZ, int toX, int toZ, long start, long end) implements CustomPacketPayload {
        public static final Type<Drift> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_drift"));
        public static final StreamCodec<FriendlyByteBuf, Drift> CODEC = StreamCodec.of((b, p) -> {
            b.writeInt(p.fromX()); b.writeInt(p.fromZ()); b.writeInt(p.toX()); b.writeInt(p.toZ()); b.writeLong(p.start()); b.writeLong(p.end());
        }, b -> new Drift(b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readLong(), b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static volatile Drift drift;
    public static volatile long driftAt;

    public static volatile Info latest;
    public static volatile boolean openPending;

    private static UUID ownerOf(MinecraftServer server, int slot) { return Orbit.OrbitSlots.get(server).ownerOf(slot); }

    static final java.util.Map<UUID, Integer> ASK_AT = new java.util.HashMap<>(), CMD_AT = new java.util.HashMap<>();
    static final int COOLDOWN_TICKS = 5;

    static boolean ready(java.util.Map<UUID, Integer> m, ServerPlayer p) {
        int now = p.server.getTickCount();
        Integer last = m.get(p.getUUID());
        if (last != null && now >= last && now - last < COOLDOWN_TICKS) return false;
        m.put(p.getUUID(), now);
        if (m.size() > 512) m.entrySet().removeIf(e -> now < e.getValue() || now - e.getValue() > COOLDOWN_TICKS);
        return true;
    }

    static boolean mayKnow(ServerPlayer p, int slot) {
        if (slot < 0) return true;
        UUID o = ownerOf(p.server, slot);
        if (o == null || o.equals(p.getUUID()) || p.hasPermissions(2)) return true;
        return Orbit.isOrbit(p.level()) && Orbit.slotAt(p.getX(), p.getZ()) == slot;
    }

    static void noAccess(ServerPlayer p, int slot) {
        UUID o = slot < 0 ? null : ownerOf(p.server, slot);
        var cache = p.server.getProfileCache();
        String name = o == null || cache == null ? "?" : cache.get(o).map(com.mojang.authlib.GameProfile::getName).orElse("?");
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.not_yours", name), true);
    }

    static void clearStatics() { ASK_AT.clear(); CMD_AT.clear(); }

    static void sendInfo(ServerPlayer p, int slot) {
        MinecraftServer server = p.server;
        if (slot < 0) slot = Orbit.slotOf(server, p.getUUID());
        if (!mayKnow(p, slot)) { noAccess(p, slot); return; }
        Station.Data d = Station.Data.get(server);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        long now = orbit != null ? orbit.getGameTime() : 0L;
        int make = 0, use = 0;
        List<Mod> mods = new ArrayList<>();
        for (Station.Module m : Station.modules(server, slot)) {
            int state = 0;
            if (!m.built()) state = 1;
            else if (Station.wrecked(server, m)) state = 3;
            else if (Station.WING.equals(m.type()) && orbit != null && orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be
                    && now < be.deploy() + Balance.STATION_DEPLOY_TICKS) state = 2;
            else if (Station.SENSOR.equals(m.type()) && orbit != null && !SensorArray.ready(orbit, m, now)) state = 2;
            boolean breach = StationLife.breached(m.id());
            mods.add(new Mod(m.type(), Station.shownPort(server, m), Math.round(d.air.getOrDefault(m.id(), 1.0F) * 100.0F), breach, state));
            if (state != 0) continue;
            switch (m.type()) {
                case Station.WING -> make += StationLife.sunlit(now) ? Balance.STATION_WING_FE : 0;
                case Station.CORE -> use += Balance.STATION_CORE_FE;
                case Station.HABITAT -> use += Balance.STATION_HAB_FE;
                case Station.SENSOR -> use += Balance.STATION_SENSOR_FE;
                case Station.LASER -> use += Balance.STATION_LASER_FE;
                case Station.TURRET -> use += Balance.STATION_TURRET_FE;
                case Station.ROD -> use += Balance.STATION_ROD_FE;
                case Station.MIRROR -> use += Balance.STATION_MIRROR_FE + (MirrorFocus.burning(slot) ? Balance.MIRROR_BURN_FE : 0);
                case Station.BAY -> use += Balance.STATION_BAY_FE + (AsteroidBay.drawing(d, m, d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2)) ? Balance.BAY_BUILD_FE : 0);
                default -> {}
            }
        }
        List<String> crew = new ArrayList<>();
        if (orbit != null) for (ServerPlayer o : orbit.players())
            if (Orbit.slotAt(o.getX(), o.getZ()) == slot && (StationLife.inside(o) != null || o.getVehicle() instanceof CrewSeatEntity)) crew.add(o.getGameProfile().getName());
        Orbit.Ground g = Orbit.ground(server, slot);
        long[] rb = Reboost.state(server, slot, now);
        Info info = new Info(slot, p.getUUID().equals(ownerOf(server, slot)), d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2), Station.capacity(server, slot), make, use,
                OrbitClock.phase(now), mods, crew, d.nextPort.getOrDefault(slot, -1),
                g.x(), g.z(), rb == null ? 0 : (int) rb[0], rb == null ? 0 : (int) rb[1], rb == null ? -1 : (int) rb[2],
                Cargo.docked(server, slot), Cargo.playerCells(p) + Cargo.podCells(server, slot),
                SensorArray.state(server, slot), seen(server, slot), threats(slot, now),
                StationLaser.status(server, slot, now)[0], StationLaser.status(server, slot, now)[1], StationLaser.auto(server, slot), laserTargets(server, slot),
                d.asatPick.getOrDefault(slot, -1), asatLabel(server, d.asatPick.getOrDefault(slot, -1)),
                d.boardPick.getOrDefault(slot, -1), d.boardPick.containsKey(slot) ? Boarding.ownerName(server, d.boardPick.get(slot)) : "");
        PacketDistributor.sendToPlayer(p, info);
    }

    private static List<Seen> seen(MinecraftServer server, int slot) {
        List<Seen> out = new ArrayList<>();
        for (SensorArray.Contact c : SensorArray.contacts(server, slot)) out.add(new Seen(c.owner(), c.slot(), c.modules(), c.health(), c.gx(), c.gz(), c.dist()));
        return out;
    }

    private static List<LTarget> laserTargets(MinecraftServer server, int slot) {
        List<LTarget> out = new ArrayList<>();
        for (StationLaser.Target t : StationLaser.targets(server, slot)) { if (out.size() >= 12) break; out.add(new LTarget(t.kind(), t.id(), t.label(), t.health())); }
        return out;
    }

    private static String asatLabel(MinecraftServer server, int id) {
        Station.Module t = id < 0 ? null : Station.byId(server, id);
        return t == null ? "" : StationLaser.name(server, t) + ": " + t.type();
    }

    private static List<Threat> threats(int slot, long now) {
        List<Threat> out = new ArrayList<>();
        for (SensorArray.Threat t : SensorArray.threats(slot, now)) out.add(new Threat(t.kind(), t.from(), (int) Math.max(0L, (t.end() - now) / 20)));
        return out;
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Info.TYPE, Info.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { latest = p; openPending = true; }));
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp && ready(ASK_AT, sp)) sendInfo(sp, p.slot()); }));
            r.playToServer(Pick.TYPE, Pick.CODEC, StationPayloads::pick);
            r.playToServer(OpenPod.TYPE, OpenPod.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) Cargo.openFromConsole(sp, p.slot(), p.port()); }));
            r.playToServer(Burn.TYPE, Burn.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp) || !ready(CMD_AT, sp)) return;
                net.minecraft.network.chat.Component why = Reboost.start(sp, p.slot(), p.x(), p.z());
                if (why != null) sp.displayClientMessage(why.copy().withStyle(net.minecraft.ChatFormatting.RED), true);
                sendInfo(sp, p.slot());
            }));
            r.playToClient(Drift.TYPE, Drift.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { drift = p; driftAt = System.currentTimeMillis(); }));
            r.playToServer(Laser.TYPE, Laser.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp) || !ready(CMD_AT, sp)) return;
                net.minecraft.network.chat.Component why = StationLaser.command(sp, p.slot(), p.cmd(), p.arg());
                if (why != null) sp.displayClientMessage(why, true);
                sendInfo(sp, p.slot());
            }));
        }
    }

    private static void pick(Pick p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || p.port() < -1 || p.port() > 3) return;
            if (!sp.getUUID().equals(ownerOf(sp.server, p.slot()))) return;
            Station.Data d = Station.Data.get(sp.server);
            if (p.port() < 0) d.nextPort.remove(p.slot()); else d.nextPort.put(p.slot(), p.port());
            d.setDirty();
            sendInfo(sp, p.slot());
        });
    }
}

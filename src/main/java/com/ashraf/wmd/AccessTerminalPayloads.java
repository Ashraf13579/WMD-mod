package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AccessTerminalPayloads {

    private AccessTerminalPayloads() {}

    static final int MAX_ROWS = 400, COOLDOWN = 3;
    static final double REACH = 8.0;

    static final int REFRESH = 0, ADD = 1, REMOVE = 2, LEVEL = 3, SCOPE = 4, TOGGLE = 5, ALL_MACHINES = 6, PUBLIC = 7;

    public record PRow(String id, String name, int level, boolean all, List<Integer> chosen) {}

    public record MRow(String key, String block, int x, int y, int z, String dim, boolean pub, boolean craft) {}

    public record State(BlockPos pos, String owner, List<PRow> players, List<MRow> machines, int total) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "access_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBlockPos(s.pos()); buf.writeUtf(s.owner(), 32);
            buf.writeVarInt(s.players().size());
            for (PRow r : s.players()) {
                buf.writeUtf(r.id(), 40); buf.writeUtf(r.name(), 32); buf.writeVarInt(r.level()); buf.writeBoolean(r.all());
                buf.writeVarInt(r.chosen().size());
                for (int i : r.chosen()) buf.writeVarInt(i);
            }
            buf.writeVarInt(s.machines().size());
            for (MRow m : s.machines()) {
                buf.writeUtf(m.key(), 128); buf.writeUtf(m.block(), 96); buf.writeVarInt(m.x()); buf.writeVarInt(m.y()); buf.writeVarInt(m.z());
                buf.writeUtf(m.dim(), 64); buf.writeBoolean(m.pub()); buf.writeBoolean(m.craft());
            }
            buf.writeVarInt(s.total());
        }, buf -> {
            BlockPos pos = buf.readBlockPos(); String owner = buf.readUtf(32);
            int np = Math.min(buf.readVarInt(), AccessStore.MAX_GRANTS);
            List<PRow> ps = new ArrayList<>();
            for (int i = 0; i < np; i++) {
                String id = buf.readUtf(40), name = buf.readUtf(32); int lv = buf.readVarInt(); boolean all = buf.readBoolean();
                int nc = Math.min(buf.readVarInt(), MAX_ROWS);
                List<Integer> ch = new ArrayList<>();
                for (int k = 0; k < nc; k++) ch.add(buf.readVarInt());
                ps.add(new PRow(id, name, lv, all, ch));
            }
            int nm = Math.min(buf.readVarInt(), MAX_ROWS);
            List<MRow> ms = new ArrayList<>();
            for (int i = 0; i < nm; i++)
                ms.add(new MRow(buf.readUtf(128), buf.readUtf(96), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean()));
            return new State(pos, owner, ps, ms, buf.readVarInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Act(BlockPos pos, int action, String a, String b, int v) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "access_act"));
        public static final StreamCodec<FriendlyByteBuf, Act> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Act::pos, ByteBufCodecs.VAR_INT, Act::action, ByteBufCodecs.stringUtf8(64), Act::a,
                ByteBufCodecs.stringUtf8(128), Act::b, ByteBufCodecs.VAR_INT, Act::v, Act::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<UUID, Integer> LAST = Statics.server(new HashMap<>());

    private static AccessTerminalBlockEntity terminal(ServerPlayer sp, BlockPos pos) {
        if (!Reach.usable(sp, pos, REACH) || !(sp.level().getBlockEntity(pos) instanceof AccessTerminalBlockEntity t) || t.getOwner() == null) return null;
        return sp.getUUID().equals(t.getOwner()) || sp.hasPermissions(2) ? t : null;
    }

    public static void handle(Act p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            AccessTerminalBlockEntity t = terminal(sp, p.pos());
            if (t == null) return;
            if (p.action() != REFRESH) {
                int now = sp.server.getTickCount();
                Integer last = LAST.get(sp.getUUID());
                if (last != null && now - last >= 0 && now - last < COOLDOWN) { sendState(sp, p.pos()); return; }
                LAST.put(sp.getUUID(), now);
                if (LAST.size() > 256) LAST.clear();
            }
            AccessStore st = AccessStore.get(sp.server);
            UUID owner = t.getOwner();
            Map<UUID, AccessStore.Grant> grants = st.grantsOf(owner);
            UUID who = parse(p.a());
            AccessStore.Grant g = who == null ? null : grants.get(who);
            switch (p.action()) {
                case ADD -> { lookUp(sp, p.pos(), p.a().trim()); return; }
                case REMOVE -> { if (who != null) grants.remove(who); }
                case LEVEL -> { if (g != null) g.level = Math.max(AccessStore.FRIEND, Math.min(AccessStore.CONFIGURE, p.v())); }
                case SCOPE -> { if (g != null) g.all = p.v() != 0; }
                case TOGGLE -> { if (g != null && st.machinesOf(owner).containsKey(p.b()) && !g.machines.remove(p.b())) g.machines.add(p.b()); }
                case ALL_MACHINES -> { if (g != null) { g.machines.clear(); if (p.v() != 0) g.machines.addAll(st.machinesOf(owner).keySet()); } }
                case PUBLIC -> {
                    if (st.machinesOf(owner).containsKey(p.b())) { var pubs = st.publicsOf(owner); if (!pubs.remove(p.b())) pubs.add(p.b()); }
                }
                default -> { }
            }
            if (p.action() != REFRESH) {
                st.setDirty();
                Wmd.LOGGER.info("[access] {} edited the access list of {}: action {} {} {}", sp.getGameProfile().getName(), t.getOwnerName(), p.action(), p.a(), p.v());
            }
            sendState(sp, p.pos());
        });
    }

    private static UUID parse(String s) {
        try { return s == null || s.isEmpty() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static void lookUp(ServerPlayer sp, BlockPos pos, String name) {
        if (name.isEmpty() || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            sp.displayClientMessage(Component.translatable("message.wmd.security.unknown_player", name), true);
            sendState(sp, pos);
            return;
        }
        ServerPlayer online = sp.server.getPlayerList().getPlayerByName(name);
        if (online != null) { add(sp, pos, online.getUUID(), online.getGameProfile().getName()); return; }
        var cache = sp.server.getProfileCache();
        java.util.concurrent.CompletableFuture<Optional<GameProfile>> f;
        try { f = cache == null ? null : cache.getAsync(name); } catch (IllegalStateException e) { f = null; }
        if (f == null) { sp.displayClientMessage(Component.translatable("message.wmd.security.unknown_player", name), true); sendState(sp, pos); return; }
        UUID asker = sp.getUUID();
        MinecraftServer server = sp.server;
        f.whenComplete((prof, err) -> server.execute(() -> {
            ServerPlayer back = server.getPlayerList().getPlayer(asker);
            if (back == null) return;
            if (err != null || prof == null || prof.isEmpty()) {
                back.displayClientMessage(Component.translatable("message.wmd.security.unknown_player", name), true);
                sendState(back, pos);
                return;
            }
            add(back, pos, prof.get().getId(), prof.get().getName());
        }));
    }

    private static void add(ServerPlayer sp, BlockPos pos, UUID id, String name) {
        AccessTerminalBlockEntity t = terminal(sp, pos);
        if (t == null) return;
        AccessStore st = AccessStore.get(sp.server);
        Map<UUID, AccessStore.Grant> grants = st.grantsOf(t.getOwner());
        if (id.equals(t.getOwner())) sp.displayClientMessage(Component.translatable("message.wmd.access.self"), true);
        else if (!grants.containsKey(id) && grants.size() >= AccessStore.MAX_GRANTS) sp.displayClientMessage(Component.translatable("message.wmd.access.full", AccessStore.MAX_GRANTS), true);
        else if (!grants.containsKey(id)) {
            grants.put(id, new AccessStore.Grant(name, AccessStore.USE));
            st.setDirty();
            Wmd.LOGGER.info("[access] {} added {} to the access list of {}", sp.getGameProfile().getName(), name, t.getOwnerName());
        }
        sendState(sp, pos);
    }

    static void sendState(ServerPlayer sp, BlockPos pos) {
        AccessTerminalBlockEntity t = terminal(sp, pos);
        if (t == null) return;
        AccessStore st = AccessStore.get(sp.server);
        UUID owner = t.getOwner();
        for (String k : new ArrayList<>(st.machinesOf(owner).keySet())) st.check(sp.server, owner, k);
        List<Map.Entry<String, AccessStore.Machine>> ms = new ArrayList<>(st.machinesOf(owner).entrySet());
        ms.sort(Comparator.comparing((Map.Entry<String, AccessStore.Machine> e) -> e.getValue().block()).thenComparing(e -> e.getValue().dim()).thenComparingLong(e -> e.getValue().pos()));
        int total = ms.size();
        if (ms.size() > MAX_ROWS) ms = ms.subList(0, MAX_ROWS);
        Map<String, Integer> idx = new HashMap<>();
        List<MRow> rows = new ArrayList<>();
        var pubs = st.publicsOf(owner);
        for (Map.Entry<String, AccessStore.Machine> e : ms) {
            AccessStore.Machine m = e.getValue();
            BlockPos p = BlockPos.of(m.pos());
            ServerLevel lv = AccessStore.levelOf(sp.server, m.dim());
            boolean craft = lv != null && lv.isLoaded(p) && SableBridge.shipIdAt(lv, p) != null;
            String dim = m.dim().contains(":") ? m.dim().substring(m.dim().indexOf(':') + 1) : m.dim();
            idx.put(e.getKey(), rows.size());
            rows.add(new MRow(e.getKey(), m.block(), p.getX(), p.getY(), p.getZ(), dim, pubs.contains(e.getKey()), craft));
        }
        List<PRow> players = new ArrayList<>();
        for (Map.Entry<UUID, AccessStore.Grant> e : st.grantsOf(owner).entrySet()) {
            List<Integer> ch = new ArrayList<>();
            for (String k : e.getValue().machines) { Integer i = idx.get(k); if (i != null) ch.add(i); }
            players.add(new PRow(e.getKey().toString(), e.getValue().name, e.getValue().level, e.getValue().all, ch));
        }
        t.setPlayers(players.size());
        PacketDistributor.sendToPlayer(sp, new State(pos, t.getOwnerName(), players, rows, total));
    }
}

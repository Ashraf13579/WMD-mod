package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

public final class WarRoomPayloads {

    private WarRoomPayloads() {}

    static final int A_REFRESH = 0, A_DECLARE = 1, A_PEACE = 2, A_SURRENDER = 3, A_JOIN = 4, A_TAKE = 5, A_TREATY_SIGN = 6, A_TREATY_WITHDRAW = 7;
    static final int PRESS_GAP = 10, REFRESH_GAP = 20;
    private static final Map<UUID, int[]> LAST = Statics.server(new HashMap<>());

    private static ResourceLocation id(String n) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, n); }

    public record Ask(int action, UUID target, int x, int z, boolean open) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(id("war_room_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of(
                (b, p) -> { b.writeByte(p.action()); b.writeBoolean(p.target() != null); if (p.target() != null) b.writeUUID(p.target()); b.writeInt(p.x()); b.writeInt(p.z()); b.writeBoolean(p.open()); },
                b -> { int a = b.readByte(); UUID t = b.readBoolean() ? b.readUUID() : null; return new Ask(a, t, b.readInt(), b.readInt(), b.readBoolean()); });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Data(boolean open, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Data> TYPE = new Type<>(id("war_room_data"));
        public static final StreamCodec<FriendlyByteBuf, Data> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBoolean(p.open()); b.writeNbt(p.data()); },
                b -> { boolean o = b.readBoolean(); CompoundTag t = b.readNbt(); return new Data(o, t == null ? new CompoundTag() : t); });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void handle(ServerPlayer sp, Ask p) {
        if (sp == null || p.action() < A_REFRESH || p.action() > A_TREATY_WITHDRAW) return;
        boolean refresh = p.action() == A_REFRESH;
        if (AirframeRecords.tooSoon(LAST, sp, refresh ? 0 : 1, refresh ? REFRESH_GAP : PRESS_GAP)) return;
        String ans = switch (p.action()) {
            case A_DECLARE -> WarManager.declare(sp, p.target());
            case A_PEACE -> p.target() == null ? "no_target" : WarManager.peace(sp, p.target());
            case A_SURRENDER -> p.target() == null ? "no_target" : WarManager.surrender(sp, p.target());
            case A_JOIN -> p.target() == null ? "no_target" : WarManager.join(sp, p.target());
            case A_TAKE -> { String a = WarManager.take(sp, p.x(), p.z()); yield a.isEmpty() ? "ok_taken" : a; }
            case A_TREATY_SIGN -> WarTreaty.sign(sp);
            case A_TREATY_WITHDRAW -> WarTreaty.withdraw(sp);
            default -> "";
        };
        send(sp, p.open(), ans);
    }

    static void open(ServerPlayer sp) { send(sp, true, ""); }

    static void send(ServerPlayer sp, boolean open, String msg) {
        PacketDistributor.sendToPlayer(sp, new Data(open, build(sp, msg)));
    }

    private static CompoundTag build(ServerPlayer sp, String msg) {
        MinecraftServer server = sp.server;
        CompoundTag d = new CompoundTag();
        d.putString("msg", msg == null ? "" : msg);
        if (VnBridge.present()) return vn(sp, d);
        d.putBoolean("opac", OpacBridge.active());
        if (!OpacBridge.active()) return d;
        UUID me = OpacBridge.nationOf(server, sp.getUUID());
        long t = WarManager.now();
        if (me != null) {
            d.putUUID("me", me);
            d.putString("meName", WarManager.name(server, me));
            d.putBoolean("lead", OpacBridge.leads(server, sp.getUUID()));
            d.putBoolean("cc", WarManager.hasCommandCenter(server, me));
        }
        WarData wd = WarData.get(server);
        ListTag nations = new ListTag();
        for (UUID n : OpacBridge.nations(server)) {
            CompoundTag r = new CompoundTag();
            r.putUUID("id", n);
            r.putString("n", WarManager.name(server, n));
            r.putInt("on", OpacBridge.online(server, n));
            r.putInt("m", OpacBridge.members(server, n).size());
            if (me != null && !me.equals(n)) {
                WarData.War w = WarManager.between(server, me, n);
                r.putInt("war", w == null ? 0 : w.state == WarData.DECLARED ? 1 : 2);
                if (w != null && w.state == WarData.ACTIVE) r.putBoolean("open", WarGate.isOpen(server, n));
                Long cool = wd.cooldowns.get(WarData.pair(me, n));
                if (cool != null && cool > t) r.putInt("cool", (int) ((cool - t + 59_999L) / 60_000L));
                r.putBoolean("ally", OpacBridge.mutualAllies(server, me, n));
            }
            if (me != null && me.equals(n)) r.putBoolean("self", true);
            nations.add(r);
        }
        d.put("nations", nations);
        ListTag wars = new ListTag(), calls = new ListTag();
        for (WarData.War w : wd.wars) {
            if (!w.live() || me == null) continue;
            int s = w.sideOf(me);
            if (s == 0) {
                boolean a = w.callA.contains(me), b = w.callB.contains(me);
                if (!a && !b) continue;
                CompoundTag c = new CompoundTag();
                c.putInt("id", w.id);
                UUID ally = a ? w.attacker : w.defender;
                c.putUUID("ally", ally);
                c.putString("allyN", WarManager.name(server, ally));
                c.putString("enemyN", WarManager.name(server, a ? w.defender : w.attacker));
                calls.add(c);
                continue;
            }
            CompoundTag r = new CompoundTag();
            r.putInt("id", w.id);
            r.putInt("st", w.state);
            r.putLong("left", Math.max(0L, (w.state == WarData.DECLARED ? w.startsAt : w.endsAt) - t));
            r.putString("a", names(server, w.sideA));
            r.putString("b", names(server, w.sideB));
            r.putInt("side", s);
            r.putInt("pa", w.points[1]);
            r.putInt("pb", w.points[2]);
            r.putInt("peace", w.peaceBy == 0 || w.peaceUntil <= t ? 0 : w.peaceBy == s ? 1 : 2);
            r.putBoolean("principal", me.equals(w.attacker) || me.equals(w.defender));
            r.putUUID("enemy", w.principal(3 - s));
            wars.add(r);
        }
        d.put("wars", wars);
        d.put("calls", calls);
        List<Map.Entry<UUID, int[]>> rows = new ArrayList<>(wd.totals.entrySet());
        rows.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        ListTag board = new ListTag();
        for (int i = 0; i < rows.size() && i < 12; i++) {
            CompoundTag r = new CompoundTag();
            int[] v = rows.get(i).getValue();
            r.putString("n", WarManager.name(server, rows.get(i).getKey()));
            r.putIntArray("v", v);
            if (WarTreaty.breaker(server, rows.get(i).getKey())) r.putBoolean("breaker", true);
            board.add(r);
        }
        d.put("board", board);
        if (me != null) {
            long[] tv = wd.treaty.get(me);
            d.putInt("treaty", tv == null ? 0 : tv[1] == 0 ? 1 : 2);
            if (tv != null && tv[1] != 0) d.putLong("treatyLeft", Math.max(0L, tv[1] - t));
        }
        d.putInt("signers", (int) wd.treaty.values().stream().filter(v -> v[1] == 0).count());
        WarData.War won = WarManager.takeWar(server, me);
        if (won != null) {
            UUID loser = won.attacker.equals(me) ? won.defender : won.attacker;
            ServerLevel level = sp.serverLevel();
            int R = WarManager.TAKE_REACH, pcx = sp.blockPosition().getX() >> 4, pcz = sp.blockPosition().getZ() >> 4, n = 2 * R + 1;
            byte[] grid = new byte[n * n];
            for (int dz = -R; dz <= R; dz++)
                for (int dx = -R; dx <= R; dx++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    UUID owner = OpacBridge.nationAt(level, new BlockPos(cx << 4, 64, cz << 4));
                    byte v = 0;
                    if (loser.equals(owner)) v = WarManager.takeBlocked(level, loser, cx, cz) == null ? (byte) 1 : (byte) 2;
                    else if (me.equals(owner)) v = 3;
                    grid[(dz + R) * n + (dx + R)] = v;
                }
            CompoundTag tk = new CompoundTag();
            tk.putInt("war", won.id);
            tk.putInt("left", won.takeLeft);
            tk.putLong("until", won.takeUntil - t);
            tk.putString("loser", WarManager.name(server, loser));
            tk.putInt("cx", pcx);
            tk.putInt("cz", pcz);
            tk.putByteArray("grid", grid);
            d.put("take", tk);
        }
        return d;
    }

    private static CompoundTag vn(ServerPlayer sp, CompoundTag d) {
        d.putBoolean("vn", true);
        d.putBoolean("opac", true);
        String me = VnBridge.countryOf(sp.getUUID());
        if (me != null) d.putString("meName", VnBridge.plainName(me));
        ListTag wars = new ListTag(), nations = new ListTag();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        if (me != null) seen.add(me);
        int i = 0;
        for (String[] w : VnBridge.wars()) {
            CompoundTag r = new CompoundTag();
            r.putInt("id", ++i);
            r.putInt("st", WarData.ACTIVE);
            r.putLong("left", 0L);
            String att = w[2] == null ? w[0] : w[2], def = att.equals(w[0]) ? w[1] : w[0];
            r.putString("a", VnBridge.plainName(att));
            r.putString("b", VnBridge.plainName(def));
            r.putInt("side", me == null ? 0 : me.equals(att) ? 1 : me.equals(def) ? 2 : 0);
            wars.add(r);
            seen.add(w[0]);
            seen.add(w[1]);
        }
        for (String c : seen) {
            CompoundTag r = new CompoundTag();
            r.putUUID("id", UUID.nameUUIDFromBytes(c.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            r.putString("n", VnBridge.plainName(c));
            if (c.equals(me)) r.putBoolean("self", true);
            else {
                r.putInt("war", VnBridge.atWar(me, c) ? 2 : 0);
                r.putBoolean("ally", VnBridge.allies(me, c));
            }
            nations.add(r);
        }
        d.put("wars", wars);
        d.put("nations", nations);
        d.put("calls", new ListTag());
        d.put("board", new ListTag());
        return d;
    }

    private static String names(MinecraftServer server, java.util.Set<UUID> side) {
        StringBuilder b = new StringBuilder();
        for (UUID n : side) b.append(b.length() == 0 ? "" : " + ").append(WarManager.name(server, n));
        return b.toString();
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handle(sp, p); }));
            r.playToClient(Data.TYPE, Data.CODEC, (p, ctx) -> ctx.enqueueWork(() -> WarRoomScreen.onData(p)));
        }
    }
}

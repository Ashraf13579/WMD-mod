package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AsteroidBayPayloads {

    private AsteroidBayPayloads() {}

    public record Info(int slot, boolean owner, int state, int am, int dt, int cores, int pm, int secs, boolean paused, long energy,
                       int phaseSecs, boolean launched, int rock, int pass, int passSize, int passSecs, boolean sensor,
                       boolean aim, int tx, int tz, int groundX, int groundZ, int pieces) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "abay_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.slot() + 1); b.writeBoolean(p.owner()); b.writeVarInt(p.state() + 2); b.writeVarInt(p.am()); b.writeVarInt(p.dt()); b.writeVarInt(p.cores());
            b.writeVarInt(p.pm()); b.writeVarInt(p.secs()); b.writeBoolean(p.paused()); b.writeVarLong(p.energy());
            b.writeVarInt(p.phaseSecs()); b.writeBoolean(p.launched()); b.writeVarInt(p.rock()); b.writeVarInt(p.pass()); b.writeVarInt(p.passSize()); b.writeVarInt(p.passSecs());
            b.writeBoolean(p.sensor()); b.writeBoolean(p.aim()); b.writeInt(p.tx()); b.writeInt(p.tz()); b.writeInt(p.groundX()); b.writeInt(p.groundZ()); b.writeVarInt(p.pieces());
        }, b -> new Info(b.readVarInt() - 1, b.readBoolean(), b.readVarInt() - 2, b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readVarLong(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(),
                b.readBoolean(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ask(int slot) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "abay_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((b, p) -> b.writeVarInt(p.slot() + 1), b -> new Ask(b.readVarInt() - 1));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Cmd(int slot, int cmd, int x, int z) implements CustomPacketPayload {
        public static final Type<Cmd> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "abay_cmd"));
        public static final StreamCodec<FriendlyByteBuf, Cmd> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot() + 1); b.writeVarInt(p.cmd()); b.writeInt(p.x()); b.writeInt(p.z()); },
                b -> new Cmd(b.readVarInt() - 1, b.readVarInt(), b.readInt(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Tug(int slot, int state, long t0, int size, long seed, int hits, int ox, int oy, int oz, int facing) implements CustomPacketPayload {
        public static final Type<Tug> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "abay_tug"));
        public static final StreamCodec<FriendlyByteBuf, Tug> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.slot()); b.writeVarInt(p.state()); b.writeVarLong(p.t0()); b.writeVarInt(p.size()); b.writeLong(p.seed()); b.writeVarInt(p.hits());
            b.writeInt(p.ox()); b.writeInt(p.oy()); b.writeInt(p.oz()); b.writeVarInt(p.facing());
        }, b -> new Tug(b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarInt(), b.readLong(), b.readVarInt(), b.readInt(), b.readInt(), b.readInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Fall(long key, int stage, int tx, int ty, int tz, long impact, int size, int pieces, long split, long seed, boolean sea, String owner) implements CustomPacketPayload {
        public static final Type<Fall> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "abay_fall"));
        public static final StreamCodec<FriendlyByteBuf, Fall> CODEC = StreamCodec.of((b, p) -> {
            b.writeLong(p.key()); b.writeVarInt(p.stage()); b.writeInt(p.tx()); b.writeInt(p.ty()); b.writeInt(p.tz()); b.writeVarLong(p.impact()); b.writeVarInt(p.size());
            b.writeVarInt(p.pieces()); b.writeVarLong(p.split()); b.writeLong(p.seed()); b.writeBoolean(p.sea()); b.writeUtf(p.owner(), 64);
        }, b -> new Fall(b.readLong(), b.readVarInt(), b.readInt(), b.readInt(), b.readInt(), b.readVarLong(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readLong(),
                b.readBoolean(), b.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.Map<Long, Object[]> FALLS = Statics.client(new java.util.concurrent.ConcurrentHashMap<>());

    public static final java.util.Map<Integer, Tug> TUGS = Statics.client(new java.util.concurrent.ConcurrentHashMap<>());

    public static volatile Info latest;
    public static volatile boolean openPending;

    static void sendInfo(ServerPlayer p, int slot) {
        MinecraftServer server = p.server;
        if (slot < 0) slot = Orbit.slotOf(server, p.getUUID());
        if (!StationPayloads.mayKnow(p, slot)) { StationPayloads.noAccess(p, slot); return; }
        if (slot < 0 || AsteroidBay.module(server, slot) == null && !p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) {
            p.displayClientMessage(Component.translatable("message.wmd.abay.none").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        int[] st = AsteroidBay.status(server, slot);
        boolean owner = p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot));
        net.minecraft.server.level.ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        int[] ps = AsteroidRedirect.passStatus(server, orbit == null ? 0L : orbit.getGameTime());
        Orbit.Ground g = Orbit.ground(server, slot);
        PacketDistributor.sendToPlayer(p, new Info(slot, owner, st[0], st[1], st[2], st[3], st[4], st[5], st[6] == 1,
                Station.Data.get(server).energy.getOrDefault(slot, Balance.STATION_BUFFER / 2), st[7], st[8] == 1, st[9], ps[0], ps[1], ps[2], SensorArray.working(server, slot),
                st[10] == 1, st[11], st[12], g.x(), g.z(), st[13]));
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Info.TYPE, Info.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { latest = p; openPending = true; }));
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp && StationPayloads.ready(StationPayloads.ASK_AT, sp)) sendInfo(sp, p.slot()); }));
            r.playToClient(Fall.TYPE, Fall.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (p.stage() == 4) FALLS.remove(p.key()); else FALLS.put(p.key(), new Object[] { p, -1L }); }));
            r.playToClient(Tug.TYPE, Tug.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (p.state() < AsteroidBay.LAUNCH) TUGS.remove(p.slot()); else TUGS.put(p.slot(), p);
            }));
            r.playToServer(Cmd.TYPE, Cmd.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp) || !StationPayloads.ready(StationPayloads.CMD_AT, sp)) return;
                Component why = AsteroidBay.command(sp, p.slot(), p.cmd(), p.x(), p.z());
                if (why != null) sp.displayClientMessage(why, true);
                sendInfo(sp, p.slot());
            }));
        }
    }
}

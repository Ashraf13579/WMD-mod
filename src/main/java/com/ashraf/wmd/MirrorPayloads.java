package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

public final class MirrorPayloads {

    private MirrorPayloads() {}

    public record Info(int slot, boolean owner, int state, int pct, int secs, boolean goal, int gx, int gz, boolean spot, int sx, int sz,
                       int groundX, int groundZ, long energy, boolean sunlit, boolean day, boolean clear) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mirror_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.slot() + 1); b.writeBoolean(p.owner()); b.writeVarInt(p.state() + 2); b.writeVarInt(p.pct()); b.writeVarInt(p.secs());
            b.writeBoolean(p.goal()); b.writeInt(p.gx()); b.writeInt(p.gz()); b.writeBoolean(p.spot()); b.writeInt(p.sx()); b.writeInt(p.sz());
            b.writeInt(p.groundX()); b.writeInt(p.groundZ()); b.writeVarLong(p.energy()); b.writeBoolean(p.sunlit()); b.writeBoolean(p.day()); b.writeBoolean(p.clear());
        }, b -> new Info(b.readVarInt() - 1, b.readBoolean(), b.readVarInt() - 2, b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readInt(), b.readInt(),
                b.readBoolean(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readVarLong(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ask(int slot) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mirror_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((b, p) -> b.writeVarInt(p.slot() + 1), b -> new Ask(b.readVarInt() - 1));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Cmd(int slot, int cmd, int x, int z) implements CustomPacketPayload {
        public static final Type<Cmd> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mirror_cmd"));
        public static final StreamCodec<FriendlyByteBuf, Cmd> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot() + 1); b.writeVarInt(p.cmd()); b.writeInt(p.x()); b.writeInt(p.z()); },
                b -> new Cmd(b.readVarInt() - 1, b.readVarInt(), b.readInt(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Beam(int slot, int state, float x, int y, float z) implements CustomPacketPayload {
        public static final Type<Beam> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mirror_beam"));
        public static final StreamCodec<FriendlyByteBuf, Beam> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot()); b.writeVarInt(p.state()); b.writeFloat(p.x()); b.writeInt(p.y()); b.writeFloat(p.z()); },
                b -> new Beam(b.readVarInt(), b.readVarInt(), b.readFloat(), b.readInt(), b.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.Map<Integer, Object[]> BEAMS = Statics.client(new java.util.concurrent.ConcurrentHashMap<>());

    public static volatile Info latest;
    public static volatile boolean openPending;

    static void sendInfo(ServerPlayer p, int slot) {
        MinecraftServer server = p.server;
        if (slot < 0) slot = Orbit.slotOf(server, p.getUUID());
        if (!StationPayloads.mayKnow(p, slot)) { StationPayloads.noAccess(p, slot); return; }
        if (slot < 0 || MirrorFocus.module(server, slot) == null && !p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) {
            p.displayClientMessage(Component.translatable("message.wmd.mirror.none").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        long now = orbit != null ? orbit.getGameTime() : 0L;
        int[] st = MirrorFocus.status(server, slot, now);
        Orbit.Ground g = Orbit.ground(server, slot);
        boolean owner = p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot));
        PacketDistributor.sendToPlayer(p, new Info(slot, owner, st[0], st[1], st[2], st[3] == 1, st[4], st[5], st[6] == 1, st[7], st[8], g.x(), g.z(),
                Station.Data.get(server).energy.getOrDefault(slot, Balance.STATION_BUFFER / 2), st[9] == 1, st[10] == 1, st[11] == 1));
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Info.TYPE, Info.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { latest = p; openPending = true; }));
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp && StationPayloads.ready(StationPayloads.ASK_AT, sp)) sendInfo(sp, p.slot()); }));
            r.playToClient(Beam.TYPE, Beam.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                Object[] old = BEAMS.get(p.slot());
                double[] from = null;
                if (old != null) { Beam o = (Beam) old[0]; from = new double[] { o.x(), o.y(), o.z() }; }
                BEAMS.put(p.slot(), new Object[] { p, -1L, from });
            }));
            r.playToServer(Cmd.TYPE, Cmd.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp) || !StationPayloads.ready(StationPayloads.CMD_AT, sp)) return;
                Component why = MirrorFocus.command(sp, p.slot(), p.cmd(), p.x(), p.z());
                if (why != null) sp.displayClientMessage(why, true);
                sendInfo(sp, p.slot());
            }));
        }
    }
}

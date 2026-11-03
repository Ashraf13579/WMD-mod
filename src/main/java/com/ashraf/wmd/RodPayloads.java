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

public final class RodPayloads {

    private RodPayloads() {}

    public record Info(int slot, boolean owner, int state, int pct, int secs, int rods, boolean target, int tx, int tz, int gx, int gz, long energy,
                       boolean powered) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rod_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.slot() + 1); b.writeBoolean(p.owner()); b.writeVarInt(p.state() + 2); b.writeVarInt(p.pct()); b.writeVarInt(p.secs() + 1);
            b.writeVarInt(p.rods()); b.writeBoolean(p.target()); b.writeInt(p.tx()); b.writeInt(p.tz()); b.writeInt(p.gx()); b.writeInt(p.gz());
            b.writeVarLong(p.energy()); b.writeBoolean(p.powered());
        }, b -> new Info(b.readVarInt() - 1, b.readBoolean(), b.readVarInt() - 2, b.readVarInt(), b.readVarInt() - 1, b.readVarInt(), b.readBoolean(),
                b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readVarLong(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ask(int slot) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rod_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((b, p) -> b.writeVarInt(p.slot() + 1), b -> new Ask(b.readVarInt() - 1));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Cmd(int slot, int cmd, int x, int z) implements CustomPacketPayload {
        public static final Type<Cmd> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rod_cmd"));
        public static final StreamCodec<FriendlyByteBuf, Cmd> CODEC = StreamCodec.of((b, p) -> { b.writeVarInt(p.slot() + 1); b.writeVarInt(p.cmd()); b.writeInt(p.x()); b.writeInt(p.z()); },
                b -> new Cmd(b.readVarInt() - 1, b.readVarInt(), b.readInt(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static volatile Info latest;
    public static volatile boolean openPending;

    static void sendInfo(ServerPlayer p, int slot) {
        MinecraftServer server = p.server;
        if (slot < 0) slot = Orbit.slotOf(server, p.getUUID());
        if (!StationPayloads.mayKnow(p, slot)) { StationPayloads.noAccess(p, slot); return; }
        if (slot < 0 || RodOfDeath.module(server, slot) == null && !p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) {
            p.displayClientMessage(Component.translatable("message.wmd.rod.none").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        long now = orbit != null ? orbit.getGameTime() : 0L;
        int[] st = RodOfDeath.status(server, slot, now);
        Station.Module m = RodOfDeath.module(server, slot);
        Station.Data d = Station.Data.get(server);
        Orbit.Ground g = Orbit.ground(server, slot);
        boolean owner = p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot));
        PacketDistributor.sendToPlayer(p, new Info(slot, owner, st[0], st[1], st[2], m == null ? 0 : d.rods.getOrDefault(m.id(), 0), st[3] == 1, st[4], st[5],
                g.x(), g.z(), d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2), StationLife.powered(slot)));
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Info.TYPE, Info.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { latest = p; openPending = true; }));
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp && StationPayloads.ready(StationPayloads.ASK_AT, sp)) sendInfo(sp, p.slot()); }));
            r.playToServer(Cmd.TYPE, Cmd.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp) || !StationPayloads.ready(StationPayloads.CMD_AT, sp)) return;
                Component why = RodOfDeath.command(sp, p.slot(), p.cmd(), p.x(), p.z());
                if (why != null) sp.displayClientMessage(why, true);
                sendInfo(sp, p.slot());
            }));
        }
    }
}

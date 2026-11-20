package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class WorldMapPayloads {

    private WorldMapPayloads() {}

    public record Tile(String dim, int lvl, int tx, int tz, int flags, int pct, byte[] ids) implements CustomPacketPayload {

        public static final Type<Tile> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "world_map_tile"));

        public static final StreamCodec<FriendlyByteBuf, Tile> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.dim(), 64); buf.writeByte(p.lvl()); buf.writeInt(p.tx()); buf.writeInt(p.tz()); buf.writeByte(p.flags()); buf.writeByte(p.pct()); buf.writeByteArray(p.ids()); },
                buf -> {
                    String dim = buf.readUtf(64);
                    int lvl = buf.readByte(), tx = buf.readInt(), tz = buf.readInt(), flags = buf.readByte(), pct = buf.readByte();
                    byte[] ids = buf.readByteArray(WorldMap.TILE * WorldMap.TILE);
                    return new Tile(dim, lvl, tx, tz, flags, pct, ids);
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Request(int lvl, int[] tiles) implements CustomPacketPayload {

        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "world_map_request"));

        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeByte(p.lvl()); buf.writeVarIntArray(p.tiles()); },
                buf -> new Request(buf.readByte(), buf.readVarIntArray(16)));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Tile.TYPE, Tile.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> WorldMapView.accept(p)));
            r.playToServer(Request.TYPE, Request.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) WorldMap.request(sp, p.lvl(), p.tiles());
            }));
        }
    }
}

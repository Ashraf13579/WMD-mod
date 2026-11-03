package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ChartTerrainPayload(int x0, int z0, int cell, int w, int h, boolean pending, int[] argb) implements CustomPacketPayload {

    public static final Type<ChartTerrainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chart_terrain"));

    public static final StreamCodec<FriendlyByteBuf, ChartTerrainPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.x0()); buf.writeInt(p.z0()); buf.writeVarInt(p.cell()); buf.writeVarInt(p.w()); buf.writeVarInt(p.h()); buf.writeBoolean(p.pending()); buf.writeVarIntArray(p.argb()); },
            buf -> {
                int x = buf.readInt(), z = buf.readInt(), c = buf.readVarInt(), w = Math.min(TerrainAtlas.MAX_CELLS, buf.readVarInt()), h = Math.min(TerrainAtlas.MAX_CELLS, buf.readVarInt());
                boolean pend = buf.readBoolean();
                int[] a = buf.readVarIntArray(TerrainAtlas.MAX_CELLS * TerrainAtlas.MAX_CELLS);
                return new ChartTerrainPayload(x, z, c, w, h, pend, a.length == w * h ? a : new int[w * h]);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Request(int x0, int z0, int cell, int w, int h) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chart_terrain_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeInt(p.x0()); buf.writeInt(p.z0()); buf.writeVarInt(p.cell()); buf.writeVarInt(p.w()); buf.writeVarInt(p.h()); },
                buf -> new Request(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ChartTerrainView.set(p)));
            r.playToServer(Request.TYPE, Request.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) TerrainAtlas.answer(sp, p.x0(), p.z0(), p.cell(), p.w(), p.h());
            }));
        }
    }
}

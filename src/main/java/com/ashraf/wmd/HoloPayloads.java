package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class HoloPayloads {

    private HoloPayloads() {}

    public record Action(BlockPos pos, int action, int a) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "holo_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeByte(p.action()); buf.writeVarInt(p.a()); },
                buf -> new Action(buf.readBlockPos(), buf.readByte(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TerrainRequest(int lvl, int[] tiles) implements CustomPacketPayload {
        public static final Type<TerrainRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "holo_terrain_request"));
        public static final StreamCodec<FriendlyByteBuf, TerrainRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeByte(p.lvl()); buf.writeVarIntArray(p.tiles()); },
                buf -> new TerrainRequest(buf.readByte(), buf.readVarIntArray(16)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TerrainTile(String dim, int lvl, int tx, int tz, int flags, byte[] ids, byte[] heights) implements CustomPacketPayload {
        public static final Type<TerrainTile> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "holo_terrain_tile"));
        public static final StreamCodec<FriendlyByteBuf, TerrainTile> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.dim(), 64); buf.writeByte(p.lvl()); buf.writeInt(p.tx()); buf.writeInt(p.tz()); buf.writeByte(p.flags());
                    buf.writeByteArray(p.ids()); buf.writeByteArray(p.heights()); },
                buf -> {
                    String dim = buf.readUtf(64);
                    int lvl = buf.readByte(), tx = buf.readInt(), tz = buf.readInt(), flags = buf.readByte();
                    byte[] ids = buf.readByteArray(WorldMap.TILE * WorldMap.TILE), h = buf.readByteArray(WorldMap.TILE * WorldMap.TILE);
                    return new TerrainTile(dim, lvl, tx, tz, flags, ids, h);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Action.TYPE, Action.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) HoloTableBlockEntity.action(sp, p.pos(), p.action(), p.a());
            }));
            r.playToServer(TerrainRequest.TYPE, TerrainRequest.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) WorldMap.requestHeights(sp, p.lvl(), p.tiles());
            }));
            r.playToClient(TerrainTile.TYPE, TerrainTile.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> HoloTerrainView.accept(p)));
        }
    }
}

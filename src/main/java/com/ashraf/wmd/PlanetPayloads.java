package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PlanetPayloads {

    private PlanetPayloads() {}

    public record Info(int key, int gx, int gz, int nearPx, int nearBlocks, int farPx, int farBlocks) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "planet_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> CODEC = StreamCodec.of(
                (b, p) -> { b.writeInt(p.key()); b.writeInt(p.gx()); b.writeInt(p.gz()); b.writeVarInt(p.nearPx()); b.writeVarInt(p.nearBlocks()); b.writeVarInt(p.farPx()); b.writeVarInt(p.farBlocks()); },
                b -> new Info(b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Tile(int key, boolean near, int tx, int ty, byte[] data) implements CustomPacketPayload {
        public static final Type<Tile> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "planet_tile"));
        public static final StreamCodec<FriendlyByteBuf, Tile> CODEC = StreamCodec.of(
                (b, p) -> { b.writeInt(p.key()); b.writeBoolean(p.near()); b.writeVarInt(p.tx()); b.writeVarInt(p.ty()); b.writeByteArray(p.data()); },
                b -> new Tile(b.readInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readByteArray(1 << 17)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Flash(float dx, float dz, int tier) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "planet_flash"));
        public static final StreamCodec<FriendlyByteBuf, Flash> CODEC = StreamCodec.of(
                (b, p) -> { b.writeFloat(p.dx()); b.writeFloat(p.dz()); b.writeVarInt(p.tier()); },
                b -> new Flash(b.readFloat(), b.readFloat(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.concurrent.ConcurrentLinkedQueue<CustomPacketPayload> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Info.TYPE, Info.CODEC, (p, ctx) -> PENDING.add(p));
            r.playToClient(Tile.TYPE, Tile.CODEC, (p, ctx) -> PENDING.add(p));
            r.playToClient(Flash.TYPE, Flash.CODEC, (p, ctx) -> PENDING.add(p));
        }
    }
}

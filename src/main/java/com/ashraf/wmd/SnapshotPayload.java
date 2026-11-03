package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SnapshotPayload(int id, int op, int a, int b, int c, int biome, byte[] data) implements CustomPacketPayload {

    public static final int START = 0, CHUNK = 1, END = 2, FAR = 3;

    public static final Type<SnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, SnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.id()); buf.writeVarInt(p.op()); buf.writeInt(p.a()); buf.writeInt(p.b()); buf.writeInt(p.c()); buf.writeVarInt(p.biome()); buf.writeByteArray(p.data()); },
            buf -> new SnapshotPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readByteArray(1 << 20)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientSnapshot.handle(p)));
        }
    }
}

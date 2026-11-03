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

public final class CargoPayloads {

    private CargoPayloads() {}

    public record Ask(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cargo_release_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((b, p) -> b.writeBlockPos(p.pos()), b -> new Ask(b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Release(BlockPos pos, int port, long start) implements CustomPacketPayload {
        public static final Type<Release> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cargo_release"));
        public static final StreamCodec<FriendlyByteBuf, Release> CODEC = StreamCodec.of((b, p) -> { b.writeBlockPos(p.pos()); b.writeVarInt(p.port()); b.writeLong(p.start()); },
                b -> new Release(b.readBlockPos(), b.readVarInt(), b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.concurrent.ConcurrentLinkedQueue<Release> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) Cargo.release(sp, p.pos()); }));
            r.playToClient(Release.TYPE, Release.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}

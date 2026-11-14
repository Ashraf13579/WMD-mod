package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record PlaceTurnPayload(int turn) implements CustomPacketPayload {

    public static final Type<PlaceTurnPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "place_turn"));

    public static final StreamCodec<FriendlyByteBuf, PlaceTurnPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.turn()),
            buf -> new PlaceTurnPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> PlaceTurn.set(ctx.player(), p.turn())));
        }
    }
}

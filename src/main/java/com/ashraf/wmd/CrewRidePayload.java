package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record CrewRidePayload(long flightId, boolean start) implements CustomPacketPayload {

    public static final Type<CrewRidePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "crew_ride"));

    public static final StreamCodec<FriendlyByteBuf, CrewRidePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.flightId()); buf.writeBoolean(p.start()); },
            buf -> new CrewRidePayload(buf.readLong(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static volatile CrewRidePayload pending;

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> pending = p));
        }
    }
}

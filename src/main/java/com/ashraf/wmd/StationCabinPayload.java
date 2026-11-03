package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record StationCabinPayload(boolean cabin) implements CustomPacketPayload {

    public static final Type<StationCabinPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_cabin"));

    public static final StreamCodec<FriendlyByteBuf, StationCabinPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.cabin()), buf -> new StationCabinPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static volatile boolean cabinNow;

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> cabinNow = p.cabin()));
        }
    }
}

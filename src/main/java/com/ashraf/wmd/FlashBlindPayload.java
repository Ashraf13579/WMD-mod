package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record FlashBlindPayload(float strength, int ticks) implements CustomPacketPayload {

    public static final Type<FlashBlindPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flash_blind"));

    public static final StreamCodec<FriendlyByteBuf, FlashBlindPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeFloat(p.strength()); buf.writeVarInt(p.ticks()); },
            buf -> new FlashBlindPayload(Math.max(0.0F, Math.min(1.0F, buf.readFloat())), Math.max(1, Math.min(2_400, buf.readVarInt()))));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> FlashBlindView.set(p)));
        }
    }
}

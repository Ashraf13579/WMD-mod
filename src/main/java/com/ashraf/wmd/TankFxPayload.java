package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record TankFxPayload(int kind, double x, double y, double z, float r, int colour) implements CustomPacketPayload {

    public static final int FIREBALL = 0, BLEVE = 1, POP = 2, SPLASH = 3, STEAM = 4;

    public static final Type<TankFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tank_fx"));

    public static final StreamCodec<FriendlyByteBuf, TankFxPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.kind()); b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeFloat(p.r()); b.writeInt(p.colour()); },
            b -> new TankFxPayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientTankFx.show(p)));
        }
    }
}

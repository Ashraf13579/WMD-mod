package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record StrikeToastPayload(int kind, int missiles, int rounds, int hits, int intercepted, boolean hasPos, int x, int z, int bda) implements CustomPacketPayload {

    public static final int MISSILES = 0, ARTILLERY = 1, MIXED = 2;

    public static final Type<StrikeToastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "strike_toast"));

    public static final StreamCodec<FriendlyByteBuf, StrikeToastPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind()); buf.writeVarInt(p.missiles()); buf.writeVarInt(p.rounds()); buf.writeVarInt(p.hits()); buf.writeVarInt(p.intercepted());
                buf.writeBoolean(p.hasPos()); buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeVarInt(p.bda() + 1);
            },
            buf -> new StrikeToastPayload(Math.max(0, Math.min(2, buf.readVarInt())), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readVarInt() - 1));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> StrikeToastView.set(p)));
        }
    }
}

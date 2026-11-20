package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record CraftShieldPayload(UUID ship, int kind, float cx, float cy, float cz, float ax, float ay, float az, float ux, float uy, float uz, int pct,
                                 float power) implements CustomPacketPayload {

    public static final int STATE = 0, HIT = 1, SHATTER = 2;

    public static final Type<CraftShieldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "craft_shield"));

    public static final StreamCodec<FriendlyByteBuf, CraftShieldPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUUID(p.ship()); b.writeVarInt(p.kind());
                b.writeFloat(p.cx()); b.writeFloat(p.cy()); b.writeFloat(p.cz());
                b.writeFloat(p.ax()); b.writeFloat(p.ay()); b.writeFloat(p.az());
                b.writeFloat(p.ux()); b.writeFloat(p.uy()); b.writeFloat(p.uz());
                b.writeVarInt(p.pct()); b.writeFloat(p.power());
            },
            b -> new CraftShieldPayload(b.readUUID(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readFloat()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientCraftShield.receive(p)));
        }
    }
}

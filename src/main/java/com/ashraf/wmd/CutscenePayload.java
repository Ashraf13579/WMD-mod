package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record CutscenePayload(int kind, float sx, float sy, float sz, float dx, float dy, float dz, int lead, int total, String caption)
        implements CustomPacketPayload {

    public static final int KIND_RAILGUN = 1, KIND_RAILGUN_REPLAY = 2;

    public static final int KIND_ROD = 3;

    public static final int KIND_CAPTURE = 4;

    public static final int KIND_DROP = 5;

    public static final int KIND_BOARD = 6;

    public static final int KIND_CAM = 7;

    public static final int KIND_TSAR = 8;

    public static final Type<CutscenePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cutscene"));

    public static final StreamCodec<FriendlyByteBuf, CutscenePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.kind()); buf.writeFloat(p.sx()); buf.writeFloat(p.sy()); buf.writeFloat(p.sz()); buf.writeFloat(p.dx()); buf.writeFloat(p.dy());
                buf.writeFloat(p.dz()); buf.writeVarInt(p.lead()); buf.writeVarInt(p.total()); buf.writeUtf(p.caption(), 96); },
            buf -> new CutscenePayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(96)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static volatile CutscenePayload pending;

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> pending = p));
        }
    }
}

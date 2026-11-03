package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AreaViewRequestPayload(int token, int mode, int cx, int cz, int size, boolean markersOnly) implements CustomPacketPayload {

    public static final Type<AreaViewRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "area_view_request"));

    public static final StreamCodec<FriendlyByteBuf, AreaViewRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.token()); buf.writeVarInt(p.mode()); buf.writeInt(p.cx()); buf.writeInt(p.cz()); buf.writeVarInt(p.size()); buf.writeBoolean(p.markersOnly()); },
            buf -> new AreaViewRequestPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AreaViewRequestPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) AreaView.request(sp, p.token(), p.mode(), p.cx(), p.cz(), p.size(), p.markersOnly());
        });
    }
}

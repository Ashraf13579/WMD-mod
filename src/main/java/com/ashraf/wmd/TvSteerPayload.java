package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TvSteerPayload(long id, int x, int z, boolean dive) implements CustomPacketPayload {

    public static final Type<TvSteerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tv_steer"));

    public static final StreamCodec<FriendlyByteBuf, TvSteerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.id()); buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeBoolean(p.dive()); },
            buf -> new TvSteerPayload(buf.readLong(), buf.readInt(), buf.readInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TvSteerPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) TvGuidance.steer(sp, p.id(), p.x(), p.z(), p.dive());
        });
    }
}

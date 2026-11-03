package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TvWindowPayload(long id, int cx, int cz, boolean cruise) implements CustomPacketPayload {

    public static final Type<TvWindowPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tv_window"));

    public static final StreamCodec<FriendlyByteBuf, TvWindowPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.id()); buf.writeInt(p.cx()); buf.writeInt(p.cz()); buf.writeBoolean(p.cruise()); },
            buf -> new TvWindowPayload(buf.readLong(), buf.readInt(), buf.readInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

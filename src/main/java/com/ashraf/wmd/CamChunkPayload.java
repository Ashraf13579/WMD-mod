package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CamChunkPayload(int cx, int cz, short[] h, int[] rgb) implements CustomPacketPayload {

    public static final int N = CamTerrain.CELLS * CamTerrain.CELLS;

    public static final Type<CamChunkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cam_chunk"));

    public static final StreamCodec<FriendlyByteBuf, CamChunkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.cx()); buf.writeInt(p.cz());
                for (int i = 0; i < N; i++) { buf.writeShort(p.h()[i]); buf.writeInt(p.rgb()[i]); }
            },
            buf -> {
                int cx = buf.readInt(), cz = buf.readInt();
                short[] h = new short[N];
                int[] rgb = new int[N];
                for (int i = 0; i < N; i++) { h[i] = buf.readShort(); rgb[i] = buf.readInt(); }
                return new CamChunkPayload(cx, cz, h, rgb);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.ashraf.wmd;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExoRocketsPayload(long[] ids, float[] pos, boolean[] end) implements CustomPacketPayload {

    public static final Type<ExoRocketsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_rockets"));

    static ExoRocketsPayload of(List<float[]> rows) {
        int n = Math.min(64, rows.size());
        long[] ids = new long[n];
        float[] pos = new float[n * 3];
        boolean[] end = new boolean[n];
        for (int i = 0; i < n; i++) {
            float[] r = rows.get(i);
            ids[i] = (long) r[0]; pos[i * 3] = r[1]; pos[i * 3 + 1] = r[2]; pos[i * 3 + 2] = r[3]; end[i] = r[4] > 0.5F;
        }
        return new ExoRocketsPayload(ids, pos, end);
    }

    public static final StreamCodec<FriendlyByteBuf, ExoRocketsPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.ids.length);
        for (int i = 0; i < p.ids.length; i++) {
            buf.writeVarLong(p.ids[i]); buf.writeFloat(p.pos[i * 3]); buf.writeFloat(p.pos[i * 3 + 1]); buf.writeFloat(p.pos[i * 3 + 2]); buf.writeBoolean(p.end[i]);
        }
    }, buf -> {
        int n = Math.min(64, buf.readVarInt());
        long[] ids = new long[n];
        float[] pos = new float[n * 3];
        boolean[] end = new boolean[n];
        for (int i = 0; i < n; i++) { ids[i] = buf.readVarLong(); pos[i * 3] = buf.readFloat(); pos[i * 3 + 1] = buf.readFloat(); pos[i * 3 + 2] = buf.readFloat(); end[i] = buf.readBoolean(); }
        return new ExoRocketsPayload(ids, pos, end);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

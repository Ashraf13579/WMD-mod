package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AreaViewPayload(int token, String refused, int cx, int cz, int n, int step, int baseY, short[] h, int[] rgb) implements CustomPacketPayload {

    public static final Type<AreaViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "area_view"));

    static AreaViewPayload refused(int token, String why) { return new AreaViewPayload(token, why, 0, 0, 0, 1, 0, new short[0], new int[0]); }

    public static final StreamCodec<FriendlyByteBuf, AreaViewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.token()); buf.writeUtf(p.refused()); buf.writeInt(p.cx()); buf.writeInt(p.cz()); buf.writeVarInt(p.n()); buf.writeVarInt(p.step()); buf.writeInt(p.baseY());
                for (int i = 0; i < p.n() * p.n(); i++) { buf.writeShort(p.h()[i]); buf.writeInt(p.rgb()[i]); }
            },
            buf -> {
                int token = buf.readVarInt();
                String why = buf.readUtf();
                int cx = buf.readInt(), cz = buf.readInt(), n = buf.readVarInt(), step = buf.readVarInt(), base = buf.readInt();
                if (n < 0 || n > Balance.AREA_VIEW_SIZE || step < 1 || step > 16) throw new IllegalArgumentException("area view: bad size " + n + " / step " + step);
                short[] h = new short[n * n];
                int[] rgb = new int[n * n];
                for (int i = 0; i < n * n; i++) { h[i] = buf.readShort(); rgb[i] = buf.readInt(); }
                return new AreaViewPayload(token, why, cx, cz, n, step, base, h, rgb);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

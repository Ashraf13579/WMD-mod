package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SeismicResultPayload(List<Hit> hits) implements CustomPacketPayload {

    public record Hit(long pos, int kind) {}

    public static final Type<SeismicResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "seismic_result"));

    public static final StreamCodec<FriendlyByteBuf, SeismicResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(64, p.hits().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) { buf.writeLong(p.hits().get(i).pos()); buf.writeByte(p.hits().get(i).kind()); }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > 64) throw new IllegalArgumentException("seismic: bad hit count " + n);
                List<Hit> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new Hit(buf.readLong(), buf.readByte()));
                return new SeismicResultPayload(List.copyOf(l));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

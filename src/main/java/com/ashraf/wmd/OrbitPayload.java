package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OrbitPayload(List<OrbitMath.Sat> sats, long serverNowMs) implements CustomPacketPayload {

    public static final Type<OrbitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "orbit"));

    public static final StreamCodec<FriendlyByteBuf, OrbitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.serverNowMs());
                buf.writeVarInt(p.sats().size());
                for (OrbitMath.Sat s : p.sats()) s.write(buf);
            },
            buf -> {
                long now = buf.readLong();
                int n = Math.min(buf.readVarInt(), 64);
                List<OrbitMath.Sat> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(OrbitMath.Sat.read(buf));
                return new OrbitPayload(list, now);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FlightsPayload(boolean full, List<Flight> flights, List<Long> removed) implements CustomPacketPayload {

    public static final int MAX_FLIGHTS = 1024, MAX_REMOVED = 4096;

    public static final Type<FlightsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flights"));

    public static final StreamCodec<FriendlyByteBuf, FlightsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.full());
                int n = Math.min(MAX_FLIGHTS, p.flights().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) p.flights().get(i).write(buf);
                int m = Math.min(MAX_REMOVED, p.removed().size());
                buf.writeVarInt(m);
                for (int i = 0; i < m; i++) buf.writeLong(p.removed().get(i));
            },
            buf -> {
                boolean full = buf.readBoolean();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_FLIGHTS) throw new io.netty.handler.codec.DecoderException("flights: bad count " + n);
                List<Flight> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(Flight.read(buf));
                int m = buf.readVarInt();
                if (m < 0 || m > MAX_REMOVED) throw new io.netty.handler.codec.DecoderException("flights: bad removed count " + m);
                List<Long> gone = new ArrayList<>(m);
                for (int i = 0; i < m; i++) gone.add(buf.readLong());
                return new FlightsPayload(full, list, gone);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

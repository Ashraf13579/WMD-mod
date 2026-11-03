package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SmokePayload(List<Entry> clouds) implements CustomPacketPayload {

    public static final int MAX = 32;

    public record Entry(int id, float x, float y, float z, long start) {}

    public static final Type<SmokePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "smoke_clouds"));

    public static final StreamCodec<FriendlyByteBuf, SmokePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.clouds().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Entry e = p.clouds().get(i);
                    buf.writeVarInt(e.id()); buf.writeFloat(e.x()); buf.writeFloat(e.y()); buf.writeFloat(e.z()); buf.writeLong(e.start());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("smoke clouds: bad count " + n);
                List<Entry> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new Entry(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readLong()));
                return new SmokePayload(List.copyOf(l));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static volatile List<Entry> view = List.of();

    public static List<Entry> view() { return view; }

    public static void onReceive(SmokePayload p) { view = p.clouds(); }

    public static void clear() { view = List.of(); }
}

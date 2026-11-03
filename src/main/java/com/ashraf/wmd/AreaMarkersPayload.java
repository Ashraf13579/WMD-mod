package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AreaMarkersPayload(int token, List<Marker> markers) implements CustomPacketPayload {

    public static final int MAX = 256;
    public static final int PLAYER = 0, AIRSHIP = 1, MISSILE = 2, LASER = 3, SMOKE = 4, MOB = 5, CREATURE = 6;

    public record Marker(int kind, float x, float y, float z, float r) {}

    public static final Type<AreaMarkersPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "area_markers"));

    public static final StreamCodec<FriendlyByteBuf, AreaMarkersPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.token());
                int n = Math.min(MAX, p.markers().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Marker m = p.markers().get(i);
                    buf.writeByte(m.kind()); buf.writeFloat(m.x()); buf.writeFloat(m.y()); buf.writeFloat(m.z()); buf.writeFloat(m.r());
                }
            },
            buf -> {
                int token = buf.readVarInt(), n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("area markers: bad count " + n);
                List<Marker> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new Marker(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
                return new AreaMarkersPayload(token, List.copyOf(l));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

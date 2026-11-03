package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SatBeamPayload(List<Beam> beams) implements CustomPacketPayload {

    public static final int MAX = 32;

    public record Beam(float x, float y, float z, float dx, float dy, float dz, float len, int drone) {
        public Beam(float x, float y, float z, float dx, float dy, float dz) { this(x, y, z, dx, dy, dz, 0.0F, 0); }
    }

    public static final Type<SatBeamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sat_beams"));

    public static final StreamCodec<FriendlyByteBuf, SatBeamPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.beams().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Beam b = p.beams().get(i);
                    buf.writeFloat(b.x()); buf.writeFloat(b.y()); buf.writeFloat(b.z()); buf.writeFloat(b.dx()); buf.writeFloat(b.dy()); buf.writeFloat(b.dz());
                    buf.writeFloat(b.len()); buf.writeVarInt(b.drone());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("sat beams: bad count " + n);
                List<Beam> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new Beam(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
                return new SatBeamPayload(List.copyOf(l));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static volatile List<Beam> view = List.of();

    public static List<Beam> view() { return view; }

    public static void onReceive(SatBeamPayload p) { view = p.beams(); }

    public static void clear() { view = List.of(); }
}

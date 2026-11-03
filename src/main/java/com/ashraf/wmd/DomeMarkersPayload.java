package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DomeMarkersPayload(List<Entry> domes) implements CustomPacketPayload {

    public static final int MAX = 8;

    public record Entry(int cx, int cz, int r, boolean active, int okMask) {}

    public static final Type<DomeMarkersPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dome_markers"));

    public static final StreamCodec<FriendlyByteBuf, DomeMarkersPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.domes().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Entry e = p.domes().get(i);
                    buf.writeInt(e.cx()); buf.writeInt(e.cz()); buf.writeVarInt(e.r()); buf.writeBoolean(e.active()); buf.writeByte(e.okMask());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("dome markers: bad count " + n);
                List<Entry> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new Entry(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readByte() & 0xFF));
                return new DomeMarkersPayload(List.copyOf(l));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static volatile List<Entry> view = List.of();

    public static List<Entry> view() { return view; }

    public static void onReceive(DomeMarkersPayload p) { view = p.domes(); }

    public static void clear() { view = List.of(); }
}

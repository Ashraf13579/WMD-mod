package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GpsPayload(boolean on, int x, int y, int z, String dim, String owner, int[] mates, List<String> mateNames, int[] warns, long[] times,
                         boolean hasDes, int dx, int dz) implements CustomPacketPayload {

    public static final int MAX_MATES = 16, MAX_WARNS = 16;

    public static final GpsPayload OFF = new GpsPayload(false, 0, 0, 0, "", "", new int[0], List.of(), new int[0], new long[0], false, 0, 0);

    public static final Type<GpsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_gps"));

    public static final StreamCodec<FriendlyByteBuf, GpsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.on()); buf.writeInt(p.x()); buf.writeInt(p.y()); buf.writeInt(p.z()); buf.writeUtf(p.dim(), 128); buf.writeUtf(p.owner(), 64);
                int nm = Math.min(MAX_MATES, Math.min(p.mates().length / 2, p.mateNames().size()));
                buf.writeVarInt(nm);
                for (int i = 0; i < nm; i++) { buf.writeInt(p.mates()[i * 2]); buf.writeInt(p.mates()[i * 2 + 1]); buf.writeUtf(p.mateNames().get(i), 32); }
                int nw = Math.min(MAX_WARNS, Math.min(p.warns().length / 4, p.times().length / 2));
                buf.writeVarInt(nw);
                for (int i = 0; i < nw; i++) {
                    for (int k = 0; k < 4; k++) buf.writeInt(p.warns()[i * 4 + k]);
                    buf.writeLong(p.times()[i * 2]); buf.writeLong(p.times()[i * 2 + 1]);
                }
                buf.writeBoolean(p.hasDes()); buf.writeInt(p.dx()); buf.writeInt(p.dz());
            },
            buf -> {
                boolean on = buf.readBoolean();
                int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
                String dim = buf.readUtf(128), owner = buf.readUtf(64);
                int nm = buf.readVarInt();
                if (nm < 0 || nm > MAX_MATES) throw new IllegalArgumentException("gps: too many mates " + nm);
                int[] mates = new int[nm * 2];
                List<String> names = new ArrayList<>(nm);
                for (int i = 0; i < nm; i++) { mates[i * 2] = buf.readInt(); mates[i * 2 + 1] = buf.readInt(); names.add(buf.readUtf(32)); }
                int nw = buf.readVarInt();
                if (nw < 0 || nw > MAX_WARNS) throw new IllegalArgumentException("gps: too many warnings " + nw);
                int[] warns = new int[nw * 4];
                long[] times = new long[nw * 2];
                for (int i = 0; i < nw; i++) {
                    for (int k = 0; k < 4; k++) warns[i * 4 + k] = buf.readInt();
                    times[i * 2] = buf.readLong(); times[i * 2 + 1] = buf.readLong();
                }
                boolean hd = buf.readBoolean();
                int dx = buf.readInt(), dz = buf.readInt();
                return new GpsPayload(on, x, y, z, dim, owner, mates, List.copyOf(names), warns, times, hd, dx, dz);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

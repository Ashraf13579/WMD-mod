package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AirRocketPayload(List<Entry> shots) implements CustomPacketPayload {

    public record Entry(long id, double x, double y, double z, float vx, float vy, float vz, boolean burning, boolean lead, boolean fresh) {}

    public static final int MAX = 96;

    public static final Type<AirRocketPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "air_rocket"));

    public static final StreamCodec<FriendlyByteBuf, AirRocketPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.shots().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Entry e = p.shots().get(i);
                    buf.writeLong(e.id());
                    buf.writeDouble(e.x()); buf.writeDouble(e.y()); buf.writeDouble(e.z());
                    buf.writeFloat(e.vx()); buf.writeFloat(e.vy()); buf.writeFloat(e.vz());
                    buf.writeByte((e.burning() ? 1 : 0) | (e.lead() ? 2 : 0) | (e.fresh() ? 4 : 0));
                }
            },
            buf -> {
                int n = Math.min(MAX, buf.readVarInt());
                List<Entry> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    long id = buf.readLong();
                    double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
                    float vx = buf.readFloat(), vy = buf.readFloat(), vz = buf.readFloat();
                    int f = buf.readByte();
                    l.add(new Entry(id, x, y, z, vx, vy, vz, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0));
                }
                return new AirRocketPayload(l);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

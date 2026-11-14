package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AamPayload(List<Entry> shots) implements CustomPacketPayload {

    public record Entry(long id, int kind, double x, double y, double z, float vx, float vy, float vz, boolean burning) {}

    public static final int MAX = 64;

    public static final Type<AamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam"));

    public static final StreamCodec<FriendlyByteBuf, AamPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.shots().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Entry e = p.shots().get(i);
                    buf.writeLong(e.id()); buf.writeByte(e.kind());
                    buf.writeDouble(e.x()); buf.writeDouble(e.y()); buf.writeDouble(e.z());
                    buf.writeFloat(e.vx()); buf.writeFloat(e.vy()); buf.writeFloat(e.vz());
                    buf.writeBoolean(e.burning());
                }
            },
            buf -> {
                int n = Math.min(MAX, buf.readVarInt());
                List<Entry> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    l.add(new Entry(buf.readLong(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
                return new AamPayload(l);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

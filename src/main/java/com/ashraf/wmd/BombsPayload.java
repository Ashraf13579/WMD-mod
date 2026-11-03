package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BombsPayload(List<BombView.Contact> bombs) implements CustomPacketPayload {

    public static final Type<BombsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bombs"));
    static final int MAX = 128;

    public static final StreamCodec<FriendlyByteBuf, BombsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.bombs().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    BombView.Contact c = p.bombs().get(i);
                    buf.writeVarInt(c.id()); buf.writeUUID(c.owner()); buf.writeDouble(c.x()); buf.writeDouble(c.y()); buf.writeDouble(c.z());
                    buf.writeVarInt(c.kind()); buf.writeDouble(c.ix()); buf.writeDouble(c.iz());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("bombs: bad count " + n);
                List<BombView.Contact> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    l.add(new BombView.Contact(buf.readVarInt(), buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readDouble(), buf.readDouble()));
                return new BombsPayload(l);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

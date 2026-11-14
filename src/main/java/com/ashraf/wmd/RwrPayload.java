package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RwrPayload(UUID aircraft, List<Line> lines) implements CustomPacketPayload {

    public static final int MAX = 12;

    public record Line(int kind, float bearing, float dist, float dy, String source, boolean active) {}

    public static final Type<RwrPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rwr"));

    public static final StreamCodec<FriendlyByteBuf, RwrPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.aircraft);
                int n = Math.min(MAX, p.lines.size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Line l = p.lines.get(i);
                    buf.writeByte(l.kind()); buf.writeFloat(l.bearing()); buf.writeFloat(l.dist()); buf.writeFloat(l.dy());
                    buf.writeUtf(l.source(), 16); buf.writeBoolean(l.active());
                }
            },
            buf -> {
                UUID id = buf.readUUID();
                int n = Math.min(MAX, buf.readVarInt());
                List<Line> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new Line(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(16), buf.readBoolean()));
                return new RwrPayload(id, List.copyOf(l));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RwrPayload p, IPayloadContext ctx) { ctx.enqueueWork(() -> ClientRwr.onReceive(p)); }
}

package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CcAlertPayload(int kind, String key, List<String> args) implements CustomPacketPayload {

    public static final int MAX_ARGS = 6;

    public static final Type<CcAlertPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_alert"));

    public static final StreamCodec<FriendlyByteBuf, CcAlertPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind());
                buf.writeUtf(p.key(), 128);
                int n = Math.min(MAX_ARGS, p.args().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeUtf(p.args().get(i), 256);
            },
            buf -> {
                int kind = buf.readVarInt();
                String key = buf.readUtf(128);
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_ARGS) throw new IllegalArgumentException("cc_alert: too many args " + n);
                List<String> a = new ArrayList<>(n);
                for (int i = 0; i < n; i++) a.add(buf.readUtf(256));
                return new CcAlertPayload(kind, key, List.copyOf(a));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

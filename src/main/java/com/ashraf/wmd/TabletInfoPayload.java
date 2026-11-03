package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TabletInfoPayload(boolean mainHand, List<String> lines, boolean ready, String why) implements CustomPacketPayload {

    public static final int MAX_LINES = 8;

    public static final Type<TabletInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tablet_info"));

    public static final StreamCodec<FriendlyByteBuf, TabletInfoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.mainHand());
                int n = Math.min(MAX_LINES, p.lines().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeUtf(p.lines().get(i), 256);
                buf.writeBoolean(p.ready());
                buf.writeUtf(p.why(), 128);
            },
            buf -> {
                boolean main = buf.readBoolean();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_LINES) throw new IllegalArgumentException("tablet_info: too many lines " + n);
                List<String> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(buf.readUtf(256));
                return new TabletInfoPayload(main, List.copyOf(l), buf.readBoolean(), buf.readUtf(128));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

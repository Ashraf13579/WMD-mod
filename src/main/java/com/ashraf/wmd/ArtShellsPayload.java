package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArtShellsPayload(List<ArtShell> shells, boolean replace) implements CustomPacketPayload {

    public static final Type<ArtShellsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "art_shells"));
    static final int MAX = 512;

    public static final StreamCodec<FriendlyByteBuf, ArtShellsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.shells().size());
                buf.writeBoolean(p.replace());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) p.shells().get(i).write(buf);
            },
            buf -> {
                boolean replace = buf.readBoolean();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("art shells: bad count " + n);
                List<ArtShell> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(ArtShell.read(buf));
                return new ArtShellsPayload(l, replace);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

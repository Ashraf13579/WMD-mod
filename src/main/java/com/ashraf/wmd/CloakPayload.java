package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CloakPayload(List<UUID> ships, List<UUID> players) implements CustomPacketPayload {

    public static final Type<CloakPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cloak"));

    public static final StreamCodec<FriendlyByteBuf, CloakPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { write(buf, p.ships()); write(buf, p.players()); },
            buf -> new CloakPayload(read(buf), read(buf)));

    private static void write(FriendlyByteBuf buf, List<UUID> l) {
        int n = Math.min(256, l.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUUID(l.get(i));
    }

    private static List<UUID> read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > 256) throw new IllegalArgumentException("cloak payload: " + n + " entries");
        List<UUID> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(buf.readUUID());
        return l;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

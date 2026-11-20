package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record TeslaStrikePayload(List<Vec3> chain, int water) implements CustomPacketPayload {

    public static final Type<TeslaStrikePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tesla_strike"));

    public static final StreamCodec<FriendlyByteBuf, TeslaStrikePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(8, p.chain().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) { Vec3 v = p.chain().get(i); buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); }
                buf.writeVarInt(p.water());
            },
            buf -> {
                int n = Math.min(8, buf.readVarInt());
                List<Vec3> c = new ArrayList<>(n);
                for (int i = 0; i < n; i++) c.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                return new TeslaStrikePayload(c, buf.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

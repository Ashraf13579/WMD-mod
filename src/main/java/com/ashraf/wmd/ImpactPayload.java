package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImpactPayload(double x, double y, double z, int tier, int warhead, int stage, float air, int flags) implements CustomPacketPayload {

    public static final int WET = 1;

    public static final int SHELL = 2;

    public ImpactPayload(double x, double y, double z, int tier, int warhead, int stage) { this(x, y, z, tier, warhead, stage, 0.0F, 0); }

    public ImpactPayload(double x, double y, double z, int tier, int warhead, int stage, float air) { this(x, y, z, tier, warhead, stage, air, 0); }

    public static final Type<ImpactPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "impact"));

    public static final StreamCodec<FriendlyByteBuf, ImpactPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeVarInt(p.tier()); b.writeVarInt(p.warhead()); b.writeVarInt(p.stage()); b.writeFloat(p.air()); b.writeVarInt(p.flags()); },
            b -> new ImpactPayload(b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

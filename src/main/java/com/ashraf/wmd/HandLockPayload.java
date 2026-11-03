package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HandLockPayload(int kind, int target, int ticks, boolean locked, int dist) implements CustomPacketPayload {

    public static final Type<HandLockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hand_lock"));

    public static final StreamCodec<FriendlyByteBuf, HandLockPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeByte(p.kind()); buf.writeByte(p.target()); buf.writeVarInt(p.ticks()); buf.writeBoolean(p.locked()); buf.writeVarInt(p.dist()); },
            buf -> new HandLockPayload(buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

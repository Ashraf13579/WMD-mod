package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HandToolFxPayload(int player, int tool, int state, float x, float y, float z, int target, float k) implements CustomPacketPayload {

    public static final int TRACTOR = 0, CUTTER = 1;

    public static final Type<HandToolFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hand_tool_fx"));

    public static final StreamCodec<FriendlyByteBuf, HandToolFxPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.player); buf.writeByte(p.tool); buf.writeByte(p.state);
        buf.writeFloat(p.x); buf.writeFloat(p.y); buf.writeFloat(p.z); buf.writeVarInt(p.target + 1); buf.writeFloat(p.k);
    }, buf -> new HandToolFxPayload(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1, buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

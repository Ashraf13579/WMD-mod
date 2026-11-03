package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DroneFlyPayload(int id, boolean active, double x, double y, double z, float yaw, int battery, int alt, int home, int range) implements CustomPacketPayload {

    public static final Type<DroneFlyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_fly"));

    public static final StreamCodec<FriendlyByteBuf, DroneFlyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id()); buf.writeBoolean(p.active());
                buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeFloat(p.yaw());
                buf.writeVarInt(p.battery()); buf.writeVarInt(p.alt() + 1); buf.writeVarInt(p.home()); buf.writeVarInt(p.range());
            },
            buf -> new DroneFlyPayload(buf.readVarInt(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt()));

    static DroneFlyPayload stop(int id) { return new DroneFlyPayload(id, false, 0, 0, 0, 0F, 0, -1, 0, 0); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LaunchSoundPayload(int kind, double x, double y, double z) implements CustomPacketPayload {

    public static final int IGNITE = 0, ASCENT = 10, CRUISE = 20, BOOM = 30, DRONE = 40;

    public static final Type<LaunchSoundPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_sound"));

    public static final StreamCodec<FriendlyByteBuf, LaunchSoundPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeByte(p.kind()); buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); },
            buf -> new LaunchSoundPayload(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    public static int range(int kind) {
        int tier = kind % 10;
        if (kind == DRONE) return 300;
        if (kind >= BOOM) return tier == 4 ? 1000 : 700;
        return kind == CRUISE ? 400 : tier == 1 ? 300 : tier == 2 ? 400 : tier == 3 ? 520 : 900;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

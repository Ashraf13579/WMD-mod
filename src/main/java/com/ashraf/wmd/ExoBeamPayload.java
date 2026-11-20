package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record ExoBeamPayload(long id, int shooter, double x, double y, double z, float dx, float dy, float dz, float k, float dist, boolean end) implements CustomPacketPayload {

    public ExoBeamPayload(long id, int shooter, Vec3 from, Vec3 dir, float k, float dist, boolean end) {
        this(id, shooter, from.x, from.y, from.z, (float) dir.x, (float) dir.y, (float) dir.z, k, dist, end);
    }

    public static final Type<ExoBeamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_beam"));

    public static final StreamCodec<FriendlyByteBuf, ExoBeamPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarLong(p.id); buf.writeVarInt(p.shooter); buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
        buf.writeFloat(p.dx); buf.writeFloat(p.dy); buf.writeFloat(p.dz); buf.writeFloat(p.k); buf.writeFloat(p.dist); buf.writeBoolean(p.end);
    }, buf -> new ExoBeamPayload(buf.readVarLong(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

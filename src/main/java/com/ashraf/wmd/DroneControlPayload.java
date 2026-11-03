package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DroneControlPayload(int id, float forward, float strafe, float up, float yaw, boolean fire, boolean leave, boolean boost) implements CustomPacketPayload {

    public static final Type<DroneControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_control"));

    public static final StreamCodec<FriendlyByteBuf, DroneControlPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id()); buf.writeFloat(p.forward()); buf.writeFloat(p.strafe()); buf.writeFloat(p.up()); buf.writeFloat(p.yaw());
                buf.writeBoolean(p.fire()); buf.writeBoolean(p.leave()); buf.writeBoolean(p.boost());
            },
            buf -> new DroneControlPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DroneControlPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!Float.isFinite(p.forward()) || !Float.isFinite(p.strafe()) || !Float.isFinite(p.up()) || !Float.isFinite(p.yaw())) return;
            StrikeDrones.control(sp, p.id(), p.forward(), p.strafe(), p.up(), p.yaw(), p.fire(), p.leave(), p.boost());
        });
    }
}

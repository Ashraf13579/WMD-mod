package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TripodAimPayload(BlockPos pos, float yaw, float pitch, boolean on) implements CustomPacketPayload {

    public static final Type<TripodAimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tripod_aim"));

    public static final StreamCodec<FriendlyByteBuf, TripodAimPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeFloat(p.yaw()); buf.writeFloat(p.pitch()); buf.writeBoolean(p.on()); },
            buf -> new TripodAimPayload(buf.readBlockPos(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TripodAimPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp) || !sp.level().isLoaded(p.pos())) return;
            if (!Float.isFinite(p.yaw()) || !Float.isFinite(p.pitch())) return;
            if (sp.level().getBlockEntity(p.pos()) instanceof LaserTripodBlockEntity t) t.aim(sp, p.yaw(), p.pitch(), p.on());
        });
    }
}

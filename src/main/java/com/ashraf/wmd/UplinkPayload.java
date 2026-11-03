package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UplinkPayload(BlockPos pos, int action, long flight, int x, int z, String name) implements CustomPacketPayload {

    public static final int SET_LOCK = 0, RETARGET = 1;

    public static final Type<UplinkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "uplink"));

    public static final StreamCodec<FriendlyByteBuf, UplinkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos()); buf.writeVarInt(p.action()); buf.writeLong(p.flight());
                buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeUtf(p.name(), 16);
            },
            buf -> new UplinkPayload(buf.readBlockPos(), buf.readVarInt(), buf.readLong(), buf.readInt(), buf.readInt(), buf.readUtf(16)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UplinkPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("gui.wmd.uplink.use_cc"), true);
        });
    }
}

package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelmInputPayload(BlockPos helm, int throttle, int rudder, int planes, int bow, boolean leave) implements CustomPacketPayload {

    public static final Type<HelmInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "helm_input"));

    public static final StreamCodec<FriendlyByteBuf, HelmInputPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.helm()); buf.writeByte(p.throttle()); buf.writeByte(p.rudder()); buf.writeByte(p.planes()); buf.writeByte(p.bow()); buf.writeBoolean(p.leave()); },
            buf -> new HelmInputPayload(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HelmInputPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.getVehicle() instanceof HelmSeatEntity seat) || !seat.helm().equals(p.helm())) return;
            if (sp.level().getBlockEntity(p.helm()) instanceof HelmStationBlockEntity be)
                be.input(sp, Math.max(-2, Math.min(2, p.throttle())), Integer.signum(p.rudder()), Integer.signum(p.planes()), Integer.signum(p.bow()), p.leave());
        });
    }
}

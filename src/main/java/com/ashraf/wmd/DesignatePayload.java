package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DesignatePayload(boolean set, int satId, int x, int z) implements CustomPacketPayload {

    public static final Type<DesignatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "designate"));

    public static final StreamCodec<FriendlyByteBuf, DesignatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.set()); buf.writeVarInt(p.satId()); buf.writeInt(p.x()); buf.writeInt(p.z()); },
            buf -> new DesignatePayload(buf.readBoolean(), buf.readVarInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DesignatePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (p.set()) SatDesignator.set(sp, p.satId(), p.x(), p.z());
            else SatDesignator.clear(sp.serverLevel(), sp.getUUID());
        });
    }
}

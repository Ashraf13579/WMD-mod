package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CamTerrainRequestPayload(int x, int z, boolean stop) implements CustomPacketPayload {

    public static final Type<CamTerrainRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cam_terrain"));

    public static final StreamCodec<FriendlyByteBuf, CamTerrainRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeBoolean(p.stop()); },
            buf -> new CamTerrainRequestPayload(buf.readInt(), buf.readInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CamTerrainRequestPayload p, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer sp) CamTerrain.request(sp, p.x(), p.z(), p.stop()); });
    }
}

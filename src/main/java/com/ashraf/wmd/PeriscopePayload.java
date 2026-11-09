package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PeriscopePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<PeriscopePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "periscope_view"));

    public static final StreamCodec<FriendlyByteBuf, PeriscopePayload> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, PeriscopePayload::pos, PeriscopePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PeriscopePayload p, IPayloadContext context) {
        context.enqueueWork(() -> { if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) ClientPeriscope.enter(p.pos()); });
    }
}

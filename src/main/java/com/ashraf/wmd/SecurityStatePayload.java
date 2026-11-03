package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SecurityStatePayload(BlockPos pos, boolean isPublic, boolean canEdit, String ownerName, List<String> names) implements CustomPacketPayload {

    public static final int MAX_NAMES = 64;

    public static final Type<SecurityStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "security_state"));

    public static final StreamCodec<FriendlyByteBuf, SecurityStatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SecurityStatePayload::pos,
            ByteBufCodecs.BOOL, SecurityStatePayload::isPublic,
            ByteBufCodecs.BOOL, SecurityStatePayload::canEdit,
            ByteBufCodecs.STRING_UTF8, SecurityStatePayload::ownerName,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_NAMES)), SecurityStatePayload::names,
            SecurityStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

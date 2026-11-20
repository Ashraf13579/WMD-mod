package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TabletInfoPayload(boolean mainHand, CompoundTag data) implements CustomPacketPayload {

    public static final Type<TabletInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tablet_info"));

    public static final StreamCodec<FriendlyByteBuf, TabletInfoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.mainHand()); buf.writeNbt(p.data()); },
            buf -> {
                boolean main = buf.readBoolean();
                CompoundTag t = buf.readNbt();
                return new TabletInfoPayload(main, t == null ? new CompoundTag() : t);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

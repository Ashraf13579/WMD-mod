package com.ashraf.wmd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record DuctTransitPayload(long[] path, int fromSide, int toSide, ItemStack item, int ticks) implements CustomPacketPayload {

    public static final Type<DuctTransitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "duct_transit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DuctTransitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.path().length);
                for (long l : p.path()) buf.writeLong(l);
                buf.writeByte(p.fromSide()); buf.writeByte(p.toSide());
                ItemStack.STREAM_CODEC.encode(buf, p.item());
                buf.writeVarInt(p.ticks());
            },
            buf -> {
                int n = Math.min(buf.readVarInt(), Balance.DUCT_MAX + 2);
                long[] path = new long[n];
                for (int i = 0; i < n; i++) path[i] = buf.readLong();
                int from = buf.readByte(), to = buf.readByte();
                return new DuctTransitPayload(path, from, to, ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

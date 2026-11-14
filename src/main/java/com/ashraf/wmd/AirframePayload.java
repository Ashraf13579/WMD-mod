package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AirframePayload {

    private AirframePayload() {}

    public record Show(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "airframe_show"));
        public static final StreamCodec<FriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeNbt(p.tag()),
                buf -> { CompoundTag t = buf.readNbt(); return new Show(t == null ? new CompoundTag() : t); });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ask(int action, UUID ship, UUID arg, int base) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "airframe_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.action());
                    UUIDUtil.STREAM_CODEC.encode(buf, p.ship());
                    buf.writeBoolean(p.arg() != null);
                    if (p.arg() != null) UUIDUtil.STREAM_CODEC.encode(buf, p.arg());
                    buf.writeVarInt(p.base());
                },
                buf -> {
                    int a = buf.readVarInt();
                    UUID s = UUIDUtil.STREAM_CODEC.decode(buf);
                    UUID g = buf.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buf) : null;
                    return new Ask(a, s, g, buf.readVarInt());
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Ask p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (AirGate.on() && ctx.player() instanceof ServerPlayer sp) AirframeRecords.handle(sp, p.action(), p.ship(), p.arg(), p.base());
            });
        }
    }
}

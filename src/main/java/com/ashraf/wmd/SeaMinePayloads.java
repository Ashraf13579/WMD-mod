package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class SeaMinePayloads {

    private SeaMinePayloads() {}

    public record Open(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sea_mine_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Open::pos, Open::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Act(BlockPos pos, int action, int value) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sea_mine_act"));
        public static final StreamCodec<FriendlyByteBuf, Act> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Act::pos,
                ByteBufCodecs.VAR_INT, Act::action,
                ByteBufCodecs.VAR_INT, Act::value,
                Act::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Act p, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer sp) || !Reach.within(sp, p.pos(), 8.0)
                        || !(sp.level().getBlockEntity(p.pos()) instanceof SeaMineBlockEntity be) || be.isRemoved() || !Access.mayUse(be, sp)) return;
                be.act(sp, p.action(), p.value());
            });
        }
    }
}

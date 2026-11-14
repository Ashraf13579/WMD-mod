package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class LoadoutPayload {

    private LoadoutPayload() {}

    public record Show(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "loadout_show"));
        public static final StreamCodec<FriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeNbt(p.tag()),
                buf -> { CompoundTag t = buf.readNbt(); return new Show(t == null ? new CompoundTag() : t); });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Act(BlockPos depot, CompoundTag args) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "loadout_act"));
        public static final StreamCodec<FriendlyByteBuf, Act> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.depot()); buf.writeNbt(p.args()); },
                buf -> { BlockPos d = buf.readBlockPos(); CompoundTag t = buf.readNbt(); return new Act(d, t == null ? new CompoundTag() : t); });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Act p, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (AirGate.on() && ctx.player() instanceof ServerPlayer sp) Loadouts.act(sp, p.depot(), p.args());
            });
        }
    }
}

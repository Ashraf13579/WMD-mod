package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetTargetPayload(BlockPos pos, int x, int y, int z) implements CustomPacketPayload {

    public static final Type<SetTargetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_target"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SetTargetPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTargetPayload::pos,
            ByteBufCodecs.VAR_INT, SetTargetPayload::x,
            ByteBufCodecs.VAR_INT, SetTargetPayload::y,
            ByteBufCodecs.VAR_INT, SetTargetPayload::z,
            SetTargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetTargetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TerminalMenu menu && menu.getPos().equals(payload.pos())
                    && context.player().level().getBlockEntity(payload.pos()) instanceof LaunchTerminalBlockEntity be) {
                be.setTarget(payload.x(), payload.y(), payload.z());
            }
        });
    }
}

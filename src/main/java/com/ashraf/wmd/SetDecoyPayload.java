package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetDecoyPayload(BlockPos pos, int x, int z) implements CustomPacketPayload {

    public static final Type<SetDecoyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_decoy"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SetDecoyPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetDecoyPayload::pos,
            ByteBufCodecs.VAR_INT, SetDecoyPayload::x,
            ByteBufCodecs.VAR_INT, SetDecoyPayload::z,
            SetDecoyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetDecoyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TerminalMenu menu && menu.getPos().equals(payload.pos())
                    && context.player().level().getBlockEntity(payload.pos()) instanceof LaunchTerminalBlockEntity be && be.hasDivert()) {
                be.setFake(payload.x(), payload.z());
            }
        });
    }
}

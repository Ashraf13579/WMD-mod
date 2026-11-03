package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetResearchPayload(BlockPos pos, String research) implements CustomPacketPayload {

    public static final Type<SetResearchPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_research"));

    public static final StreamCodec<FriendlyByteBuf, SetResearchPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetResearchPayload::pos,
            ByteBufCodecs.stringUtf8(64), SetResearchPayload::research,
            SetResearchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetResearchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.machine() instanceof ResearchStationBlockEntity be
                    && be.getBlockPos().equals(payload.pos())) {
                be.select(payload.research());
            }
        });
    }
}

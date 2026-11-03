package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetLauncherFrequencyPayload(BlockPos pos, int frequency) implements CustomPacketPayload {

    public static final Type<SetLauncherFrequencyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_launcher_frequency"));

    public static final StreamCodec<FriendlyByteBuf, SetLauncherFrequencyPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLauncherFrequencyPayload::pos,
            ByteBufCodecs.VAR_INT, SetLauncherFrequencyPayload::frequency,
            SetLauncherFrequencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetLauncherFrequencyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof LauncherMenu menu && menu.getPos().equals(payload.pos())
                    && context.player().level().getBlockEntity(payload.pos()) instanceof InterceptorSite be
                    && (!(be instanceof Owned ow) || Access.mayUse(ow, context.player()))) {
                be.setFrequency(payload.frequency());
            }
        });
    }
}

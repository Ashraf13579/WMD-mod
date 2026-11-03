package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ArtShellExtendPayload(ArtShell shell) implements CustomPacketPayload {

    public static final Type<ArtShellExtendPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "art_shell_extend"));

    public static final StreamCodec<FriendlyByteBuf, ArtShellExtendPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> p.shell().write(buf),
            buf -> new ArtShellExtendPayload(ArtShell.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ArtilleryView.onExtend(p.shell())));
        }
    }
}

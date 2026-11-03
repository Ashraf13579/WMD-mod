package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public record CameraShellPayload(int x, int z) implements CustomPacketPayload {

    public static final Type<CameraShellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "camera_shell"));

    public static final StreamCodec<FriendlyByteBuf, CameraShellPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.x()); buf.writeInt(p.z()); }, buf -> new CameraShellPayload(buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmdcam").executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) return 0;
            double[] c = SpecialShells.camera(p);
            if (c == null) { p.displayClientMessage(Component.translatable("message.wmd.camera.none").withStyle(ChatFormatting.RED), true); return 0; }
            PacketDistributor.sendToPlayer(p, new CameraShellPayload((int) Math.floor(c[0]), (int) Math.floor(c[1])));
            return 1;
        }));
    }
}

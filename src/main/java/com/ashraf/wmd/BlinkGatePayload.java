package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record BlinkGatePayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final int A_SIZE = 0, A_AUTO = 1, A_BUILD = 2, A_ARM = 3, A_CLOSE = 4, A_UNPAIR = 5;

    public static final Type<BlinkGatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "blink_gate"));

    public static final StreamCodec<FriendlyByteBuf, BlinkGatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlinkGatePayload::pos,
            ByteBufCodecs.VAR_INT, BlinkGatePayload::action,
            ByteBufCodecs.VAR_INT, BlinkGatePayload::value,
            BlinkGatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static void handle(ServerPlayer player, BlinkGatePayload p) {
        if (!(player.containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof BlinkGateCoreBlockEntity c) || !c.getBlockPos().equals(p.pos())) return;
        if (!Access.mayUse(c, player)) return;
        switch (p.action()) {
            case A_SIZE -> c.setMode((c.mode() & ~3) | Math.max(0, Math.min(BlinkGateCoreBlockEntity.SIZES.length - 1, p.value())));
            case A_AUTO -> c.setMode((c.mode() & ~BlinkGateCoreBlockEntity.AUTO_BIT) | (p.value() != 0 ? BlinkGateCoreBlockEntity.AUTO_BIT : 0));
            case A_BUILD -> c.build(player);
            case A_ARM -> { if (!c.arm()) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.wmd.blink.hint." + c.gateStatus()), true); }
            case A_CLOSE -> c.close();
            case A_UNPAIR -> { if (Access.mayConfigure(c, player)) c.unpair(); }
            default -> {}
        }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handle(sp, p); }));
        }
    }
}

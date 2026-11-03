package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShellPressTextPayload(BlockPos pos, String text) implements CustomPacketPayload {

    public static final Type<ShellPressTextPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "shell_press_text"));

    public static final StreamCodec<FriendlyByteBuf, ShellPressTextPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.text(), Balance.LEAFLET_CHARS); },
            buf -> new ShellPressTextPayload(buf.readBlockPos(), buf.readUtf(Balance.LEAFLET_CHARS)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ShellPressTextPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.machine() instanceof ShellPressBlockEntity sp
                    && sp.getBlockPos().equals(p.pos()) && !sp.isRemoved() && Access.mayUse(sp, context.player()))
                sp.setLeafletText(p.text());
        });
    }
}

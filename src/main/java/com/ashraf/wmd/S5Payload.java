package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S5Payload(BlockPos pos, int action, int sat) implements CustomPacketPayload {

    public static final int HACK = 1, ASAT_PICK = 2, FUSE = 3, MIRV = 4;

    public static final Type<S5Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "s5"));

    public static final StreamCodec<FriendlyByteBuf, S5Payload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.action()); buf.writeVarInt(p.sat()); },
            buf -> new S5Payload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(S5Payload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            switch (p.action()) {
                case HACK -> {
                    if (!(sp.containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof GroundStationBlockEntity st) || st.isRemoved()
                            || !st.getBlockPos().equals(p.pos()) || st.getLevel() != sp.level()) return;
                    Component answer = SatHack.start(sp, st, p.sat(), false);
                    if (answer != null) sp.displayClientMessage(answer.copy().withStyle(ChatFormatting.AQUA), false);
                }
                case ASAT_PICK -> {
                    if (!(sp.containerMenu instanceof TerminalMenu menu) || !menu.getPos().equals(p.pos())
                            || !(sp.level().getBlockEntity(p.pos()) instanceof LaunchTerminalBlockEntity t)) return;
                    Component answer = t.pickAsat(sp.getUUID(), p.sat());
                    if (answer != null) sp.displayClientMessage(answer, true);
                }
                case FUSE -> {
                    if (!(sp.containerMenu instanceof TerminalMenu menu) || !menu.getPos().equals(p.pos())
                            || !(sp.level().getBlockEntity(p.pos()) instanceof LaunchTerminalBlockEntity t)) return;
                    sp.displayClientMessage(t.toggleFuse(sp.getUUID()), true);
                }
                case MIRV -> {
                    if (!(sp.containerMenu instanceof TerminalMenu menu) || !menu.getPos().equals(p.pos())
                            || !(sp.level().getBlockEntity(p.pos()) instanceof LaunchTerminalBlockEntity t)) return;
                    sp.displayClientMessage(t.cycleMirv(sp.getUUID()), true);
                }
                default -> {}
            }
        });
    }
}

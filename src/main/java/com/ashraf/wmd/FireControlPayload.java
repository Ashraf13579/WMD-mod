package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FireControlPayload(BlockPos pos, int action, int a, int b) implements CustomPacketPayload {

    public static final Type<FireControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fire_control"));

    public static final StreamCodec<FriendlyByteBuf, FireControlPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FireControlPayload::pos,
            ByteBufCodecs.VAR_INT, FireControlPayload::action,
            ByteBufCodecs.VAR_INT, FireControlPayload::a,
            ByteBufCodecs.VAR_INT, FireControlPayload::b,
            FireControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FireControlPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof FireControlConsoleBlockEntity c)
                    || !c.getBlockPos().equals(p.pos()) || c.isRemoved() || !Access.mayUse(c, context.player())) return;
            c.act(context.player(), p.action(), p.a(), p.b());
        });
    }
}

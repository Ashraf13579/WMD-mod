package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ThrowerPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final Type<ThrowerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "depth_charge_thrower"));

    public static final StreamCodec<FriendlyByteBuf, ThrowerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ThrowerPayload::pos,
            ByteBufCodecs.VAR_INT, ThrowerPayload::action,
            ByteBufCodecs.VAR_INT, ThrowerPayload::value,
            ThrowerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ThrowerPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof DepthChargeThrowerBlockEntity k)
                    || !k.getBlockPos().equals(p.pos()) || k.isRemoved() || !Access.mayUse(k, context.player())) return;
            k.act(context.player(), p.action(), p.value());
        });
    }
}

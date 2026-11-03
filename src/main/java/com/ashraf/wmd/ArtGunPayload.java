package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ArtGunPayload(BlockPos pos, int action, int a, int b) implements CustomPacketPayload {

    public static final Type<ArtGunPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "art_gun"));

    public static final StreamCodec<FriendlyByteBuf, ArtGunPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArtGunPayload::pos,
            ByteBufCodecs.VAR_INT, ArtGunPayload::action,
            ByteBufCodecs.VAR_INT, ArtGunPayload::a,
            ByteBufCodecs.VAR_INT, ArtGunPayload::b,
            ArtGunPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ArtGunPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.machine() instanceof ArtilleryGunBlockEntity g
                    && g.getBlockPos().equals(p.pos()) && !g.isRemoved() && Access.mayUse(g, context.player())
                    && p.action() >= ArtilleryGunBlockEntity.A_TARGET && p.action() <= ArtilleryGunBlockEntity.A_CEASE) {
                g.act(p.action(), p.a(), p.b(), context.player());
            }
        });
    }
}

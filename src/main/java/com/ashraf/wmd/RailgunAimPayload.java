package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record RailgunAimPayload(BlockPos pos, int cmd, int x, int z) implements CustomPacketPayload {

    public static final Type<RailgunAimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "railgun_aim"));

    public static final StreamCodec<FriendlyByteBuf, RailgunAimPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.cmd()); buf.writeInt(p.x()); buf.writeInt(p.z()); },
            buf -> new RailgunAimPayload(buf.readBlockPos(), buf.readVarInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                if (!sp.level().isLoaded(p.pos()) || SableBridge.distSqWithShips(sp.level(), sp.position(), net.minecraft.world.phys.Vec3.atCenterOf(p.pos())) > 24 * 24) return;
                if (sp.level().getBlockEntity(p.pos()) instanceof ScreenCommandTarget t) t.command(sp, p.cmd(), p.x(), p.z());
            }));
        }
    }
}

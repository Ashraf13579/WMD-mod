package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ShipTargetPayload(BlockPos radar, UUID ship, int action) implements CustomPacketPayload {

    public static final int DESIGNATE = 0, CONFIRM = 1, CLEAR = 2;

    public static final Type<ShipTargetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ship_target"));

    public static final StreamCodec<FriendlyByteBuf, ShipTargetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.radar()); buf.writeUUID(p.ship()); buf.writeVarInt(p.action()); },
            buf -> new ShipTargetPayload(buf.readBlockPos(), buf.readUUID(), Math.max(0, Math.min(2, buf.readVarInt()))));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static void handle(ShipTargetPayload p, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer pl) || !(pl.containerMenu instanceof RadarMenu menu) || !menu.getPos().equals(p.radar())) return;
            ServerLevel sl = pl.serverLevel();
            if (!(sl.getBlockEntity(p.radar()) instanceof RadarBlockEntity be) || !Access.mayUse(be, pl)) return;
            UUID owner = be.getOwner();
            if (owner == null) return;
            if (p.action() == CLEAR) {
                ShipTargets.clear(owner);
                pl.displayClientMessage(Component.translatable("message.wmd.ship.cleared"), true);
                return;
            }

            boolean listed = false;
            for (RadarBlockEntity.ShipContact c : be.shipContacts()) if (c.id().equals(p.ship())) { listed = true; break; }
            if (!listed) { pl.displayClientMessage(Component.translatable("message.wmd.ship.gone"), true); return; }
            ShipTargets.Result r = ShipTargets.designate(sl, owner, p.ship(), p.action() == CONFIRM);
            pl.displayClientMessage(r.msg(), true);
        });
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, STREAM_CODEC, ShipTargetPayload::handle);
        }
    }
}

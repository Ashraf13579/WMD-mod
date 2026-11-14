package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
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

public final class AnchorPayloads {

    private AnchorPayloads() {}

    static final int A_DROP = 0, A_WEIGH = 1;

    private static final Map<UUID, int[]> LAST = Statics.server(new HashMap<>());

    private static ResourceLocation id(String n) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, n); }

    public record Press(BlockPos windlass, int action) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(id("anchor_press"));
        public static final StreamCodec<FriendlyByteBuf, Press> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.windlass()); b.writeByte(p.action()); },
                b -> new Press(b.readBlockPos(), b.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Open(BlockPos windlass) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("anchor_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = StreamCodec.of((b, p) -> b.writeBlockPos(p.windlass()), b -> new Open(b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void handlePress(ServerPlayer sp, Press p) {
        if (!AirGate.on() || sp == null || (p.action() != A_DROP && p.action() != A_WEIGH)) return;
        if (AirframeRecords.tooSoon(LAST, sp, 0, Balance.ANCHOR_PRESS_GAP)) return;
        if (!Reach.usable(sp, p.windlass(), Balance.ANCHOR_REACH)) return;
        if (!(sp.level() instanceof ServerLevel sl) || !(sl.getBlockEntity(p.windlass()) instanceof AnchorWindlassBlockEntity be) || be.isRemoved()) return;
        if (be.getOwner() == null || !Access.mayUse(be, sp)) { answer(sp, "denied"); return; }
        if (!craftAllows(sl, be, sp)) { answer(sp, "not_owner"); return; }
        answer(sp, be.command(p.action() == A_DROP, sp));
    }

    static boolean craftAllows(ServerLevel sl, AnchorWindlassBlockEntity be, ServerPlayer sp) {
        UUID ship = SableBridge.shipIdAt(sl, be.getBlockPos());
        if (ship == null) return true;
        UUID owner = Crafts.owner(sl, ship);
        return owner == null || owner.equals(sp.getUUID()) || owner.equals(be.getOwner());
    }

    private static void answer(ServerPlayer sp, String key) {
        sp.displayClientMessage(Component.translatable("message.wmd.anchor." + key), true);
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Press.TYPE, Press.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handlePress(sp, p); }));
            r.playToClient(Open.TYPE, Open.CODEC, (p, ctx) -> ctx.enqueueWork(() -> AnchorClient.open(p.windlass())));
        }
    }
}

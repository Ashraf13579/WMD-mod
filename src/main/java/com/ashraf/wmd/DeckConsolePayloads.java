package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DeckConsolePayloads {

    private DeckConsolePayloads() {}

    private static final Map<UUID, int[]> LAST = Statics.server(new HashMap<>());

    private static ResourceLocation id(String n) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, n); }

    public record Ask(BlockPos console, int action, long target, UUID ship, int arg) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(id("deck_console_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeBlockPos(p.console()); b.writeByte(p.action()); b.writeLong(p.target()); b.writeBoolean(p.ship() != null);
                    if (p.ship() != null) b.writeUUID(p.ship());
                    b.writeByte(p.arg());
                },
                b -> {
                    BlockPos c = b.readBlockPos();
                    int a = b.readByte();
                    long t = b.readLong();
                    UUID s = b.readBoolean() ? b.readUUID() : null;
                    return new Ask(c, a, t, s, b.readByte());
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Wing(BlockPos console, boolean open, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Wing> TYPE = new Type<>(id("deck_console_wing"));
        public static final StreamCodec<FriendlyByteBuf, Wing> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.console()); b.writeBoolean(p.open()); b.writeNbt(p.data()); },
                b -> {
                    BlockPos c = b.readBlockPos();
                    boolean o = b.readBoolean();
                    CompoundTag t = b.readNbt();
                    return new Wing(c, o, t == null ? new CompoundTag() : t);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LightSet(BlockPos console, long[] targets, int mode) implements CustomPacketPayload {
        public static final Type<LightSet> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "deck_console_lights"));
        public static final StreamCodec<FriendlyByteBuf, LightSet> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.console()); b.writeByte(p.mode()); b.writeLongArray(p.targets()); },
                b -> { BlockPos c = b.readBlockPos(); int m = b.readByte(); return new LightSet(c, b.readLongArray(new long[0]), m); });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void handleLightSet(ServerPlayer sp, LightSet p) {
        if (!AirGate.on() || sp == null || p.targets() == null || p.targets().length == 0 || p.targets().length > Balance.FDC_MAX_LIGHTS) return;
        if (AirframeRecords.tooSoon(LAST, sp, 1, Balance.FDC_PRESS_GAP)) return;
        if (!Reach.usable(sp, p.console(), Balance.FDC_REACH)) return;
        if (!(sp.level().getBlockEntity(p.console()) instanceof DeckConsoleBlockEntity be) || be.isRemoved()) return;
        if (!Access.mayUse(be, sp) || be.getOwner() == null) return;
        be.answer(sp, be.lightSet(sp, p.targets(), p.mode()));
    }

    static void handleAsk(ServerPlayer sp, Ask p) {
        if (!AirGate.on() || sp == null || p.action() < 0 || p.action() > DeckConsoleBlockEntity.A_LAST) return;
        boolean refresh = p.action() == DeckConsoleBlockEntity.A_REFRESH;
        if (AirframeRecords.tooSoon(LAST, sp, refresh ? 0 : 1, refresh ? Balance.FDC_SCAN_TICKS / 2 : Balance.FDC_PRESS_GAP)) return;
        if (!Reach.usable(sp, p.console(), Balance.FDC_REACH)) return;
        if (!(sp.level().getBlockEntity(p.console()) instanceof DeckConsoleBlockEntity be) || be.isRemoved()) return;
        if (be.getOwner() == null || !Access.mayUse(be, sp)) return;
        if (refresh) { be.ask(sp, false); return; }
        be.answer(sp, be.command(sp, p.action(), p.target(), p.ship(), p.arg()));
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handleAsk(sp, p); }));
            r.playToClient(Wing.TYPE, Wing.CODEC, (p, ctx) -> ctx.enqueueWork(() -> DeckConsoleClient.onWing(p)));
            r.playToServer(LightSet.TYPE, LightSet.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handleLightSet(sp, p); }));
        }
    }
}

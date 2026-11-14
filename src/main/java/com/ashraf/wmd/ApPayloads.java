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
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ApPayloads {

    private ApPayloads() {}

    public static final int OPEN = 0, SET = 1, ENGAGE = 2, QUICK = 3, OFF = 4, HERE = 5, HOME_MAP = 6, HOME_HERE = 7, HOME_CLEAR = 8;

    private static final Map<UUID, int[]> LAST = Statics.server(new HashMap<>());

    public record Ask(BlockPos cockpit, int op, int arg, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ap_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.cockpit()); buf.writeByte(p.op()); buf.writeByte(p.arg()); buf.writeNbt(p.data() == null ? new CompoundTag() : p.data()); },
                buf -> new Ask(buf.readBlockPos(), buf.readByte(), buf.readByte(), orEmpty(buf.readNbt())));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(BlockPos cockpit, CompoundTag data) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ap_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.cockpit()); buf.writeNbt(p.data()); },
                buf -> new State(buf.readBlockPos(), orEmpty(buf.readNbt())));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static CompoundTag orEmpty(CompoundTag t) { return t == null ? new CompoundTag() : t; }

    public static void handleAsk(Ask p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!AirGate.on() || !(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.getVehicle() instanceof CockpitSeatEntity seat) || !seat.cockpit().equals(p.cockpit())) return;
            if (AirframeRecords.tooSoon(LAST, sp, p.op() == OPEN ? 0 : 1, Balance.AP_ASK_GAP)) return;
            if (!(sp.level().getBlockEntity(p.cockpit()) instanceof JetCockpitBlockEntity be)) return;
            be.apAsk(sp, p.op(), p.arg(), p.data());
        });
    }

    public static void handleState(State p, IPayloadContext context) { context.enqueueWork(() -> ClientAutopilotBridge.onState(p)); }
}

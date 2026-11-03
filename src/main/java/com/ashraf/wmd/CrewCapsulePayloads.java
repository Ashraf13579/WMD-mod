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
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class CrewCapsulePayloads {

    private CrewCapsulePayloads() {}

    public static final int SEAT = 0, RETURN_PAD = 1, RETURN_XZ = 2, RECOVER = 3, ENTER = 4;

    public record Open(BlockPos pos, int seated, boolean landed, int padX, int padZ, boolean docked) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "capsule_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeVarInt(p.seated()); b.writeBoolean(p.landed()); b.writeInt(p.padX()); b.writeInt(p.padZ()); b.writeBoolean(p.docked()); },
                b -> new Open(b.readBlockPos(), b.readVarInt(), b.readBoolean(), b.readInt(), b.readInt(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Action(BlockPos pos, int action, int x, int z) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "capsule_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeVarInt(p.action()); b.writeInt(p.x()); b.writeInt(p.z()); },
                b -> new Action(b.readBlockPos(), b.readVarInt(), b.readInt(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        static void handle(Action a, IPayloadContext ctx) {
            ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) Crew.action(sp, a.pos(), a.action(), a.x(), a.z()); });
        }
    }

    public record Descent(double x, double z, int groundY, long start, int ticks, boolean camera) implements CustomPacketPayload {
        public static final Type<Descent> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "capsule_descent"));
        public static final StreamCodec<FriendlyByteBuf, Descent> CODEC = StreamCodec.of(
                (b, p) -> { b.writeDouble(p.x()); b.writeDouble(p.z()); b.writeInt(p.groundY()); b.writeLong(p.start()); b.writeVarInt(p.ticks()); b.writeBoolean(p.camera()); },
                b -> new Descent(b.readDouble(), b.readDouble(), b.readInt(), b.readLong(), b.readVarInt(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Burn(long start, int ticks, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<Burn> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "capsule_burn"));
        public static final StreamCodec<FriendlyByteBuf, Burn> CODEC = StreamCodec.of(
                (b, p) -> { b.writeLong(p.start()); b.writeVarInt(p.ticks()); b.writeInt(p.x()); b.writeInt(p.y()); b.writeInt(p.z()); },
                b -> new Burn(b.readLong(), b.readVarInt(), b.readInt(), b.readInt(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static volatile Burn pendingBurn;

    public static volatile Open pendingOpen;
    public static final java.util.concurrent.ConcurrentLinkedQueue<Descent> PENDING_DESCENTS = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Open.TYPE, Open.CODEC, (p, ctx) -> ctx.enqueueWork(() -> pendingOpen = p));
            r.playToClient(Descent.TYPE, Descent.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING_DESCENTS.add(p)));
            r.playToClient(Burn.TYPE, Burn.CODEC, (p, ctx) -> ctx.enqueueWork(() -> pendingBurn = p));
            r.playToServer(Action.TYPE, Action.CODEC, Action::handle);
        }
    }
}

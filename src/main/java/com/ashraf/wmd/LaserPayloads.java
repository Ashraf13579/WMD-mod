package com.ashraf.wmd;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LaserPayloads {

    private LaserPayloads() {}

    public record Beam(Vec3 from, Vec3 to, long start, boolean hit) implements CustomPacketPayload {
        public static final Type<Beam> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "laser_beam"));
        public static final StreamCodec<FriendlyByteBuf, Beam> CODEC = StreamCodec.of((b, p) -> {
            b.writeDouble(p.from().x); b.writeDouble(p.from().y); b.writeDouble(p.from().z);
            b.writeDouble(p.to().x); b.writeDouble(p.to().y); b.writeDouble(p.to().z);
            b.writeLong(p.start()); b.writeBoolean(p.hit());
        }, b -> new Beam(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readLong(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ground(double x, double y, double z, long start) implements CustomPacketPayload {
        public static final Type<Ground> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "laser_ground"));
        public static final StreamCodec<FriendlyByteBuf, Ground> CODEC = StreamCodec.of((b, p) -> { b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeLong(p.start()); },
                b -> new Ground(b.readDouble(), b.readDouble(), b.readDouble(), b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final ConcurrentLinkedQueue<CustomPacketPayload> PENDING = Statics.client(new ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToClient(Beam.TYPE, Beam.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
            r.playToClient(Ground.TYPE, Ground.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}

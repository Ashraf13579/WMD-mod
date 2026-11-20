package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record GravityWellPayload(int kind, BlockPos gen, Vec3 centre, int radius, List<Vec3> pts) implements CustomPacketPayload {

    public static final Type<GravityWellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gravity_well"));

    public static final StreamCodec<FriendlyByteBuf, GravityWellPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind());
                buf.writeBlockPos(p.gen());
                vec(buf, p.centre());
                buf.writeVarInt(p.radius());
                int n = Math.min(8, p.pts().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) vec(buf, p.pts().get(i));
            },
            buf -> {
                int k = buf.readVarInt();
                BlockPos g = buf.readBlockPos();
                Vec3 c = vec(buf);
                int r = buf.readVarInt();
                int n = Math.min(8, buf.readVarInt());
                List<Vec3> pts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) pts.add(vec(buf));
                return new GravityWellPayload(k, g, c, r, pts);
            });

    private static void vec(FriendlyByteBuf buf, Vec3 v) { buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); }

    private static Vec3 vec(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientGravityWell.onPayload(p)));
        }
    }
}

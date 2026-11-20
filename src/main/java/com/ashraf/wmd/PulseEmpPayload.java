package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record PulseEmpPayload(Vec3 from, Vec3 axis, List<Vec3> hits) implements CustomPacketPayload {

    public static final Type<PulseEmpPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pulse_emp"));

    public static final StreamCodec<FriendlyByteBuf, PulseEmpPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                vec(buf, p.from()); vec(buf, p.axis());
                buf.writeVarInt(Math.min(24, p.hits().size()));
                for (int i = 0; i < Math.min(24, p.hits().size()); i++) vec(buf, p.hits().get(i));
            },
            buf -> {
                Vec3 f = vec(buf), a = vec(buf);
                int n = Math.min(24, buf.readVarInt());
                List<Vec3> h = new ArrayList<>(n);
                for (int i = 0; i < n; i++) h.add(vec(buf));
                return new PulseEmpPayload(f, a, h);
            });

    private static void vec(FriendlyByteBuf buf, Vec3 v) { buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); }

    private static Vec3 vec(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

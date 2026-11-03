package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record RailgunEventPayload(long gun, int stage, int tx, int ty, int tz, long fireTick, float mx, float my, float mz, String owner, int kind)
        implements CustomPacketPayload {

    public static final int ENDED = 0, CHARGING = 1, FINAL = 2, FIRED = 3, ABORTED = 4;

    public static final int KIND_RAILGUN = 0, KIND_ROD = 1;

    public static final Type<RailgunEventPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "railgun_event"));

    public static final StreamCodec<FriendlyByteBuf, RailgunEventPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.gun()); buf.writeVarInt(p.stage()); buf.writeInt(p.tx()); buf.writeInt(p.ty()); buf.writeInt(p.tz()); buf.writeLong(p.fireTick());
                buf.writeFloat(p.mx()); buf.writeFloat(p.my()); buf.writeFloat(p.mz()); buf.writeUtf(p.owner(), 64); buf.writeVarInt(p.kind()); },
            buf -> new RailgunEventPayload(buf.readLong(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readLong(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(64), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final Map<Long, Object[]> EVENTS = Statics.client(new HashMap<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                if (p.stage() == ENDED) EVENTS.remove(p.gun());
                else EVENTS.put(p.gun(), new Object[] { p, -1L });
            }));
        }
    }
}

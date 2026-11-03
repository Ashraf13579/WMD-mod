package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CcAlarmLogPayload(BlockPos pos, List<CcAlarms.Event> events) implements CustomPacketPayload {

    public static final Type<CcAlarmLogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_alarm_log"));

    public static final StreamCodec<FriendlyByteBuf, CcAlarmLogPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                int n = Math.min(CcAlarms.RING, p.events().size());
                buf.writeVarInt(n);
                for (int i = p.events().size() - n; i < p.events().size(); i++) p.events().get(i).write(buf);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int n = Math.min(CcAlarms.RING, buf.readVarInt());
                List<CcAlarms.Event> l = new ArrayList<>();
                for (int i = 0; i < n; i++) l.add(CcAlarms.Event.read(buf));
                return new CcAlarmLogPayload(pos, l);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CcBattleLogPayload(BlockPos pos, List<CcBattleLog.Event> events) implements CustomPacketPayload {

    public static final Type<CcBattleLogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_battle_log"));

    public static final StreamCodec<FriendlyByteBuf, CcBattleLogPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                int n = Math.min(CcBattleLog.RING, p.events().size());
                buf.writeVarInt(n);
                for (int i = p.events().size() - n; i < p.events().size(); i++) p.events().get(i).write(buf);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int n = buf.readVarInt();
                if (n < 0 || n > CcBattleLog.RING) throw new IllegalArgumentException("battle log: too many lines " + n);
                List<CcBattleLog.Event> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(CcBattleLog.Event.read(buf));
                return new CcBattleLogPayload(pos, List.copyOf(l));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
